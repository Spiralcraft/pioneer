/**
 * Manages a pool of resources using a check-in
 *  check-out system.
 */
package spiralcraft.pioneer.pool;

import java.util.Stack;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.List;
import java.util.Collection;

import spiralcraft.pioneer.util.ThrowableUtil;

import spiralcraft.time.Clock;
import spiralcraft.time.Scheduler;

import spiralcraft.log.Level;
import spiralcraft.log.ClassLog;

import spiralcraft.meter.MeterContext;
import spiralcraft.meter.Meter;
import spiralcraft.meter.Register;

public class Pool
{
  private static final ClassLog _log=ClassLog.getInstance(Pool.class);

  private int _overdueSeconds=600;
  private ResourceFactory _factory;
  private int _idleSeconds=3600;
  private int _maxSize=1;
  private int _initialSize=1;
  private int _minAvailable=0;
  private long _lastUse=0;
  private long _maintenanceInterval=500;
  private Keeper _keeper=new Keeper();
  private Stack<Reference> _available=new Stack<Reference>();
  private HashMap<Object,Reference> _out=new HashMap<Object,Reference>();
  private Object _monitor=new Object();
  private boolean _started=false;
//  private Object _startLock=new Object();
  private boolean _conserve=false;

  private Meter _meter;
  private Register _availableRegister;
  private Register _checkedOutRegister;
  private Register _checkInsRegister;
  private Register _checkOutsRegister;
  private Register _clientDiscardsRegister;
  private Register _waitsRegister;
  private Register _waitingRegister;
  private Register _overdueDiscardsRegister;
  private Register _addsRegister;
  private Register _removesRegister;
  
  private boolean debug;



  public void installMeter(MeterContext meterContext)
  { 
    _meter=meterContext.meter("connectionPool");
    _availableRegister
      =_meter.register("available");
    _checkedOutRegister
      =_meter.register("checkedOut");
    _checkInsRegister
      =_meter.register("checkIns");
    _checkOutsRegister
      =_meter.register("checkOuts");
    _clientDiscardsRegister
      =_meter.register("clientDiscards");
    _waitsRegister
      =_meter.register("waits");
    _waitingRegister
      =_meter.register("waiting");
    _overdueDiscardsRegister
      =_meter.register("overdueDiscards");
    _addsRegister
      =_meter.register("adds");
    _removesRegister
      =_meter.register("removes");
  }


  public void setDebug(boolean debug)
  { this.debug=debug;
  }
  
  /**
   * Conserve resources by not discarding them when demand drops,
   *   in order to promote maximum reuse.
   */
  public void setConserve(boolean val)
  { _conserve=val;
  }

  /**
   * Specify the initial number of objects that
   *   will be created.
   */
  public void setInitialSize(int size)
  { 
    if (size<=0)
    { throw new IllegalArgumentException("Initial size of pool must be at least 1");
    }
    _initialSize=size;
  }

  /**
   * Specify the minimum number of available
   *   objects. When the number of available
   *   objects crosses this threshold, new 
   *   objects will be created until either
   *   this threshold is reached or the maxSize
   *   is reached.
   */
  public void setMinAvailable(int size)
  { 
    if (size<=0)
    { throw new IllegalArgumentException("Minimum available objects in pool must be at least 1");
    }
    _minAvailable=size;
  }

  /**
   * Specify the maximum total of checked
   *   out and available items.
   */
  public void setMaxSize(int size)
  { _maxSize=size;
  }

  /**
   * Specify the time of no activity after which the pool
   *   will to the initial size.
   */
  public void setIdleTimeSeconds(int seconds)
  { _idleSeconds=seconds;
  }

  /**
   * Specify the component that creates and discards 
   *   pooled objects.
   */
  public void setResourceFactory(ResourceFactory factory)
  { _factory=factory;
  }
  
  /**
   * Specify the maximum check-out time, after which
   *   an item will be discarded.
   */
  public void setOverdueTimeSeconds(int seconds)
  { _overdueSeconds=seconds;
  }

  /**
   * Start the pool by filling it up to the minumum size and
   *   starting the Keeper.
   */
  public void init()
  {
    synchronized (_monitor)
    {
      restoreInitial();
      _started=true;
      _keeper.start();
      _monitor.notifyAll();
      
    }
    
  }

  /**
   * Stop the pool and discard all resources
   */
  public void stop()
  {
    synchronized (_monitor)
    {
      _started=false;
      _keeper.stop();
      while (!_available.isEmpty())
      { 
        if (_meter!=null)
        { _availableRegister.decrementValue();
        }
        _factory.discardResource(_available.pop().resource );
      }      
    }
  }

  public int getTotalSize()
  { return _available.size()+_out.size();
  }

  public int getNumAvailable()
  { return _available.size();
  }

  /**
   * Checkout an object from the pool of
   *   available object.
   */
  public Object checkout()
  {
    _lastUse=Clock.instance().approxTimeMillis();
    synchronized (_monitor)
    {
      if (!_started)
      { 
        _log.log(Level.INFO,"Waiting for pool to start");
        try
        { 
          _monitor.wait();
          _log.log(Level.INFO,"Notified that pool started");
        }
        catch (InterruptedException x)
        {  
          _log.log(Level.WARNING,"Checkout on startup interrupted");
          return null;
        }
      }

      while (_available.isEmpty())
      { 
        _keeper.wake();
        if (_available.isEmpty())
        {
          try
          { 
            if (_meter!=null)
            { 
              _waitsRegister.incrementValue();
              _waitingRegister.incrementValue();
            }
            
            long time=0;
            if (debug)
            { 
              _log.log(Level.INFO,"Waiting for pool");
              time=System.currentTimeMillis();
            }
            _monitor.wait();
            if (_meter!=null)
            { _waitingRegister.decrementValue();
            }
            if (debug)
            { 
              _log.log
                (Level.INFO,"Waited "
                 +(System.currentTimeMillis()-time)+" for pool"
                );
            }
          }
          catch (InterruptedException x)
          { 
            if (_meter!=null)
            { _waitingRegister.decrementValue();
            }
            return null; 
          }
        }
      }
      Reference ref= _available.pop();
      ref.checkOutTime=Clock.instance().approxTimeMillis();
      _out.put(ref.resource,ref);
      if (_meter!=null)
      { 
        _checkedOutRegister.incrementValue();
        _checkOutsRegister.incrementValue();
        _availableRegister.decrementValue();
      }
      return ref.resource;

    }
  }

  /**
   * Return a checked out object to the pool
   *   of available objects.
   */
  public void checkin(Object resource)
  {
    _lastUse=Clock.instance().approxTimeMillis();
    synchronized (_monitor)
    {
      Reference ref= _out.remove(resource);
      if (_meter!=null)
      { 
        _checkInsRegister.incrementValue();
        _checkedOutRegister.decrementValue();
      }
      if (ref!=null)
      {
        if (_started)
        { 
          _available.push(ref);
          if (_meter!=null)
          { _availableRegister.incrementValue();
          }
        }
        else
        { _factory.discardResource(resource);
        }
        _monitor.notify();
      }
      else
      { _log.log(Level.WARNING,"Unbalanced checkin: "+resource.toString()); 
      }
    }

  }

  /**
   * Discard a checked out object. A new object will be created to
   *   fill the void if required. Used when it is known that an
   *   object is corrupt or has expired for some reason.
   */
  public void discard(Object resource)
  {
    _lastUse=Clock.instance().approxTimeMillis();
    synchronized (_monitor)
    { 
      _out.remove(resource);
    }
    if (_meter!=null)
    { _clientDiscardsRegister.incrementValue();
    }
    _factory.discardResource(resource);
  }

  //////////////////////////////////////////////////////////////////
  //
  // Private Members
  //
  //////////////////////////////////////////////////////////////////

  class Keeper
    implements Runnable
  {
    private boolean _done=false;
    private boolean _running=false;
//    private int _numTimes=0;
    private Object _keeperMonitor=new Object();
    private int _scheduledCount=0;

    public void stop()
    { _done=true;
    }

    @Override
    public void run()
    {

      if (_done)
      { return;
      }

      synchronized (_keeperMonitor)
      { 
        _scheduledCount--;
        if (_running)
        { return;
        }
        _running=true;
      }

//      _numTimes++;
      try
      {
        if (_overdueSeconds>0)
        { discardOverdue();
        }

        if ( getTotalSize()>_initialSize
            && System.currentTimeMillis()-_lastUse>(_idleSeconds*1000)
            )
        { restoreInitial();
        }

        grow();
      }
      catch (Exception x)
      { _log.log(Level.SEVERE,"Exception while keeping pool. "+x.toString());
      }
      
      synchronized (_keeperMonitor)
      {
        _running=false;
        if (_scheduledCount==0)
        {
          _scheduledCount++;
          Scheduler.instance().scheduleIn(this,_maintenanceInterval);
        }
      }
    }

    public void start()
    { 
      synchronized (_keeperMonitor)
      {
        _scheduledCount++;
        Scheduler.instance().scheduleNow(this);
      }
    }
    
    public void wake()
    { 
      synchronized (_keeperMonitor)
      {
        if (!_running && _scheduledCount<2)
        { 
          _scheduledCount++;
          Scheduler.instance().scheduleNow(this);
        }
      }
    }
  }

  class Reference
  {
    public Object resource;
    public long checkOutTime;
  }


  private void restoreInitial()
  {
    while (getTotalSize()<_initialSize)
    { add();
    }
    if (!_conserve)
    {
      while (getTotalSize()>_initialSize && getNumAvailable()>0)
      { remove();
      }
    }
  }

  private void grow()
  {
    while (getNumAvailable()<_minAvailable && getTotalSize()<_maxSize && _started==true)
    { add();
    }
  }

  private void discardOverdue()
  {

    
    Reference[] snapshot;
    synchronized (_monitor)
    { 
      Collection<Reference> collection=_out.values();
      snapshot=new Reference[collection.size()];
      collection.toArray(snapshot);
    }

    List<Object> discardList=null;
    long time=Clock.instance().approxTimeMillis();

    for (int i=0;i<snapshot.length;i++)
    {
      if (snapshot[i].checkOutTime-time>_overdueSeconds*1000)
      {
        Reference ref=null;
        synchronized (_monitor)
        { ref=_out.remove(snapshot[i].resource);
        }
        if (ref!=null)
        { 
          if (discardList==null)
          { discardList=new LinkedList<Object>();
          }
          discardList.add(ref.resource);
        }
      }
    }

    if (discardList!=null)
    {
      Iterator<Object> it=discardList.iterator();
      while (it.hasNext())
      { 
        if (_meter!=null)
        { _overdueDiscardsRegister.incrementValue();
        }
        _factory.discardResource(it.next()); 
      }
    }
    
  }

  private void add()
  {

    Reference ref=new Reference();
    try
    { ref.resource=_factory.createResource();
    }
    catch (Exception x)
    { _log.log(Level.SEVERE,"Exception creating pooled resource. "+ThrowableUtil.getStackTrace(x));
    }

    if (ref.resource!=null)
    {
      synchronized (_monitor)
      {
        _available.push(ref);
        _monitor.notify();
      }
      if (_meter!=null)
      { 
        _addsRegister.incrementValue();
        _availableRegister.incrementValue();
      }
    }
  }

  private void remove()
  {
    Object resource=null;

    synchronized (_monitor)
    {
      if (!_available.isEmpty())
      { resource=_available.pop().resource;
      }
    }
    
    if (resource!=null)
    {
      try
      { _factory.discardResource(resource);
      }
      catch (Exception x)
      { 
        _log.log
          (Level.SEVERE,"Exception discarding pooled resource. "
          +ThrowableUtil.getStackTrace(x)
          );
      }
      if (_meter!=null)
      { 
        _availableRegister.decrementValue();
        _removesRegister.incrementValue();
      }

    }
  }
}


