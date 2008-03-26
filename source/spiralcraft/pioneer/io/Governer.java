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
 * Provides pacing services for streaming data.
 */
package spiralcraft.pioneer.io;

import java.io.IOException;

public interface Governer
{

  /**
   * Block until the next packet may be sent.
   *@return The size of the packet that should be sent,
   *          which will be less than or equal to the
   *          desired packet size.
   */
  public int nextPacket(int desiredPacketSize)
    throws IOException;

}


