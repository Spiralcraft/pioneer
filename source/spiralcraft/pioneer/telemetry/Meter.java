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
/**
 * Used by application components to publish events and
 *   counter values.
 */
package spiralcraft.pioneer.telemetry;

import spiralcraft.pioneer.log.Log;

public interface Meter
{

  /**
   * Return the child Meter bound to the specified name 
   */
  public Meter getChildMeter(String name);

  /**
   * Create a Meter for use by a 
   *   child object.
   */
  public Meter createChildMeter(String name)
    throws NameAlreadyBoundException;

  /**
   * Create a Meter for use by a 
   *   child object, retaining a reference to the target
   *   for use by admin interfaces.
   */
  public Meter createChildMeter(String name,Object target)
    throws NameAlreadyBoundException;

  /**
   * Return the metered object
   */
  public Object getTarget();

  /**
   * Create a Register to publish a single counter
   */
  public Register createRegister(Class<?> meteredClass,String registerName)
    throws NameAlreadyBoundException;
 
  /**
   * Add a FrameListener to be notified when the Meter is about
   *   to be 'read'. 
   */
  public void setFrameListener(FrameListener listener);
  
  /**
   * Create a named log for a specific type of event
   */
  public Log createLog(Class<?> meteredClass,String logName)
    throws NameAlreadyBoundException;

  public Meter[] getChildren();

  public Register[] getRegisters();

  /**
   * Return the standard event log
   */
  public Log getEventLog(Class<?> meteredClass);
  
}
