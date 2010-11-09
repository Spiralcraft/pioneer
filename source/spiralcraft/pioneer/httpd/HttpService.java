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


import spiralcraft.pioneer.net.QueueConnectionHandler;
import spiralcraft.pioneer.net.Listener;


import spiralcraft.service.Service;
import spiralcraft.common.LifecycleException;


/**
 * Run the HttpServer as a Daemon
 * 
 */
public class HttpService
  extends HttpServer
  implements Service
{
  
  private Listener[] listeners=new Listener[0];
  
  private QueueConnectionHandler handlerQueue=new QueueConnectionHandler();
  
  public void setListeners(Listener[] listeners)
  { this.listeners=listeners;
  }
  
  public QueueConnectionHandler getHandlerQueue()
  { return handlerQueue;
  }
  
  @Override
  public void start()
    throws LifecycleException
  {
    if (handlerQueue==null)
    { handlerQueue=new QueueConnectionHandler();
    }
    
    handlerQueue.setConnectionHandlerFactory(this);
    
    for (Listener listener: listeners)
    { listener.setConnectionHandler(handlerQueue);
    }
    
    super.start();
    handlerQueue.init();
    for (Listener listener: listeners)
    { listener.start();
    }
    
   
  }

  @Override
  public void stop()
    throws LifecycleException
  {
    for (Listener listener: listeners)
    { listener.stop();
    }
    handlerQueue.stop();
    super.stop();
    
  }


}
