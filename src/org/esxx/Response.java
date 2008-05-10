/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2008 Martin Blom <martin@blom.org>
     
     This program is free software: you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation, either version 3
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.esxx;

import java.awt.image.RenderedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import org.json.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Node;

public class Response  {

  public static interface HeaderEnumerator {
    public void header(String name, String value);
  }

  public Response(int status, String content_type, Object result, Map<String, String> headers) {
    setStatus(status);
    setResult(result);

    if (resultObject != null && content_type == null) {
      // Set default content-type, if missing
      if (resultObject instanceof InputStream ||
	  resultObject instanceof ByteArrayOutputStream ||
	  resultObject instanceof ByteBuffer) {
	content_type = "application/octet-stream";
      }
      else if (resultObject instanceof Reader ||
	       resultObject instanceof String) {
	content_type = "text/plain; charset=UTF-8";
      }
      else if (resultObject instanceof RenderedImage) {
	content_type = "image/png";
      }
      else if (resultObject instanceof Node) {
	content_type = "application/xml";
      }
      else if (resultObject instanceof Scriptable) {
	content_type = "application/json";
      }
    }

    setContentType(content_type);
    httpHeaders = headers;
  }

  public int getStatus() {
    return httpStatus;
  }

  public void setStatus(int status) {
    httpStatus = status;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String content_type) {
    contentType = content_type;
  }

  public Object getResult() {
    return resultObject;
  }

  public void setResult(Object result) {
    // Unwrap wrapped objects
    while (result instanceof Wrapper) {
      result = ((Wrapper) result).unwrap();
    }

    // Convert to "primitive" types
    if (result instanceof org.mozilla.javascript.xml.XMLObject) {
      result = ESXX.e4xToDOM((Scriptable) result);
    }
    else if (result instanceof byte[]) {
      result = new ByteArrayInputStream((byte[]) result);
    }

    resultObject = result;
  }

  public Map headers() {
    return httpHeaders;
  }


  public void enumerateHeaders(HeaderEnumerator he) {
    if (httpHeaders != null) {
      for (Map.Entry<String, String> e : httpHeaders.entrySet()) {
	he.header(e.getKey(), e.getValue());
      }
    }
  }

  public void writeResult(ESXX esxx, Context cx, OutputStream out)
    throws IOException {
    HashMap<String,String> mime_params = new HashMap<String,String>();
    String mime_type = ESXX.parseMIMEType(contentType, mime_params);

    Object object = resultObject;

    if (object instanceof Node) {
      object = esxx.serializeNode((Node) object);
    }
    else if (object instanceof Scriptable) {
      object = jsToJSON((Scriptable) object, cx).toString();
    }
    
    if (object instanceof ByteArrayOutputStream) {
      ByteArrayOutputStream bos = (ByteArrayOutputStream) object;

      bos.writeTo(out);
    }
    else if (object instanceof ByteBuffer) {
      // Write result as-is to output stream
      WritableByteChannel wbc = Channels.newChannel(out);
      ByteBuffer          bb  = (ByteBuffer) object;

      bb.rewind();

      while (bb.hasRemaining()) {
	wbc.write(bb);
      }

      wbc.close();
    }
    else if (object instanceof InputStream) {
      ESXX.copyStream((InputStream) object, out);
    }
    else if (object instanceof Reader) {
      // Write stream as-is, using the specified charset (if present)
      String cs = mime_params.get("charset");

      if (cs == null) {
	cs = "UTF-8";
      }

      Writer ow = new OutputStreamWriter(out, cs);
      
      ESXX.copyReader((Reader) object, ow);
    }
    else if (object instanceof String) {
      // Write string as-is, using the specified charset (if present)
      String cs = mime_params.get("charset");

      if (cs == null) {
	cs = "UTF-8";
      }

      Writer ow = new OutputStreamWriter(out, cs);
      ow.write((String) object);
      ow.flush();
    }
    else if (object instanceof RenderedImage) {
      Iterator<ImageWriter> i = ImageIO.getImageWritersByMIMEType(mime_type);

      if (!i.hasNext()) {
	throw new ESXXException("No ImageWriter available for " + mime_type);
      }

      ImageWriter writer = i.next();
      
      writer.setOutput(ImageIO.createImageOutputStream(out));
      writer.write((RenderedImage) object);
    }
    else {
      throw new UnsupportedOperationException("Unsupported object class type: " 
					      + object.getClass());
    }
  }

  private static Object jsToJSON(Object object, Context cx) {
    try {
      if (object instanceof NativeArray) {
	Object[] array = cx.getElements((Scriptable) object);
      
	for (int i = 0; i < array.length; ++i) {
	  array[i] = jsToJSON(array[i], cx);
	}

	object = new JSONArray(array).toString();
      }
      else if (object instanceof Scriptable) {
	Scriptable jsobject = (Scriptable) object;

	object = new JSONObject();

	for (Object k : jsobject.getIds()) {
	  if (k instanceof String) {
	    String key = (String) k;
	    ((JSONObject) object).put(key, jsToJSON(jsobject.get(key, jsobject), cx));
	  }
	}
      }
      else {
	object = Context.jsToJava(object, Object.class);
      }
    
      return object;
    }
    catch (JSONException ex) {
      throw new ESXXException("Failed to convert JavaScript object to JSON: " + ex.getMessage(), 
			      ex);
    }
  }


  private int httpStatus;
  private String contentType;
  private Object resultObject;
  private Map<String, String> httpHeaders;
}
