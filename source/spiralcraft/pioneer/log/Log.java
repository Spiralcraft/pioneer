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
package spiralcraft.pioneer.log;

import java.util.Map;

/**
 * A place for a component to record events.
 */
public interface Log
{
  public static final int DEBUG=4;
  public static final int MESSAGE=3;
  public static final int WARNING=2;
  public static final int ERROR=1;
  public static final int OFF=0;

  /**
   * Write a message of the given severity level
   *   to the log.
   */
  public void log(int level,String message);

  /**
   * Write an Event to the log.
   */
  public void logEvent(Event evt);

  /**
   * Obtain the level threshold beyond which 
   *   messages will be ignored. Performance conscious
   *   Clients should query this level to avoid composing
   *   unneeded messages.
   */
  public int getLevel();

	/**
	 * Specify the message output threshold for this log.
	 */
	public void setLevel(int level);
	
  /**
   * Determine if the given level is past the threshhold.
   */ 
  public boolean isLevel(int level);

  /**
   * Indicate whether debugging is turned on for the specified group name
   */
  public boolean isDebugEnabled(String debugGroupName);

  /**
   * Specify a Map which maps debugging group names to
   *   a non null value (as yet undefined) to indicate whether
   *   the group is turned on.
   */
  public void setDebugProfile(Map map);

  /**
   * Return the debug profile map
   */
  public Map getDebugProfile();
}
