/**
 * Generic interface for use by Servlets that wish to
 *   authenticate client requests. 
 */
package spiralcraft.pioneer.security.servlet;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.HashMap;

import java.io.IOException;

import spiralcraft.pioneer.security.SecurityException;

public interface ServletAuthenticator
{
  /**
   * Ensures that the client submitting the request has been
   *   authenticated by this ServletAuthenticator. Normally,
   *   the implementing class will maintain state in the HttpSession.
   *
   *@return true, if the client has been authenticated. If true,
   *        the response should proceed normally. If false,
   *        the implementation has committed an appropriate response
   *        which is implementation specific. The caller should
   *        return from the service() method without sending further
   *        output.
   */
  public boolean authenticate
    (HttpServletRequest request
    ,HttpServletResponse response
    ,HashMap<String,String> formData
    )
    throws SecurityException,IOException;

  /**
   * Indicate whether or not the client is authenticated, without trying to authenticate.
   */
  public boolean isAuthenticated(HttpServletRequest request);

  /**
   * Return the Principle for the authenticated user issuing the request.
   * If noone is authenticated, return null. The Principle generally contains the userId.
   */  
  public Principal getAuthenticatedPrincipal(HttpServletRequest request);

  /**
   * De-authenticates client. Client must go through an explicit login process to
   *   gain access.
   */
  public void logout(HttpServletRequest request,HttpServletResponse response);

  /**
   * Indicate whether login data appears in the dictionary of
   *   form data.
   */
  public boolean attemptedLogin(HashMap<String,String> formData);
}
