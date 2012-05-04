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
 * Provides an interface to a session manager.
 */
package spiralcraft.pioneer.httpd;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

public interface HttpSessionManager
{
	/**
	 * Return a specific session or create a new one.
	 */
	public HttpSession getSession(String id,boolean create);

	/**
	 * Indicate whether the specified session id is valid.
	 */
	public boolean isSessionIdValid(String id);

	/**
	 * Specify the ServletContext that will be returned by 
	 *   HttpSession.getServletContext()
	 * @param context
	 */
	public void setServletContext(ServletContext context);

}
