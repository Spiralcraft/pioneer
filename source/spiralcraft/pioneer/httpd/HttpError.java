package spiralcraft.pioneer.httpd;

class HttpError
{
  final int code;
  final String message;
  final Throwable exception;

  HttpError(int code,String message,Throwable exception)
  { 
    this.code=code;
    this.message=message;
    this.exception=exception;
  }
  
}