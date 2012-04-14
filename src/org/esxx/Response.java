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

import au.com.bytecode.opencsv.CSVWriter;
import java.awt.image.RenderedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.mail.internet.ContentType;
import org.esxx.util.IO;
import org.esxx.util.JS;
import org.esxx.util.PropertyBag;
import org.esxx.util.StringUtil;
import org.json.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Node;

public class Response  {

  public static interface HeaderEnumerator {
    public void header(String name, String value);
  }

  public Response(int status, String content_type, Object result, Map<String, String> headers) {
    setStatus(status);
    setContentType(content_type);
    setResult(result);
    httpHeaders = headers;
    contentLength = -1;
  }

  public int getStatus() {
    return httpStatus;
  }

  public void setStatus(int status) {
    httpStatus = status;
  }

  public String getContentType(boolean guess) {
    if (guess) {
      return guessContentType();
    }
    else {
      return contentType;
    }
  }

  public void setContentType(String content_type) {
    contentType = content_type;
  }

  public Object getResult() {
    return resultObject;
  }

  public void setResult(Object result) {
    resultObject = result;
  }

  public void setBuffered(boolean bool) {
    buffered = bool;
  }

  public boolean isBuffered() {
    return buffered;
  }

  public long getContentLength()
    throws IOException {
    if (!buffered) {
      throw new IllegalStateException("getContentLength() only works on buffered responses");
    }

    if (contentLength == -1) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();

      writeResult(bos);
      setResult(bos.toByteArray());
      contentLength = bos.size();
    }

    return contentLength;
  }

  public void unwrapResult() {
    resultObject = JS.toJavaObject(resultObject);
  }

  public Map<String, String> headers() {
    return httpHeaders;
  }


  public void enumerateHeaders(HeaderEnumerator he) {
    if (httpHeaders != null) {
      for (Map.Entry<String, String> e : httpHeaders.entrySet()) {
	he.header(e.getKey(), e.getValue());
      }
    }
  }

  public void writeResult(OutputStream out)
    throws IOException {
    try {
      writeObject(resultObject, new ContentType(guessContentType()), out);
    }
    catch (javax.mail.internet.ParseException ex) {
      throw new IOException("Invalid content-type: " + ex.getMessage(), ex);
    }
  }

  public static void writeObject(Object object, ContentType ct, OutputStream out)
    throws IOException {

    if (object == null) {
      return;
    }

    // Unwrap wrapped objects
    object = JS.toJavaObject(object);

    // Convert complex types to primitive types
    if (object instanceof Node) {
      ESXX esxx = ESXX.getInstance();

      if (ct.match("message/rfc822")) {
	try {
	  String xml = esxx.serializeNode((Node) object);
	  org.esxx.xmtp.XMTPParser xmtpp = new org.esxx.xmtp.XMTPParser();
	  javax.mail.Message msg = xmtpp.convertMessage(new StringReader(xml));
	  object = new ByteArrayOutputStream();
	  msg.writeTo(new FilterOutputStream((OutputStream) object) {
	      @Override
	      public void write(int b)
		throws IOException {
		if (b == '\r') {
		  return;
		}
		else if (b == '\n') {
		  out.write('\r');
		  out.write('\n');
		}
		else {
		  out.write(b);
		}
	      }
	    });
	}
	catch (javax.xml.stream.XMLStreamException ex) {
	  throw new ESXXException("Failed to serialize Node as message/rfc822:" + ex.getMessage(),
				  ex);
	}
	catch (javax.mail.MessagingException ex) {
	  throw new ESXXException("Failed to serialize Node as message/rfc822:" + ex.getMessage(),
				  ex);
	}
      }
      else {
	object = esxx.serializeNode((Node) object);
      }
    }
    else if (object instanceof Scriptable) {
      if (ct.match("application/x-www-form-urlencoded")) {
	String cs = Parsers.getParameter(ct, "charset", "UTF-8");

	object = StringUtil.encodeFormVariables(cs, (Scriptable) object);
      }
      else if (ct.match("text/csv")) {
	object = jsToCSV(ct, (Scriptable) object);
      }
      else {
	object = jsToJSON(object).toString();
      }
    }
    else if (object instanceof byte[]) {
      object = new ByteArrayInputStream((byte[]) object);
    }
    else if (object instanceof File) {
      object = new FileInputStream((File) object);
    }

    // Serialize primitive types
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
      IO.copyStream((InputStream) object, out);
    }
    else if (object instanceof Reader) {
      // Write stream as-is, using the specified charset (if present)
      String cs = Parsers.getParameter(ct, "charset", "UTF-8");
      Writer ow = new OutputStreamWriter(out, cs);

      IO.copyReader((Reader) object, ow);
    }
    else if (object instanceof String) {
      // Write string as-is, using the specified charset (if present)
      String cs = Parsers.getParameter(ct, "charset", "UTF-8");
      Writer ow = new OutputStreamWriter(out, cs);
      ow.write((String) object);
      ow.flush();
    }
    else if (object instanceof RenderedImage) {
      Iterator<ImageWriter> i = ImageIO.getImageWritersByMIMEType(ct.getBaseType());

      if (!i.hasNext()) {
	throw new ESXXException("No ImageWriter available for " + ct.getBaseType());
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

  public String guessContentType() {
    if (contentType == null) {
      // Set default content-type, if missing
      if (resultObject instanceof InputStream ||
	  resultObject instanceof ByteArrayOutputStream ||
	  resultObject instanceof ByteBuffer ||
	  resultObject instanceof byte[]) {
	return "application/octet-stream";
      }
      else if (resultObject instanceof File) {
	return ESXX.fileTypeMap.getContentType((File) resultObject);
      }
      else if (resultObject instanceof Reader ||
	       resultObject instanceof String) {
	return "text/plain; charset=UTF-8";
      }
      else if (resultObject instanceof RenderedImage) {
	return "image/png";
      }
      else if (resultObject instanceof Node ||
	       resultObject instanceof org.mozilla.javascript.xml.XMLObject) {
	return "application/xml";
      }
      else if (resultObject instanceof Scriptable) {
	return "application/json";
      }
      else {
	return "application/octet-stream";
      }
    }

    return contentType;
  }

  private static Object jsToJSON(Object object) {
    try {
      if (object instanceof NativeArray) {
	Object[] array = Context.getCurrentContext().getElements((Scriptable) object);

	for (int i = 0; i < array.length; ++i) {
	  array[i] = jsToJSON(array[i]);
	}

	object = new JSONArray(array).toString();
      }
      else if (object instanceof Wrapper) {
	object = jsToJSON(((Wrapper) object).unwrap());
      }
      else if (object instanceof Scriptable) {
	Scriptable jsobject = (Scriptable) object;

	object = new JSONObject();

	for (Object k : jsobject.getIds()) {
	  if (k instanceof String) {
	    String key = (String) k;
	    ((JSONObject) object).put(key, jsToJSON(jsobject.get(key, jsobject)));
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

  static private Object jsToCSV(ContentType ct, Scriptable object)
    throws IOException {
    Context cx = Context.getCurrentContext();

    String separator = Parsers.getParameter(ct, "x-separator", ",");
    String quote     = Parsers.getParameter(ct, "x-quote", "\"");
    String escape    = Parsers.getParameter(ct, "x-escape", "\"");

    if ("none".equals(separator)) separator = "";
    if ("none".equals(quote))     quote     = "";
    if ("none".equals(escape))    escape    = "";

    if (separator.length() > 1 || quote.length() > 1 || escape.length() > 1) {
      throw new IOException("x-separator, x-quote and x-escape values must be " +
			    "empty or a single character");
    }

    PropertyBag                 rows = PropertyBag.get(cx, object);
    TreeMap<String, Integer> columns = new TreeMap<String, Integer>();

    // Find all possible colunms
    for (Object r : rows.getValues(cx)) {
      PropertyBag row = PropertyBag.get(cx, r);

      for (Object c : row.getKeys(cx)) {
	columns.put(StringUtil.toSortable(c), null);
      }
    }

    // "Sort" them
    int cnt = 0;
    for (Map.Entry<String, Integer> e : columns.entrySet()) {
      e.setValue(cnt++);
    }

    // Write lines
    StringWriter sw = new StringWriter();
    CSVWriter csv = new CSVWriter(sw,
				  separator.isEmpty() ? '\0' : separator.charAt(0),
				  quote.isEmpty()     ? '\0' : quote.charAt(0),
				  escape.isEmpty()    ? '\0' : escape.charAt(0));

    for (Object k : rows.getKeys(cx)) {
      writeCSVRow(cx, csv, columns, rows.getValue(cx, k));
    }

    return sw.toString();
  }

  static private void writeCSVRow(Context cx, CSVWriter csv,
				  TreeMap<String, Integer> columns, Object r) {
    PropertyBag row = PropertyBag.get(cx, r);
    String[] fields = new String[columns.size()];

    for (Object c : row.getKeys(cx)) {
      int idx = columns.get(StringUtil.toSortable(c));

      fields[idx] = Context.toString(row.getValue(cx, c));
    }

    csv.writeNext(fields);
  }

  private int httpStatus;
  private String contentType;
  private Object resultObject;
  private long contentLength;
  private boolean buffered;
  private Map<String, String> httpHeaders;
}
