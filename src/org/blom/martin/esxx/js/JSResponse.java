
package org.blom.martin.esxx.js;

import org.blom.martin.esxx.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import org.mozilla.javascript.*;

public class JSResponse 
  extends ScriptableObject {
    public JSResponse() {
      super();
    }

    public JSResponse(String status, String content_type, Object result)
      throws ESXXException {
      this();

      // Unwrap wrapped objects
      if (result instanceof Wrapper) {
	result = ((Wrapper) result).unwrap();
      }

      // Check for valid result
      if (result instanceof ByteArrayOutputStream ||
	  result instanceof ByteBuffer) {
	if (content_type == null) {
	  content_type = "application/octent-stream";
	}
      }
      else if (result instanceof String) {
	if (content_type == null) {
	  content_type = "text/plain;charset=" + 
	    java.nio.charset.Charset.defaultCharset().name();
	}
      }
      else if (result instanceof BufferedImage) {
	if (content_type == null) {
	  content_type = "image/png";
	}

	// TODO ...
	throw new ESXXException("BufferedImage results not supported yet.");
      }
      else {
	try {
	  result = (org.mozilla.javascript.xmlimpl.XMLLibImpl.toDomNode(result));
	}
	catch (Exception ex) {
	  throw new ESXXException("Unsupported result type: " + result.getClass());
	}
      }

      this.status  = status;
      contentType  = content_type;
      this.result  = result;
    }


    static public Object jsConstructor(Context cx, 
				       java.lang.Object[] args, 
				       Function ctorObj, 
				       boolean inNewExpr) 
      throws ESXXException {
      String status;
      String content_type;
      Object result;

      switch (args.length) {
	case 1:
	  status       = "200 OK";
	  content_type = null;
	  result       = args[0];
	  break;
	 
	case 2:
	  status       = "200 OK";
	  content_type = Context.toString(args[0]);
	  result       = args[1];
	  break;
	  
	case 3:
	  status       = Context.toString(args[0]);
	  content_type = Context.toString(args[1]);
	  result       = args[2];
	  break;

	default:
	  throw Context.reportRuntimeError("Response() constructor requires 1-3 arguments."); 
      }

      return new JSResponse(status, content_type, result);
    }

    public String getClassName() {
      return "Response";
    }


    public void jsFunction_addHeader(String name, String value) {
      ScriptableObject.putProperty(this, name, value);
    }

    public String getStatus() {
      return status;
    }

    public String getContentType() {
      return contentType;
    }


    public Object getResult() {
      return result;
    }


    public void setContentType(String content_type) {
      contentType = content_type;
    }


    public void setResult(Object result) {
      this.result = result;
    }


    public static interface HeaderEnumerator {
	public void header(String name, String value);
    }

    public void enumerateHeaders(HeaderEnumerator he) {
      for (Object hdr : getIds()) {
	if (hdr instanceof String) {
	  String name = (String) hdr;

	  he.header(name, Context.toString(ScriptableObject.getProperty(this, name)));
	}
      }
    }

    private String status;
    private String contentType;
    private Object result;
}
