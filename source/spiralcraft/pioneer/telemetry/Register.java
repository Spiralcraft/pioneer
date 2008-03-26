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
 * An interface that an application component uses to publish a single
 *   measurement point.
 */
package spiralcraft.pioneer.telemetry;

public interface Register
{

  public void setValue(long value);
  
  public void incrementValue();

  public void decrementValue();

  public void adjustValue(long increment);

  /**
   * Read the value and reset the changed flag 
   */
  public long readValue();

  /**
   * Return the value
   */
  public long getValue();

  /**
   * Indicate whether the value has changed since the last read
   */
  public boolean hasChanged();


  /**
   * Return the instance path of the register (does not include the
   *   attribute name)
   */
  public String getInstancePath();
  
  /**
   * Return the name of the metered class to which this register
   *   belongs
   */
  public String getMeteredClassName();

  /**
   * Return the attribute name of the register
   */
  public String getAttributeName();
}
