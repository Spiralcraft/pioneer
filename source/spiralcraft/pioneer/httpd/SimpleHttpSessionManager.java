//
// Copyright (c) 1998,2008 Michael Toth
// Spiralcraft Inc., All Rights Reserved
//
// This package is part of the Spiralcraft project and is licensed under
// a multiple-license framework.
//
// You may not use this file except in compliance with the terms found in the
// SPIRALCRAFT-LICENSE.txt file at the top of this distribution, or available
// at http://www.spiralcraft.org/licensing/SPIRALCRAFT-LICENSE.txt.
//
// Unless otherwise agreed to in writing, this software is distributed on an
// "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
//

package spiralcraft.pioneer.httpd;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

import javax.servlet.http.HttpSessionContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import spiralcraft.time.Clock;
import spiralcraft.time.Scheduler;

import spiralcraft.log.Level;
import spiralcraft.log.ClassLog;

import spiralcraft.util.IteratorEnumeration;

import java.util.Enumeration;

import spiralcraft.meter.Meter;
import spiralcraft.meter.Register;
import spiralcraft.meter.MeterContext;


@SuppressWarnings("deprecation")
/**
 * Manages sessions
 */
public class SimpleHttpSessionManager
  implements HttpSessionManager
            ,HttpSessionContext
{

  private static final ClassLog log
    =ClassLog.getInstance(SimpleHttpSessionManager.class);
  
  private int _maxInactiveInterval=600;
  private int _maxSecondsToJoin=60;
  private Register _activeSessionsRegister;
  private Register _deadSessionsRegister;
  private Register _newSessionsRegister;
  private boolean _logSessionEvents=true;
  private ServletContext _servletContext;
	
  private HashMap<String,Session> _sessions
    =new HashMap<String,Session>();
  private Object _sessionLock=new Object();
  private ReaperThread _reaper=new ReaperThread();
  private int _reapIntervalSeconds=30;
  
  public void installMeter(MeterContext meterContext)
  {
    Meter meter=meterContext.meter("SessionManager");
    _activeSessionsRegister=meter.register("activeSessions");
    _newSessionsRegister=meter.register("newSessions");
    _deadSessionsRegister=meter.register("deadSessions");
  }

	/**
	 * Return a specific session or create a new one.
	 */
	@Override
  public HttpSession getSession(final String id,final boolean create)
  {
    Session session=null;
    synchronized (_sessionLock)
    {
      session=_sessions.get(id);
      if (session==null)
      {
        if (create)
        { session=new Session();
        }
      }
      else if (session.isExpired())
      {
        session=null;
        if (create)
        { session=new Session();
        }
      }
      else 
      { session.markNotNew();
      }
    }
    return session;
  }

  /**
   * Return a session, create a new one if not found
   */
  @Override
  public HttpSession getSession(String id)
  { return getSession(id,true);
  }

  @Override
  public Enumeration<?> getIds()
  { return new IteratorEnumeration<String>(_sessions.keySet().iterator());
  }

	/**
	 * Indicate whether the specified session id is valid.
	 */
	@Override
  public boolean isSessionIdValid(String id)
  { 
    synchronized (_sessionLock)
    { 
      Session session=_sessions.get(id);
      return session!=null && !session.isExpired();
    }
  }

  
  /**
   * Start the session manager thread
   */
  public void init()
  { 
    _reaper.start();
  }

  public void stop()
  {
    _reaper.finished();
    _sessions.clear();
  }

  public void setMaxSecondsToJoin(int secs)
  { _maxSecondsToJoin=secs;
  }

  public void setMaxInactiveInterval(int secs)
  { _maxInactiveInterval=secs;
  }

  @Override
  public void setServletContext(ServletContext context)
  { this._servletContext=context;
  }
  
  public class Session
    implements HttpSession
  {
    private String _id;
    private volatile boolean _new=true;
    private volatile boolean _expired=false;
    private long _lastAccess;
    private long _creationTime=Clock.instance().approxTimeMillis();
    private int _maxInactiveIntervalMs=_maxInactiveInterval*1000;
    private int _maxMsToJoin=_maxSecondsToJoin*1000;
    private Map<String,Object> _values
      =Collections.synchronizedMap(new HashMap<String,Object>());
    private Map<String,Object> _attributes
      =Collections.synchronizedMap(new HashMap<String,Object>());
    
    public Session()
    {
      if (_newSessionsRegister!=null)
      { _newSessionsRegister.incrementValue();
      }
      if (_activeSessionsRegister!=null)
      { _activeSessionsRegister.incrementValue();
      }

      _id=RandomSessionId.nextId();
      if (_logSessionEvents && log.canLog(Level.INFO))
      { 
        log.log
          (Level.INFO
          ,"HttpSession #"+_id+" started- ttl="+_maxInactiveIntervalMs/1000
          );
      }
      touch();
      _sessions.put(_id,this);
    }

    private void touch()
    { _lastAccess=Clock.instance().approxTimeMillis();
    }
    
    private synchronized void markNotNew()
    { 
      touch();
      _new=false;
    }

    @Override
    public void setMaxInactiveInterval(int seconds)
    { _maxInactiveIntervalMs=seconds*1000;
    }

    public synchronized boolean isExpired()
    {
      final long now=Clock.instance().approxTimeMillis();
      if (_expired 
          || now-_lastAccess
              >_maxInactiveIntervalMs
          || (_new 
              && now-_creationTime
                >_maxMsToJoin
             )
          )
      { _expired=true;
      }
      return _expired;
    }

    @Override
    public synchronized boolean isNew()
    { return _new;
    }

    @Override
    public void removeValue(String name)
    { _values.remove(name);
    }

    @Override
    @Deprecated
    public HttpSessionContext getSessionContext()
    { return SimpleHttpSessionManager.this;
    }

    @Override
    public String[] getValueNames()
    {
      String[] names=new String[_values.size()];
      int i=0;
      Iterator<String> it=_values.keySet().iterator();
      while (it.hasNext())
      { names[i++]=it.next();
      }
      return names;
    }

    @Override
    public void invalidate()
    {
      if (_activeSessionsRegister!=null)
      { _activeSessionsRegister.decrementValue();
      }
      if (_deadSessionsRegister!=null)
      { _deadSessionsRegister.incrementValue();
      }
      _expired=true;
      synchronized (_sessionLock)
      { _sessions.remove(_id);
      }
      _values.clear();
      _attributes.clear();

      if (log.canLog(Level.INFO))
      {
        log.log
          (Level.INFO
          ,_new
            ?"HttpSession #"+_id+" expired while new"
            :"HttpSession #"+_id+" expired"
          );
      }
    }

    @Override
    public void finalize()
      throws Throwable
    { 
      if (_deadSessionsRegister!=null)
      { _deadSessionsRegister.decrementValue();
      }
      super.finalize();
    }

    @Override
    public String getId()
    { return _id;
    }

    @Override
    public void putValue(String name, Object value)
    { _values.put(name,value);
    }
    
    @Override
    public Object getValue(String name)
    { return _values.get(name);
    }
    @Override
    public int getMaxInactiveInterval()
    { return _maxInactiveIntervalMs/1000;
    }

    @Override
    public long getLastAccessedTime()
    { return _lastAccess;
    }

    @Override
    public long getCreationTime()
    { return _creationTime;
    }

    @Override
    public Object getAttribute(String name)
    { return _attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames()
    { return new IteratorEnumeration<String>(_attributes.keySet().iterator());
    }

    @Override
    public void setAttribute(String name,Object value)
    { _attributes.put(name,value);
    }

    @Override
    public void removeAttribute(String name)
    { _attributes.remove(name);
    }

    @Override
    public ServletContext getServletContext()
    { return _servletContext;
    }

  }

  class ReaperThread
    implements Runnable
  {

    public void start()
    { Scheduler.instance().scheduleIn(this,_reapIntervalSeconds*1000);
    }

    public void finished()
    { _finished=true;
    }

    @Override
    public void run()
    {
      try
      { reap();
      }
      finally
      {
        if (!_finished)
        { Scheduler.instance().scheduleIn(this,_reapIntervalSeconds*1000);
        }
        else
        { 
          synchronized(_sessionLock)
          { 
            Session[] sessions=new Session[_sessions.size()];
            _sessions.values().toArray(sessions);
            for (int i=0;i<sessions.length;i++)
            { sessions[i].invalidate();
            }
          }
        }
      }
    }

    private void reap()
    {
      List<String> expiredList=new ArrayList<String>(50);
      synchronized (_sessionLock)
      {
        Iterator<Session> it=_sessions.values().iterator();
        while (it.hasNext())
        {
          Session session=it.next();
          if (session.isExpired())
          { expiredList.add(session.getId());
          }
        }
      }
      Iterator<String> it=expiredList.iterator();
      while (it.hasNext())
      {
        synchronized (_sessionLock)
        { 
          Session session= _sessions.get(it.next());
          session.invalidate();
        }
      }
    }

    private boolean _finished=false;

  }

}
