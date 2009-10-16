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

package org.esxx.request;

import java.io.*;
import java.net.*;
import java.util.Properties;
import org.esxx.*;
import org.esxx.util.JS;
import org.esxx.util.StringUtil;
import org.esxx.util.XML;
import org.mozilla.javascript.*;

import javax.xml.transform.dom.*;
import net.sf.saxon.s9api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static net.sf.saxon.s9api.Serializer.Property.*;


public abstract class WebRequest
  extends Request
  implements ESXX.ResponseHandler {

  protected WebRequest(InputStream in, OutputStream err) {
    super(in, err);
  }

  protected void initRequest(String request_method, URI request_uri, URI path_translated,
			     Properties cgi_env, URI fs_root_uri, boolean update_env) {
    URI      script_uri        = null;
    String   path_info         = null;
    URI      script_filename   = null;
    URI      working_directory = null;
    Response quick_response    = null;

    try {
      quick_response = getEmbeddedResource(request_uri.getQuery());

      if (quick_response == null) {
	request_uri     = request_uri.normalize();
	path_translated = path_translated.normalize();
	fs_root_uri     = fs_root_uri.normalize();

	File script_file = new File(path_translated);

	if (!script_file.isAbsolute()) {
	  throw new IllegalArgumentException(path_translated + " is not an absolute path");
	}

	if (!path_translated.toString().startsWith(fs_root_uri.toString())) {
	  getReqLogger().warning("Document " + path_translated 
				 + " from request URI " + request_uri 
				 + " is outside root path " + fs_root_uri);
	  throw new FileNotFoundException("Document is outside root path");
	}

	boolean iterated = false;

	while (!script_file.exists()) {
	  script_file = script_file.getParentFile();
	  iterated = true;
	}

	if (script_file.isDirectory()) {
	  if (iterated) {
	    throw new FileNotFoundException("Resource '" + path_translated + "' not found.");
	  }

	  quick_response = getFileListingResponse(request_uri.getPath(), script_file);
	}
	else if (!ESXX.fileTypeMap.getContentType(script_file).equals("application/x-esxx+xml")) {
	  if (iterated) {
	    throw new FileNotFoundException("Only .esxx files can have a trailing path.");
	  }

	  quick_response = new Response(200, ESXX.fileTypeMap.getContentType(script_file),
					new FileInputStream(script_file), null);
	}
	else {
	  working_directory  = script_file.getParentFile().toURI();
	  script_filename    = script_file.toURI();
	  path_info          = script_filename.relativize(path_translated).getRawPath();
	  path_info          = StringUtil.decodeURI(path_info, false);
	  String req_path    = StringUtil.decodeURI(request_uri.getRawPath(), false);
	  String script_name = null;

	  if (req_path.endsWith(path_info)) {
	    script_name = req_path.substring(0, req_path.length() - path_info.length());
	    script_name = StringUtil.encodeURI(script_name, true);

	    // Create the URI version of script_name, and terminate it
	    // with a slash to make it easy to resolve subresources.
	    script_uri = request_uri.resolve(script_name + "/").normalize();
	  }

	  if (!path_info.startsWith("/")) {
	    // path_info should always begin with a slash.
	    path_info = "/" + path_info;
	  }

	  // Complete CGI environment (using native OS file paths)
	  if (update_env) {
	    cgi_env.setProperty("PATH_TRANSLATED", new File(path_translated).toString());
	    cgi_env.setProperty("PATH_INFO",       path_info);
	    cgi_env.setProperty("SCRIPT_FILENAME", script_file.toString());
	    cgi_env.setProperty("SCRIPT_NAME",     script_name);
	  }
	}
      }
    }
    catch (Exception ex) {
      quick_response = createErrorResponse(ex);
    }

    super.initRequest(request_method, request_uri, script_uri, path_info,
		      script_filename, null, working_directory, cgi_env,
		      quick_response);
  }

  public Integer handleError(Throwable ex) {
    try {
      return handleResponse(createErrorResponse(ex));
    }
    catch (Exception ex2) {
      // Hmm
      ex2.printStackTrace();
      return 20;
    }
  }

  public Integer reportInternalError(int code, 
				     String title, 
				     String subtitle, 
				     String message, 
				     Throwable ex) {
    try {
      return handleResponse(createErrorResponse(code, title, subtitle, message, ex));
    }
    catch (Exception ex2) {
      // Hmm
      ex2.printStackTrace();
      return 20;
    }
  }

  protected static URI getPathTranslated(URI    fs_root_uri,
					 String raw_request_path,
					 String context_path) {
    if (!raw_request_path.startsWith(context_path)) {
      throw new IllegalArgumentException(raw_request_path + " must begin with " + context_path);
    }

    int offset = context_path.length();

    while (offset < raw_request_path.length() && raw_request_path.charAt(offset) == '/') {
	++offset; // Trim leading slashes
    }

    return fs_root_uri.resolve(raw_request_path.substring(offset));
  }

  protected Properties createCGIEnvironment(String request_method, String protocol,
					    URI full_request_uri,
					    URI path_translated,
					    String local_host, int local_port,
					    String remote_host, int remote_port,
					    URI root_uri) {
    Properties p = new Properties();
    String query = full_request_uri.getRawQuery();

    if (query == null) {
      query = "";
    }

    if (local_host == null) {
      // Probably a Google App Engine problem
      local_host = "0.0.0.0";
    }

    p.setProperty("GATEWAY_INTERFACE", "CGI/1.1");
    p.setProperty("SERVER_SOFTWARE",   "ESXX/1.0");
    p.setProperty("DOCUMENT_ROOT",     new File(root_uri).toString());

    p.setProperty("REQUEST_METHOD",    request_method);
    p.setProperty("SERVER_NAME",       full_request_uri.getHost());
    p.setProperty("REQUEST_URI",       full_request_uri.getRawPath());
    p.setProperty("QUERY_STRING",      query);
    p.setProperty("SERVER_PROTOCOL",   protocol);

    p.setProperty("REMOTE_ADDR",       remote_host);
    p.setProperty("REMOTE_PORT",       "" + remote_port);

    p.setProperty("SERVER_ADDR",       local_host);
    p.setProperty("SERVER_PORT",       "" + local_port);

    return p;
  }

  /** A pattern that matches '!esxx-rsrc=' followed by a string of valid characters 
      and dot. (Slash is not valid.) */
  private static java.util.regex.Pattern esxxResource = 
    java.util.regex.Pattern.compile("^!esxx-rsrc=[a-zA-Z0-9.]+$");

  private Response getEmbeddedResource(String qs)
    throws IOException {
    if (qs != null && esxxResource.matcher(qs).matches()) {
      String embedded = qs.substring(11);

      InputStream rsrc = ESXX.getInstance().openCachedURI(URI.create("esxx-rsrc:" + embedded));

      if (rsrc == null) {
	throw new ESXXException(404, "Embedded resource '" + embedded + "' not found");
      }
      else {
	java.util.TreeMap<String, String> hdr = new java.util.TreeMap<String, String>();
	hdr.put("Cache-Control", "max-age=3600");

	return new Response(200, ESXX.fileTypeMap.getContentType(embedded),
			    rsrc, hdr);
      }
    }
    else {
      return null;
    }
  }

  private Response createErrorResponse(Throwable ex) {
    int    code     = ex instanceof ESXXException ? ((ESXXException) ex).getStatus() : 500;
    String title    = "ESXX Server Error";
    String subtitle = "Unhandled exception: " + ex.getClass().getSimpleName();
    String message  = ex.getMessage();

    if (ex instanceof FileNotFoundException) {
      code     = 404;
      subtitle = "Not Found";
    }
    
    if (ex instanceof ESXXException ||
	ex instanceof FileNotFoundException ||
	ex instanceof javax.xml.transform.TransformerException) {
      // Don't print stack trace
      ex = null;
    }

    return createErrorResponse(code, title, subtitle, message, ex);
  }

  private Response createErrorResponse(int code, 
				       String title, 
				       String subtitle, 
				       String message, 
				       Throwable ex) {
    ESXX    esxx = ESXX.getInstance();
    Document doc = esxx.createDocument("error");
    Element root = doc.getDocumentElement();

    String stacktrace = null;

    if (ex != null) {
      if (ex instanceof RhinoException) {
	RhinoException re = (RhinoException) ex;
	stacktrace = re.getScriptStackTrace(new JS.JSFilenameFilter());

	message = message + " [" + re.sourceName() + ", line " + re.lineNumber()
	  + ", column " + re.columnNumber() + ": " + re.lineSource() + "]";
      }
      else {
	StringWriter sw = new StringWriter();
	PrintWriter  pw = new PrintWriter(sw);
	ex.printStackTrace(pw);
	pw.flush();
	stacktrace = sw.toString();
      }
    }

    // Log all unhandled error responses
    getReqLogger().log(java.util.logging.Level.WARNING, message, ex);

    XML.addChild(root, "title",    title);
    XML.addChild(root, "subtitle", subtitle);
    XML.addChild(root, "message",  message);
    
    if (stacktrace != null) {
      XML.addChild(root, "stacktrace", stacktrace);
    }

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    String ct = renderHTML(doc, os);

    return new Response(code, ct, os, null);
  }

  private Response getFileListingResponse(String req_uri, File dir)
    throws Exception {
    Document doc = org.esxx.js.protocol.FILEHandler.createDirectoryListing(dir);
    doc.getDocumentElement().setAttributeNS(null, "requestURI", req_uri);

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    String ct = renderHTML(doc, os);

    return new Response(200, ct, os, null);
  }

  private String renderHTML(Document doc, OutputStream dst) {
    try {
      ESXX       esxx = ESXX.getInstance();
      Stylesheet xslt = esxx.getCachedStylesheet(new URI("esxx-rsrc:esxx.xslt"));

      XsltExecutable  xe = xslt.getExecutable();
      XsltTransformer tr = xe.load();
      Serializer       s = new Serializer();

      s.setOutputStream(dst);

      // Remove this code when upgrading to Saxon 9.1 (?)
      Properties op = xe.getUnderlyingCompiledStylesheet().getOutputProperties();
      s.setOutputProperty(BYTE_ORDER_MARK,        op.getProperty("byte-order-mark"));
      s.setOutputProperty(CDATA_SECTION_ELEMENTS, op.getProperty("cdata-section-elements"));
      s.setOutputProperty(DOCTYPE_PUBLIC,         op.getProperty("doctype-public"));
      s.setOutputProperty(DOCTYPE_SYSTEM,         op.getProperty("doctype-system"));
      s.setOutputProperty(ENCODING,               op.getProperty("encoding"));
      s.setOutputProperty(ESCAPE_URI_ATTRIBUTES,  op.getProperty("escape-uri-attributes"));
      s.setOutputProperty(INCLUDE_CONTENT_TYPE,   op.getProperty("include-content-type"));
      s.setOutputProperty(INDENT,                 op.getProperty("indent"));
      s.setOutputProperty(MEDIA_TYPE,             op.getProperty("media-type", "text/html"));
      s.setOutputProperty(METHOD,                 op.getProperty("method"));
      //    s.setOutputProperty(NORMALIZATION_FORM,     op.getProperty("normalization-form"));
      s.setOutputProperty(OMIT_XML_DECLARATION,   op.getProperty("omit-xml-declaration"));
      s.setOutputProperty(STANDALONE,             op.getProperty("standalone"));
      s.setOutputProperty(UNDECLARE_PREFIXES,     op.getProperty("undeclare-prefixes"));
      s.setOutputProperty(USE_CHARACTER_MAPS,     op.getProperty("use-character-maps"));
      s.setOutputProperty(VERSION,                op.getProperty("version"));

      tr.setSource(new DOMSource(doc));
      tr.setDestination(s);
      tr.transform();

      return s.getOutputProperty(MEDIA_TYPE);
    }
    catch (Exception ex) {
      // This should never happen
      PrintWriter pw = new PrintWriter(dst);
      ex.printStackTrace(pw);
      pw.close();
      return "text/plain";
    }
  }
}
