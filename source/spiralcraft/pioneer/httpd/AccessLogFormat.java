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
 * A formatter for access log entries.
 *
 * Assumes that access logs will be stored in a format
 *   with one line for a header and one line for each request.
 *
 * The implementation must provide appropriate synchronization if
 *   required.
 */
package spiralcraft.pioneer.httpd;

public interface AccessLogFormat
{
  public static final String TIME="time";
  public static final String CLIENT_ADDRESS="clientAddress";
  public static final String SERVER_NAME="serverName";
  public static final String REQUEST="request";
  public static final String REFERER="referer";
  public static final String USER_AGENT="userAgent";
  public static final String DURATION="duration";
  public static final String BYTE_COUNT="byteCount";
  public static final String SESSION_ID="sessionId";
  public static final String USER="userId";
  public static final String HTTP_AUTH_ID="httpAuthId";
  public static final String RESPONSE_CODE="responseCode";
  
  /**
   * Return the String that should appear at the beginning
   *   of a log file, or null if nothing should appear.
   */
  public String header();
  
  /**
   * Format a log entry from the completed request/response.
   */
  public String format(HttpServerRequest request,HttpServerResponse response);

  
}

