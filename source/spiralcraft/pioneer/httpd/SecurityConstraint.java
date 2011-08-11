//
// Copyright (c) 2011 Michael Toth
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

/**
 * A declared Security Constraint for a ServletContext
 * 
 * @author mike
 *
 */
public class SecurityConstraint
{
  
  private WebResourceCollection resourceCollection;
  private UserDataConstraint userDataConstraint;
  
  public void setResourceCollection(WebResourceCollection resourceCollection)
  { this.resourceCollection=resourceCollection;
  }
  
  public void setUserDataConstraint(UserDataConstraint userDataConstraint)
  { this.userDataConstraint=userDataConstraint;
  }
  
  public boolean matches(String httpMethod,String path)
  { return resourceCollection.matches(httpMethod,path);
  }
  
  public void setRequireSecureChannel(boolean requiresSecureChannel)
  { 
    if (requiresSecureChannel)
    { 
      userDataConstraint=new UserDataConstraint();
      userDataConstraint.setTransportGuarantee
        (UserDataConstraint.TransportGuarantee.CONFIDENTIAL);
    }
    else
    { 
      if (getRequireSecureChannel())
      { userDataConstraint=null;
      }
    }
  }
  
  public boolean getRequireSecureChannel()
  { 
    if (userDataConstraint!=null)
    {
      if (userDataConstraint.getTransportGuarantee()!=null)
      { 
        return UserDataConstraint.TransportGuarantee.CONFIDENTIAL
          .equals(userDataConstraint.getTransportGuarantee());
      }
    }
    return false;
  }
}
