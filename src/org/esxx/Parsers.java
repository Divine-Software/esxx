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

import au.com.bytecode.opencsv.CSVReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URI;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.mail.Header;
import javax.mail.Session;
import javax.mail.internet.ContentDisposition;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.SharedFileInputStream;
import nu.validator.htmlparser.common.*;
import nu.validator.htmlparser.dom.*;
import org.esxx.util.IO;
import org.esxx.util.JS;
import org.esxx.util.StringUtil;
import org.json.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

class Parsers {
  public Parsers() {
    Parser schema_parser = new SchemaParser();

    parserMap.put("application/json",                    new JSONParser());
    parserMap.put("application/octet-stream",            new BinaryParser());
    parserMap.put("application/relax-ng-compact-syntax", schema_parser);
    parserMap.put("application/x-nrl+xml",               schema_parser);
    parserMap.put("application/x-nvdl+xml",              schema_parser);
    parserMap.put("application/x-rnc",                   schema_parser);
    parserMap.put("application/x-rng+xml",               schema_parser);
    parserMap.put("application/x-schematron+xml",        schema_parser);
    parserMap.put("application/x-www-form-urlencoded",   new FormParser());
    parserMap.put("application/x-xsd+xml",               schema_parser);
    parserMap.put("application/xml",                     new XMLParser());
    parserMap.put("image/*",                             new ImageParser());
    parserMap.put("message/rfc822",                      new MIMEParser());
    parserMap.put("multipart/form-data",                 new MultipartFormParser());
    parserMap.put("text/csv",                            new CSVParser());
    parserMap.put("text/html",                           new HTMLParser());
    parserMap.put("text/plain",                          new StringParser());
    parserMap.put("text/xml",                            new XMLParser());
  }

  public Object parse(ContentType ct, InputStream is, final URI is_uri,
		      Collection<URI> external_uris,
		      PrintWriter err,
		      Context cx, Scriptable scope)
    throws Exception {
    // Read-only accesses; no syncronization required
    Parser parser = parserMap.get(ct.getBaseType());

    if (parser == null) {
      if (ct.getBaseType().endsWith("+xml")) {
	parser = parserMap.get("application/xml");
      }
      else if (ct.match("image/*")) {
	parser = parserMap.get("image/*");
      }
      else if (ct.match("text/*")) {
	parser = parserMap.get("text/plain");
      }
      else {
	parser = parserMap.get("application/octet-stream");
      }
    }

    Object result = null;

    try {
      result = parser.parse(ct, is, is_uri,
			    external_uris, err, cx, scope);
    }
    finally {
      if (result != is) {
	is.close();
      }
    }

    return result;
  }

  public static String getParameter(ContentType ct, String name, String def) {
    String value = ct.getParameter(name);
    return value == null ? def : value;
  }

  /** The interface all parsers must implement */
  private interface Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws Exception;
  }

  /** A HashMap of all registered parsers */
  private HashMap<String, Parser> parserMap = new HashMap<String, Parser>();


  /** A Parser that returns the InputStream as-is, or loads it into a ByteBuffer */
  private static class BinaryParser
    implements Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws IOException, org.xml.sax.SAXException {
      String format = getParameter(ct, "x-format", "stream");

      if (format.equals("stream")) {
	return is;
      }
      else if (format.equals("buffer")) {
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	IO.copyStream(is, bos);
	return java.nio.ByteBuffer.wrap(bos.toByteArray());
      }
      else {
	throw new IOException("Invalid value in param 'x-format=" + format + "'");
      }
    }
  }

  /** A Parser that reads characters and returns a string. */
  private static class StringParser
    implements Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws IOException {
      String cs = getParameter(ct, "charset", "UTF-8");

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      IO.copyStream(is, bos);
      return bos.toString(cs);
    }
  }

  /** A Parser that parses CSV files and returns a JavaScript Array of Arrays. */
  private static class CSVParser
    implements Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws IOException {
      String cs        = getParameter(ct, "charset", "UTF-8");
      String header    = getParameter(ct, "header", "absent");
      String separator = getParameter(ct, "x-separator", ",");
      String quote     = getParameter(ct, "x-quote", "\"");
      String escape    = getParameter(ct, "x-escape", "\\");

      if (!header.equals("present") && !header.equals("absent")) {
	throw new IOException("Invalid value in param 'header=" + header + "'");
      }

      if ("none".equals(separator)) separator = "";
      if ("none".equals(quote))     quote     = "";
      if ("none".equals(escape))    escape    = "";

      if (separator.length() > 1 || quote.length() > 1 || escape.length() > 1) {
	throw new IOException("x-separator, x-quote and x-escape values must be " +
			      "empty or a single character");
      }

      CSVReader csv = new CSVReader(new InputStreamReader(is, cs),
				    separator.isEmpty() ? '\0' : separator.charAt(0),
				    quote.isEmpty()     ? '\0' : quote.charAt(0),
				    escape.isEmpty()    ? '\0' : escape.charAt(0));
      
      List<String[]> values = csv.readAll();
      String[] headers = header.equals("present") && !values.isEmpty() ? values.remove(0) : null;

      Scriptable res = cx.newArray(scope, values.size());

      if (headers != null) {
	res.put("headers", res, JS.toJSArray(cx, scope, headers));
      }

      int i = 0;
      for (String[] value : values) {
	res.put(i++, res, JS.toJSArray(cx, scope, value));
      }

      return res;
    }
  }

  /** A Parser that parses XML and returns an E4X XML Node. */
  private static class XMLParser
    implements Parser {
    public Object parse(ContentType ct,	InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws IOException, org.xml.sax.SAXException {
      Document result = ESXX.getInstance().parseXML(is, is_uri, external_uris, err);
      return ESXX.domToE4X(result, cx, scope);
    }
  }

  /** A Parser that parses HTML and returns XHTML as an E4X XML Node. */
  private static class HTMLParser
    implements Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws IOException  {
      try {
	HtmlDocumentBuilder hdb = new HtmlDocumentBuilder(XmlViolationPolicy.ALTER_INFOSET);

	hdb.setHtml4ModeCompatibleWithXhtml1Schemata(true);
	hdb.setIgnoringComments(false);
	hdb.setMappingLangToXmlLang(true);
	hdb.setScriptingEnabled(false);

	hdb.setEntityResolver(new EntityResolver() {
	    @Override public InputSource resolveEntity(String publicId, String systemId) {
	      return new InputSource(new StringReader(" ")); // Fake almost empty doc
	    }
	  });

	InputSource source = new InputSource(is);
	source.setEncoding(ct.getParameter("charset"));

	return ESXX.domToE4X(hdb.parse(source), cx, scope);
      }
      catch (org.xml.sax.SAXException ex) {
	throw new IOException("Failed to parse HTML document" + ex.getMessage(), ex);
      }
    }
  }

  /** A Parser that parses HTML Form submissions and returns a JavaScript Object. */
  private static class FormParser
    implements Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws IOException {
      String cs = getParameter(ct, "charset", "UTF-8");

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      IO.copyStream(is, bos);

      Scriptable result = cx.newObject(scope);
      StringUtil.decodeFormVariables(bos.toString(cs), result);
      return result;
    }
  }

  /** A Parser that parses HTML Multipart Form submissions and returns
   * a JavaScript Object. */
  private static class MultipartFormParser
    implements Parser {
    public MultipartFormParser() {
      // Make "mail.mime.encodefilename" true if unspecified. Note
      // that this property is a System property and not a
      // Session.getInstance() parameter.
 
      Properties p = System.getProperties();
      p.setProperty("mail.mime.encodefilename",
		    p.getProperty("mail.mime.encodefilename", "true"));
    }

    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws Exception {
      try {
	ESXX esxx = ESXX.getInstance();
	File temp = esxx.createTempFile(cx);

	// Add required MIME header and stream data to a temporary file
	FileOutputStream fos = new FileOutputStream(temp);
	fos.write(("Content-Type: " + ct
		   + "\r\n\r\n").getBytes());
	IO.copyStream(is, fos);
	fos.close();

	Scriptable result = cx.newObject(scope);

	// Parse request entity using SharedFileInputStream to avoid
	// excessive memory usage.  TODO: Create a custom
	// SharedFileInputStream that allows us to read the absolute
	// file offset, and add support in FILEHandler to read URIs
	// with offset and length parameters. This would avoid the
	// disk-to-disk copying that happens when extracting the
	// actual file parts.
	Session session = Session.getInstance(System.getProperties());
	MimeMessage message = new MimeMessage(session, new SharedFileInputStream(temp));

	MimeMultipart mmp = (MimeMultipart) message.getContent();
	for (int i = 0; i < mmp.getCount(); ++i) {
	  MimeBodyPart mbp = (MimeBodyPart) mmp.getBodyPart(i);
	  String[] disp = mbp.getHeader("Content-Disposition");

	  if (disp.length == 0) {
	    // We don't handle parts with no Content-Disposition header
	    continue;
	  }
	  
	  String name     = new ContentDisposition(disp[0]).getParameter("name");
	  String filename = mbp.getFileName();
	  Object value;

	  if (filename == null) {
	    // Not a file, so parse it
	    String part_ct = mbp.getContentType();

	    // Default content-type is text/plain for
	    // multipart/form-data parts
	    if (part_ct == null) {
	      part_ct = "text/plain";
	    }

	    value = esxx.parseStream(part_ct, mbp.getInputStream(), temp.toURI(),
				     null, err, cx, scope);
	    value = Context.javaToJS(value, scope);
	  }
	  else {
	    // Create a new temporary file and create a description as value.
	    temp = esxx.createTempFile(cx);
	    fos = new FileOutputStream(temp);
	    IO.copyStream(mbp.getInputStream(), fos);
	    fos.close();
	    
	    Scriptable descr = cx.newObject(scope);
	    descr.put("uri",    descr, temp.toURI().toString());
	    descr.put("name",   descr, filename);
	    descr.put("length", descr, temp.length());

	    Scriptable headers = cx.newObject(scope);
	    for (Enumeration<?> e = mbp.getAllHeaders(); e.hasMoreElements();) {
	      Header hdr = (Header) e.nextElement();
	      headers.put(hdr.getName(), headers, hdr.getValue());
	    }
	    descr.put("headers", descr, headers);

	    value = descr;
	  }

	  if (name != null) {
	    result.put(name, result, value);
	  }
	  else {
	    result.put(i, result, value);
	  }
	}

	return result;
      }
      catch (ClassCastException ex) {
	throw new IOException("Failed to parse form data: " + ex.getMessage(), ex);
      }
      catch (javax.mail.MessagingException ex) {
	throw new IOException("Failed to parse form data: " + ex.getMessage(), ex);
      }
    }
  }

  /** A Parser that parses JSON and returns a JavaScript Array or Object. */
  private static class JSONParser
    implements Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws IOException {
      String cs = getParameter(ct, "charset", "UTF-8");

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      IO.copyStream(is, bos);

      try {
	JSONTokener tok = new JSONTokener(bos.toString(cs));

	char first = tok.nextClean();
	tok.back();

	if (first == '{') {
	  return jsonToJS(new JSONObject(tok), cx, scope);
	}
	else if (first == '[') {
	  return jsonToJS(new JSONArray(tok), cx, scope);
	}
	else {
	  throw new IOException("Not a JSON Array or Object");
	}
      }
      catch (JSONException ex) {
	throw new IOException("Failed to parse JSON data: " + ex.getMessage(), ex);
      }
    }

    private static Object jsonToJS(Object json, Context cx, Scriptable scope)
      throws IOException, JSONException {
      Scriptable res;

      if (json == JSONObject.NULL) {
	return null;
      }
      else if (json instanceof String ||
	       json instanceof Number ||
	       json instanceof Boolean) {
	return json;
      }
      else if (json instanceof JSONObject) {
	JSONObject jo = (JSONObject) json;
	res  = cx.newObject(scope);

	for (Iterator<?> i = jo.keys(); i.hasNext();) {
	  String  key = (String) i.next();
	  Object  val = jsonToJS(jo.get(key), cx, scope);
	  res.put(key, res, val);
	}
      }
      else if (json instanceof JSONArray) {
	JSONArray ja = (JSONArray) json;
	res = cx.newArray(scope, ja.length());

	for (int i = 0; i < ja.length(); ++i) {
	  Object val = jsonToJS(ja.get(i), cx, scope);
	  res.put(i, res, val);
	}
      }
      else {
	res = Context.toObject(json, scope);
      }

      return res;
    }
  }

  /** A Parser that parses emails and returns an E4X XML Node. */
  private static class MIMEParser
    implements Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws IOException, org.xml.sax.SAXException {
      boolean xmtp;
      boolean ns;
      boolean html;
      boolean js;

      String fmt = getParameter(ct, "x-format", "esxx");
      String prc = ct.getParameter("x-process-html");
      String sjs = ct.getParameter("x-strip-js");

      if (fmt.equals("esxx")) {
	xmtp = false;
	ns   = false;
	html = true;
	js   = false;
      }
      else if (fmt.equals("xmtp")) {
	xmtp = true;
	ns   = true;
	html = false;
	js   = false;
      }
      else if (fmt.equals("xios")) {
	xmtp = false;
	ns   = true;
	html = true;
	js   = false;
      }
      else {
	throw new IOException("Invalid value in param 'x-format=" + fmt + "'");
      }

      if (prc == null) {
	// Leave html as-is
      }
      else if (prc.equals("true")) {
	html = true;
      }
      else if (prc.equals("false")) {
	html = false;
      }
      else {
	throw new IOException("Invalid value in param 'x-process-html=" + prc + "'");
      }


      if (sjs == null) {
	// Leave js as-is
      }
      else if (sjs.equals("true")) {
	js = true;
      }
      else if (sjs.equals("false")) {
	js = false;
      }
      else {
	throw new IOException("Invalid value in param 'x-strip-js=" + sjs + "'");
      }

      if (js) {
	html = true;
      }

      try {
	org.esxx.xmtp.MIMEParser p = new org.esxx.xmtp.MIMEParser(xmtp, ns, html, js, true);
	p.convertMessage(is);
	Document result = p.getDocument();
	return ESXX.domToE4X(result, cx, scope);
      }
      catch (Exception ex) {
	throw new IOException("Unable to parse email message", ex);
      }
    }
  }

  /** A Parser that parses images and returns a BufferedImage Java object. */
  private static class ImageParser
    implements Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope)
      throws IOException {
      if (ct.getBaseType().equals("image/*")) {
	return ImageIO.read(is);
      }
      else {
	Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(ct.getBaseType());

	if (readers.hasNext()) {
	  ImageReader reader = readers.next();
	  String      index  = ct.getParameter("x-index");

	  reader.setInput(new FileCacheImageInputStream(is, null));
	  return reader.read(index != null ? Integer.parseInt(index) : 0);
	}
	else {
	  return null;
	}
      }
    }
  }

  /** A Parser that parses XML Schemas and returns a JavaScript ESXX.Schema object. */
  private static class SchemaParser
    implements Parser {
    public Object parse(ContentType ct, InputStream is, URI is_uri,
			Collection<URI> external_uris,
			PrintWriter err, Context cx, Scriptable scope) {
      // NB: If the schema is already loaded into the schema cache
      // (with key 'is_uri'), the 'is' argument will be left unused.
      return org.esxx.js.JSSchema.newJSSchema(cx, scope, is_uri, is, ct.getBaseType());
    }
  }
}
