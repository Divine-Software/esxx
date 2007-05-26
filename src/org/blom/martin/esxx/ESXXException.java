
package org.blom.martin.esxx;

public class ESXXException 
  extends Exception {
    public ESXXException(String why) { 
      super(why);
      statusCode = 500;
    }

    public ESXXException(String why, Throwable cause) { 
      super(why, cause); 
      statusCode = 500;
    }

    public ESXXException(int status, String why) { 
      super(why); 
      statusCode = status;
    }

    public ESXXException(int status, String why, Throwable cause) { 
      super(why, cause); 
      statusCode = status;
    }

    public int getStatus() {
      return statusCode;
    }

    private int statusCode;
}
