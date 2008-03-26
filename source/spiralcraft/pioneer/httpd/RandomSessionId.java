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
 * Generates a random session id
 */
package spiralcraft.pioneer.httpd;

import java.util.Random;

public class RandomSessionId
{
  private static char[] chars=
    {'A'
    ,'B'
    ,'C'
    ,'D'
    ,'E'
    ,'F'
    ,'G'
    ,'H'
    ,'I'
    ,'J'
    ,'K'
    ,'L'
    ,'M'
    ,'N'
    ,'O'
    ,'P'
    ,'Q'
    ,'R'
    ,'S'
    ,'T'
    ,'U'
    ,'V'
    ,'W'
    ,'X'
    ,'Y'
    ,'Z'
    ,'0'
    ,'1'
    ,'2'
    ,'3'
    ,'4'
    ,'5'
    ,'6'
    ,'7'
    ,'8'
    ,'9'
    };

  private static final Random RANDOM=new Random();

  public static String nextId()
  {
    StringBuffer out=new StringBuffer(8);
    for (int i=0;i<20;i++)
    { out.append(chars[ Math.abs(RANDOM.nextInt()%36) ]);
    }
    return out.toString();
  }
}
