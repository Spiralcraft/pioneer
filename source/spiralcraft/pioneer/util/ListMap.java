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
package spiralcraft.pioneer.util;

import java.util.Map;

/**
 * Extends the map interface to facilitate the efficient
 *   mapping of multiple values with the same key by mapping
 *   each key to a List of values.
 */
@SuppressWarnings("unchecked")
public interface ListMap
  extends Map
{

  /**
   * Return the first value that matches the key.
   */
  public Object getFirst(Object key);

  /**
   * When true, the map will ensure that
   *   there is only one value in the
   *   list 
   */
  public void setUnique(boolean unique);
}
