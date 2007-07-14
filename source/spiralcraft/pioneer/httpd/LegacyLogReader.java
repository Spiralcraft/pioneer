//
// Copyright (c) 1998,2005 Michael Toth
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

import spiralcraft.exec.Executable;
import spiralcraft.exec.ExecutionContext;

import spiralcraft.util.Arguments;

import spiralcraft.vfs.StreamUtil;
import spiralcraft.vfs.Resource;
import spiralcraft.vfs.Resolver;

import com.spiralcraft.httpd.AccessLogReader;
import com.spiralcraft.httpd.AccessLogFormat;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Reads legacy log formats 
 */
@SuppressWarnings("unchecked") // Legacy code will never use generics
public class LegacyLogReader
  implements Executable
{
  private String hostName; 
  private String readerClassName;
  private List<String> resourceNames=new ArrayList<String>();
  private ExecutionContext context;
  
  @SuppressWarnings("unused") // XXX Use this
  private String dateFormatString;
  
  private DateFormat dateFormat=new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.S");
  private String headerResourceName;
  private byte[] header;
  private HashMap<String,String> ipLookup=new HashMap<String,String>();

  public void execute(ExecutionContext context,String[] args)
  {
    this.context=context;
    new Arguments()
    {
      protected boolean processOption(String option)
      {
        if (option=="reader")
        { readerClassName=nextArgument();
        }
        else if (option=="hostName")
        { hostName=nextArgument();
        }
        else if (option=="dateFormat")
        { dateFormatString=nextArgument();
        }
        else if (option=="header")
        { headerResourceName=nextArgument();
        }
        else
        { return false;
        }
        return true;
      }
      
      protected boolean processArgument(String argument)
      {
        resourceNames.add(argument);
        return true;
      }
      
    }.process(args,'-');
    
    try
    { processAll();
    }
    catch (Exception x)
    { x.printStackTrace();
    }
  }
  
  private void processAll()
    throws IOException,ClassNotFoundException,InstantiationException,IllegalAccessException
  { 

    Iterator<String> it=resourceNames.iterator();
    while (it.hasNext())
    {
      String resourceName=it.next();
      this.context.out().println(resourceName);
      process(resourceName);
    }
    
    

  }
  
  private void process(String resourceName)
    throws IOException,ClassNotFoundException,InstantiationException,IllegalAccessException
  {    
    URI inputUri=this.context.canonicalize(URI.create(resourceName));
    Resource inputResource=Resolver.getInstance().resolve(inputUri);
    
    URI outputUri=this.context.canonicalize(URI.create(resourceName+".csv"));
    Resource outputResource=Resolver.getInstance().resolve(outputUri);
    
    if (this.headerResourceName!=null)
    {
      URI headerUri=this.context.canonicalize(URI.create(this.headerResourceName));
      Resource headerResource=Resolver.getInstance().resolve(headerUri);
      InputStream headerIn=headerResource.getInputStream();
      this.header=StreamUtil.readBytes(headerIn);
      headerIn.close();
    }
    
    if (inputResource!=null)
    {
      AccessLogReader logReader
        =(AccessLogReader) Class.forName(readerClassName).newInstance();
      InputStream in=inputResource.getInputStream();
      
      OutputStream out=outputResource.getOutputStream();
      if (this.header!=null)
      { 
        out.write(header);
        out.flush();
      }
        
      PrintWriter outWriter
        =new PrintWriter(new OutputStreamWriter(out),false);
      
           
      BufferedReader reader=new BufferedReader(new InputStreamReader(in));
      int record=0;
      for (String line=null;(line=reader.readLine())!=null;)
      { 
        Map map=logReader.readData(line);
        StringBuffer csvLine=new StringBuffer();
        
        csvLine.append("\"")
          .append(resourceName)
          .append("\"");
          
        csvLine.append(",");
        csvLine.append(record);

        csvLine.append(",");
        csvLine
          .append("\"")
          .append(this.hostName)
          .append("\"");
          
        csvLine.append(",");
        
        csvLine.append("\"")
          .append(map.get(AccessLogFormat.CLIENT_ADDRESS))
          .append("\"");
        
        csvLine.append(",");
        
        csvLine.append("\"")
          .append(lookupAddress((String) map.get(AccessLogFormat.CLIENT_ADDRESS)))
          .append("\"");
        
        
        csvLine.append(",");

        if (map.get(AccessLogFormat.HTTP_AUTH_ID)!=null)
        {
          csvLine.append("\"")
            .append(map.get(AccessLogFormat.HTTP_AUTH_ID))
            .append("\"");
        }
        csvLine.append(",");
        
        csvLine.append("\"")
          .append(dateFormat.format(map.get(AccessLogFormat.TIME)))
          .append("\",\"")
          .append(map.get(AccessLogFormat.REQUEST))
          .append("\",\"")
          .append(map.get(AccessLogFormat.REFERER))
          .append("\",\"")
          .append(map.get(AccessLogFormat.USER_AGENT))
          .append("\",")
          .append(map.get(AccessLogFormat.RESPONSE_CODE))
          .append(",")
          .append(map.get(AccessLogFormat.BYTE_COUNT))
          .append(",")
          .append(map.get(AccessLogFormat.DURATION))
          .append(",");
        
        if (map.get(AccessLogFormat.SESSION_ID)!=null)
        {
          csvLine.append("\"")
            .append(map.get(AccessLogFormat.SESSION_ID))
            .append("\"")
            ;
        }
        csvLine.append(",");
        String request=(String) map.get(AccessLogFormat.REQUEST);
        if (request!=null)
        {
          int pos1=request.indexOf(' ');
          int pos2=request.lastIndexOf(' ');
          String path=request.substring(pos1+1,pos2);
          csvLine.append("\"")
            .append(path)
            .append("\"")
            ;
        }
        
        outWriter.println(csvLine.toString());
        record++;
        if (record%10==0)
        { context.out().println(record);
        }
      }
      outWriter.flush();
      outWriter.close();
    }
  }
  
  private String lookupAddress(String ip)
    throws UnknownHostException
  {
    String address=(String) ipLookup.get(ip);
    if (address==null)
    {
      context.out().print("Looking up "+ip+"...");
      address=InetAddress.getByName(ip).getHostName();
      ipLookup.put(ip,address);
      context.out().println(address);
    }
    return address;
  }  
}
