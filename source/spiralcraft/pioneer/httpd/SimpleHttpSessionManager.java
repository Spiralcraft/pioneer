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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import spiralcraft.time.Clock;
import spiralcraft.time.Scheduler;

import spiralcraft.pioneer.log.Log;
import spiralcraft.pioneer.log.LogManager;

import spiralcraft.util.IteratorEnumeration;

import java.util.Enumeration;

import spiralcraft.pioneer.telemetry.Meter;
import spiralcraft.pioneer.telemetry.Register;
import spiralcraft.pioneer.telemetry.Meterable;


@SuppressWarnings("deprecation")
public class SimpleHttpSessionManager
  implements HttpSessionManager
            ,HttpSessionContext
            ,Meterable
{

  private int _maxInactiveInterval=600;
  private Log _log=LogManager.getGlobalLog();
  private Meter _meter;
  private Register _activeSessionsRegister;
  private Register _deadSessionsRegister;
  private Register _newSessionsRegister;
	
  public void installMeter(Meter meter)
  {
    _meter=meter;
    _activeSessionsRegister=_meter.createRegister(HttpServer.class,"activeSessions");
    _newSessionsRegister=_meter.createRegister(HttpServer.class,"newSessions");
    _deadSessionsRegister=_meter.createRegister(HttpServer.class,"deadSessions");
    _log=_meter.getEventLog(HttpServer.class);
  }

	/**
	 * Return a specific session or create a new one.
	 */
	public HttpSession getSession(final String id,final boolean create)
  {
    Session session=null;
    synchronized (_sessionLock)
    {
      session=(Session) _sessions.get(id);
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
  public HttpSession getSession(String id)
  { return getSession(id,true);
  }

  public Enumeration getIds()
  { return new IteratorEnumeration(_sessions.keySet().iterator());
  }

	/**
	 * Indicate whether the specified session id is valid.
	 */
	public boolean isSessionIdValid(String id)
  { 
    synchronized (_sessionLock)
    { 
      Session session=(Session) _sessions.get(id);
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


  public void setMaxInactiveInterval(int secs)
  { _maxInactiveInterval=secs;
  }

  public class Session
    implements HttpSession
  {
    public Session()
    {
      if (_newSessionsRegister!=null)
      { _newSessionsRegister.incrementValue();
      }
      if (_activeSessionsRegister!=null)
      { _activeSessionsRegister.incrementValue();
      }

      _id=RandomSessionId.nextId();
      if (_log.isLevel(Log.MESSAGE))
      { 
        _log.log
          (Log.MESSAGE
          ,"HttpSession #"+_id+" started- ttl="+_maxInactiveIntervalMs/1000
          );
      }
      touch();
      _sessions.put(_id,this);
    }

    public void touch()
    { _lastAccess=Clock.instance().approxTimeMillis();
    }
    
    public void markNotNew()
    { 
      touch();
      _new=false;
    }

    public void setMaxInactiveInterval(int seconds)
    { _maxInactiveIntervalMs=seconds*1000;
    }

    public boolean isExpired()
    {
      if (_expired 
          || Clock.instance().approxTimeMillis()-_lastAccess
              >_maxInactiveIntervalMs
          )
      { _expired=true;
      }
      return _expired;
    }

    public boolean isNew()
    { return _new;
    }

    public void removeValue(String name)
    { _values.remove(name);
    }

    public HttpSessionContext getSessionContext()
    { return SimpleHttpSessionManager.this;
    }

    public String[] getValueNames()
    {
      String[] names=new String[_values.size()];
      int i=0;
      Iterator it=_values.keySet().iterator();
      while (it.hasNext())
      { names[i++]=(String) it.next();
      }
      return names;
    }

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

      if (_log.isLevel(Log.MESSAGE))
      {
        _log.log
          (Log.MESSAGE
          ,"HttpSession #"+_id+" expired"
          );
      }
    }

    public void finalize()
      throws Throwable
    { 
      if (_deadSessionsRegister!=null)
      { _deadSessionsRegister.decrementValue();
      }
      super.finalize();
    }

    public String getId()
    { return _id;
    }

    public void putValue(String name, Object value)
    { _values.put(name,value);
    }
    
    public Object getValue(String name)
    { return _values.get(name);
    }
    public int getMaxInactiveInterval()
    { return _maxInactiveIntervalMs/1000;
    }

    public long getLastAccessedTime()
    { return _lastAccess;
    }

    public long getCreationTime()
    { return _creationTime;
    }

    public Object getAttribute(String name)
    { return _attributes.get(name);
    }

    public Enumeration getAttributeNames()
    { return new IteratorEnumeration(_attributes.keySet().iterator());
    }

    public void setAttribute(String name,Object value)
    { _attributes.put(name,value);
    }

    public void removeAttribute(String name)
    { _attributes.remove(name);
    }

    public ServletContext getServletContext()
    {

      // TODO Auto-generated method stub
      _log.log(Log.ERROR, "SimpleHttpSession.getServletContext() not implemented");
      return null;
    }

    private String _id;
    private boolean _new=true;
    private boolean _expired=false;
    private long _lastAccess;
    private long _creationTime=Clock.instance().approxTimeMillis();
    private int _maxInactiveIntervalMs=_maxInactiveInterval*1000;
    private HashMap _values=new HashMap();
    private HashMap _attributes=new HashMap();
    
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
      List expiredList=new ArrayList(50);
      synchronized (_sessionLock)
      {
        Iterator it=_sessions.values().iterator();
        while (it.hasNext())
        {
          Session session=(Session) it.next();
          if (session.isExpired())
          { expiredList.add(session.getId());
          }
        }
      }
      Iterator it=expiredList.iterator();
      while (it.hasNext())
      {
        synchronized (_sessionLock)
        { 
          Session session=(Session) _sessions.get((String) it.next());
          session.invalidate();
        }
      }
    }

    private boolean _finished=false;

  }

  private HashMap _sessions=new HashMap();
  private Object _sessionLock=new Object();
  private ReaperThread _reaper=new ReaperThread();
  private int _reapIntervalSeconds=30;
}
