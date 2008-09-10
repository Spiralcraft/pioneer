package spiralcraft.pioneer.httpd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.FileNotFoundException;

import spiralcraft.pioneer.log.Log;
import spiralcraft.pioneer.log.LogManager;

import spiralcraft.util.StringUtil;
import spiralcraft.vfs.Resolver;
import spiralcraft.vfs.StreamUtil;
import spiralcraft.vfs.Resource;

import spiralcraft.pioneer.io.Filename;

import java.text.SimpleDateFormat;

import java.util.ArrayList;

import java.io.File;
import java.io.FilenameFilter;

import java.util.Enumeration;
import java.util.Date;

import java.io.OutputStream;
import java.io.InputStream;


public class FileServlet
  extends HttpServlet
{

  private static final long serialVersionUID = 1L;


//  private FileCache _fileCache=new FileCache();
//  { _fileCache.setMaxSize(1024*1024); 
//  }
  
  private Log _log=LogManager.getGlobalLog();
  private int _bufferSize=8192;
  private ServletConfig _servletConfig;
  private boolean _permitDirListing=true;
  private String[] _defaultFiles={"index.html","index.htm","default.htm"};
  private SimpleDateFormat _fileDateFormat=new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");
  
  public FileServlet()
  {
  }

  @Override
  public void init(ServletConfig servletConfig)
    throws ServletException
  { 
    _log.log(Log.DEBUG
            ,"FileServlet.init() "
            );
    
    if (servletConfig.getServletContext()==null)
    { throw new ServletException("FileServlet.init(): No servlet context");
    }

    ArrayList<String> defaultFiles=new ArrayList<String>();
    _servletConfig=servletConfig;
    Enumeration<?> e=_servletConfig.getInitParameterNames();
    while (e.hasMoreElements())
    {
      String key=(String) e.nextElement();

      if (_log.isLevel(Log.DEBUG))
      { 
        _log.log(Log.DEBUG
                ,key+"="+_servletConfig.getInitParameter(key)
                );
      }

      if (key.startsWith("default."))
      {
        defaultFiles.add
          (_servletConfig.getInitParameter(key)
          );
      }
    }
    if (defaultFiles.size()>0)
    {
      _defaultFiles=new String[defaultFiles.size()];
      defaultFiles.toArray(_defaultFiles);
    }
      
  }

  @Override
  public void service(HttpServletRequest request,HttpServletResponse response)
    throws IOException,ServletException
  {
    if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
    { _log.log(Log.DEBUG,"Servicing request for "+request.getRequestURI());
    }
    
    String path
      =_servletConfig.getServletContext()
        .getRealPath(request.getServletPath());
    
    if (path==null)
    { 
      // Send an error, because the URI could not be translated into
      //   a real path (malformed URI or illegal path)
      response.sendError
        (400
        ,"<H1>Bad Request</H1>"
        +"Your browser sent a request that this server could not understand.<P>"
        +"Invalid URI in request "+request.getRequestURI()
        );

      return;
      
    }

    File file=new File(path);
    if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
    { _log.log(Log.DEBUG,"File Servlet serving "+path);
    }
    
    if (request.getRequestURI().endsWith("/"))
    {
      if (!isDirectory(file))
      {
        response.sendError
          (500
          ,"<H1>Forbidden</H1>"
          +"You don't have permission to access "
          +request.getRequestURI()+" on this server.<P>"
          );
      }
      else
      {
        path=findDefaultFile(file);
        if (path!=null)
        { 
          if (request.getMethod().equals("GET"))
          { sendFile(request,response,path);
          }
          else if (request.getMethod().equals("HEAD"))
          { sendHeaders(request,response,path);
          }
          else if (request.getMethod().equals("PUT"))
          { putFile(request,response,path);
          }
          else
          { response.sendError(405);
          }
        }
        else
        { 
          if (_permitDirListing)
          { sendDirectory(request,response,file);
          }
          else
          {
            // Send an error, because we couldn't find the servlet,
            //   and defaulting to sending the file would be bad.
            response.sendError
              (500
              ,"<H1>Forbidden</H1>"
              +"You don't have permission to access "
              +request.getRequestURI()+" on this server.<P>"
              );
          }
          return;
        }
      }
    }
    else
    { 
      if (request.getMethod().equals("GET"))
      { sendFile(request,response,path);
      }
      else if (request.getMethod().equals("HEAD"))
      { sendHeaders(request,response,path);
      }
      else if (request.getMethod().equals("PUT"))
      { putFile(request,response,path);
      }
      else
      { response.sendError(405);
      }
    }
  }



  //////////////////////////////////////////////////////////////////
  //
  // Private Methods
  //
  //////////////////////////////////////////////////////////////////




  /**
   * Returns the default file for a directory, or
   *   null if none of the default files can be found
   */
  private String findDefaultFile(File dir)
  {
    for (int i=0;i<_defaultFiles.length;i++)
    { 
      if (exists(dir,_defaultFiles[i]))
      { return new File(dir,_defaultFiles[i]).getPath();
      }
    }
    return null;
  }

  private void putFile
    (HttpServletRequest request
    ,HttpServletResponse response
    ,String path
    )
    throws IOException
  {
    try
    {
      String contentLengthString=request.getHeader("Content-Length");
      if (contentLengthString==null)
      {
        response.sendError(411);
        return;
      }
      int contentLength=Integer.parseInt(contentLengthString);

      Resource resource=Resolver.getInstance().resolve(new File(path).toURI());
      OutputStream out=resource.getOutputStream();
      InputStream in=request.getInputStream();
      StreamUtil.copyRaw(in,out,16384,contentLength);
      out.flush();
      out.close();
      response.setStatus(201);
    }
    catch (IOException x)
    { 
      _log.log(Log.ERROR,"Error writing "+request.getRequestURI()+":"+x.toString());
      x.printStackTrace();
      response.sendError(500,"Error transferring file");
    }
  }

  private void setHeaders
    (HttpServletRequest request
    ,HttpServletResponse response
    ,Resource resource
    )
    throws IOException
  {
    String contentType
      =_servletConfig.getServletContext().getMimeType
        (resource.getLocalName());
    if (contentType!=null)
    { response.setContentType(contentType);
    }
    long size=resource.getSize();
    if (size>0 && size<Integer.MAX_VALUE)
    { response.setContentLength((int) size);
    }
    response.setDateHeader
      (HttpServerResponse.HDR_LAST_MODIFIED
      ,floorToSecond(resource.getLastModified())
      );
    response.setDateHeader
      (HttpServerResponse.HDR_EXPIRES
      ,floorToSecond(resource.getLastModified())
      );
    response.setHeader(HttpServerResponse.HDR_CACHE_CONTROL,"max-age=0");
    
  }
  
  /**
   * Send the headers for the specified file to the client
   */
  private void sendHeaders
    (HttpServletRequest request
    ,HttpServletResponse response
    ,String path
    )
    throws IOException
  {
    try
    {
      Resource resource=Resolver.getInstance().resolve(new File(path).toURI());
      long lastModified=floorToSecond(resource.getLastModified());      
      try
      { 
        long ifModifiedSince=request.getDateHeader(HttpServerResponse.HDR_IF_MODIFIED_SINCE);
        if (ifModifiedSince>0 && lastModified<=ifModifiedSince)
        {
          // Send unchanged status because resource not modified.
          response.setStatus(304);
          response.getOutputStream().flush();
          return;
        }
        else if (ifModifiedSince>0 && _log.isDebugEnabled(HttpServer.DEBUG_PROTOCOL))
        { _log.log(Log.DEBUG,"If-Modified-Since: "+ifModifiedSince+", lastModified="+lastModified);
        }
      }
      catch (IllegalArgumentException x)
      {
        _log.log
          (Log.WARNING
          ,"Unrecognized date format in header- If-Modified-Since: "
          +request.getHeader(HttpServerResponse.HDR_IF_MODIFIED_SINCE)
          );
      }      

      setHeaders(request,response,resource);
      
      response.getOutputStream().flush();
    }
    catch (FileNotFoundException x)
    {
      response.sendError
        (404
        ,"<H2>404 - Not Found</H2>The specified URL, <STRONG>"
        +request.getRequestURI()
        +"</STRONG> could not be found on this server."
        );
    }
    catch (IOException x)
    { 
      if (!x.getMessage().equals("Broken pipe")
          && !x.getMessage().equals("Connection reset by peer")
          )
      {
        _log.log
          (Log.WARNING
          ,"IOException retrieving "+path+": "+x.toString()
          );
      }

    }
  }
  
  private long floorToSecond(long timeInMs)
  { return (long) Math.floor((double) timeInMs/(double) 1000)*1000;
  }
  
  /**
   * Send the specified file to the client
   */
  private void sendFile
    (HttpServletRequest request
    ,HttpServletResponse response
    ,String path
    )
    throws IOException
  {
    // Standard file service
    // Simply send the disk file
    
    InputStream resourceInputStream=null;
    
    try
    {
      Resource resource=Resolver.getInstance().resolve(new File(path).toURI());
      long lastModified=floorToSecond(resource.getLastModified());
      try
      { 
        long ifModifiedSince=request.getDateHeader(HttpServerResponse.HDR_IF_MODIFIED_SINCE);
        if (ifModifiedSince>0 && lastModified<=ifModifiedSince)
        {
          // Send unchanged status because resource not modified.
          response.setStatus(304);
          response.getOutputStream().flush();
          return;
        }
        else if (ifModifiedSince>0 && _log.isDebugEnabled(HttpServer.DEBUG_PROTOCOL))
        {
          _log.log(Log.DEBUG,"If-Modified-Since: "
                    +ifModifiedSince+", lastModified="+lastModified);
        }
      }
      catch (IllegalArgumentException x)
      {
        _log.log
          (Log.WARNING
          ,"Unrecognized date format in header- If-Modified-Since: "
          +request.getHeader(HttpServerResponse.HDR_IF_MODIFIED_SINCE)
          );
      }      

      setHeaders(request,response,resource);

      resourceInputStream
        =resource.getInputStream();

      /**
       * Interpret range
       * XXX Process multiple range-specs in header
       */
      RangeHeader rangeHeader=null;
      String rangeSpec=request.getHeader(HttpServerResponse.HDR_RANGE);
      if (rangeSpec!=null)
      { rangeHeader=new RangeHeader(rangeSpec);
      }
        
      int contentLength=(int) resource.getSize();
      if (rangeHeader!=null)
      { 
        resourceInputStream.skip(rangeHeader.getSkipBytes());
        
        // XXX Will be a problem for resources longer than MAXINT
        
        contentLength
          =(int) Math.max(0,resource.getSize()-rangeHeader.getSkipBytes());
        
        if (rangeHeader.getMaxBytes()==-1)
        { response.setContentLength(contentLength);
          
        }
        else
        { 
          response.setContentLength
            (Math.min(contentLength,rangeHeader.getMaxBytes()));
        }
      }
      
      if (rangeHeader!=null)
      { 
        response.setStatus(206);
        response.setHeader
          (HttpServerResponse.HDR_CONTENT_RANGE
          ,"bytes "
          +rangeHeader.getSkipBytes()
          +"-"
          +Math.min(resource.getSize()-1,rangeHeader.getLastByte())
          +"/"
          +resource.getSize()
          );
      }
      
      StreamUtil.copyRaw
        (resourceInputStream
        ,response.getOutputStream()
        ,_bufferSize
        ,rangeHeader!=null?rangeHeader.getMaxBytes():-1
        );
      
      resourceInputStream.close();
      response.getOutputStream().flush();
      
    }
    catch (FileNotFoundException x)
    {
      response.sendError
        (404
        ,"<H2>404 - Not Found</H2>The specified URL, <STRONG>"
        +request.getRequestURI()
        +"</STRONG> could not be found on this server."
        );
    }
    catch (IOException x)
    { 
      if (!x.getMessage().equals("Broken pipe")
          && !x.getMessage().equals("Connection reset by peer")
          )
      {
        _log.log
          (Log.WARNING
          ,"IOException retrieving "+path+": "+x.toString()
          );
      }

    }
    finally
    { 
      if (resourceInputStream!=null)
      { resourceInputStream.close();
      }
    }
  }


  private void sendDirectory
    (HttpServletRequest request
    ,HttpServletResponse response
    ,File dir
    )
    throws IOException
  {
    if (_log.isLevel(Log.DEBUG))
    { _log.log(Log.DEBUG,"Listing "+dir.getPath());
    }

    String host=request.getHeader("Host");
    String uri=request.getRequestURI();
    if (!uri.endsWith("/"))
    { uri=uri.concat("/");
    }
    response.setContentType("text/html");
    
    StringBuffer out=new StringBuffer();
    out.append("<HTML><HEAD>\r\n");
    out.append("<TITLE>Index of ");
    out.append(uri);
    out.append("</TITLE>\r\n");
    out.append("</HEAD><BODY>\r\n");
    out.append("<H1>Index of ");
    out.append(uri);
    out.append("</H1>\r\n");
    out.append("<HR>\r\n");

    if (uri.length()>1)
    {
      out.append("<A href=\"");
      out.append("http://");
      out.append(host);
      String parent=new Filename(uri).getParent();
      if (parent!=null)
      { out.append(parent);
      }
      out.append("\">Parent Directory</A>\r\n");
    }

    String[] dirs
      =dir.list
        (new FilenameFilter()
          {
            public boolean accept(File dir,String name)
            { return new File(dir,name).isDirectory();
            }
          }
        );


    out.append("<TABLE>");
    out.append("<TR>");
    out.append("<TH align=\"left\">Modified Date ("+_fileDateFormat.getTimeZone().getDisplayName()+")");
    out.append("</TH>");
    out.append("<TH align=\"left\">Size</TH>");
    out.append("<TH align=\"left\">Filename</TH>");
    out.append("</TR>");

    if (dirs!=null)
    {
      for (int i=0;i<dirs.length;i++)
      { 
        out.append("<TR>");
        File subdir=new File(dir,dirs[i]);
        out.append("<TD align=\"left\"><TT>");
        out.append(_fileDateFormat.format(new Date(subdir.lastModified())));
        out.append("</TT></TD>");

        out.append("<TD>");
        out.append("</TD>");

        out.append("<TD><TT>");
        out.append("<A href=\"");
        out.append("http://");
        out.append(host);
        out.append(uri);
        out.append(dirs[i]);
        out.append("/\">");
        out.append(dirs[i]);
        out.append("/</A>");
        out.append("</TT></TD>");
        out.append("</TR>\r\n");
      }
    }

    String[] files
      =dir.list
        (new FilenameFilter()
          {
            public boolean accept(File dir,String name)
            { return !(new File(dir,name).isDirectory());
            }
          }
        );

    if (files!=null)
    {
      for (int i=0;i<files.length;i++)
      { 
        File file=new File(dir,files[i]);
        out.append("<TR>");

        out.append("<TD  align=\"left\"><TT>");
        out.append(_fileDateFormat.format(new Date(file.lastModified())));
        out.append("</TT></TD>");

        out.append("<TD align=\"right\"><TT>");
        out.append(file.length());
        out.append("</TT></TD>");

        out.append("<TD><TT>");
        out.append("<A href=\"");
        out.append("http://");
        out.append(host);
        out.append(uri);
        out.append(files[i]);
        out.append("\">");
        out.append(files[i]);
        out.append("</A>");
        out.append("</TT></TD>");
        out.append("</TR>\r\n");
      }
    }

    out.append("</TABLE>");
    out.append("</BODY></HTML>");

    response.setContentLength(out.length());
    response.getOutputStream().write(StringUtil.asciiBytes(out.toString()));
    response.getOutputStream().flush();

  }

  private boolean isDirectory(File file)
  {
    // XXX Potentially real slow
    return file.isDirectory();
  }

  private boolean exists(File dir,String name)
  {
    // XXX Potentially real slow
    return new File(dir,name).exists();
  }
  

}
