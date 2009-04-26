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
import java.util.Date;
import java.util.Properties;
import org.esxx.*;
import org.esxx.util.IO;
import org.esxx.util.JS;
import org.esxx.util.XML;
import org.mozilla.javascript.*;

import javax.xml.transform.dom.*;
import net.sf.saxon.s9api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static net.sf.saxon.s9api.Serializer.Property.*;


public class WebRequest
  extends Request
  implements ESXX.ResponseHandler {

  public WebRequest(URI app_file, String[] command_line, Properties properties,
		    InputStream in, OutputStream error, OutputStream out)
    throws IOException {
    super(app_file, command_line, properties, in, error);
    outStream = out;
  }

  @Override public URI getWD() {
    URI main = super.getScriptFilename();

    return new File(main).getParentFile().toURI();
  }

  public Integer handleResponse(ESXX esxx, Context cx, Response response)
    throws Exception {
    // Output HTTP headers
    final PrintWriter out = new PrintWriter(IO.createWriter(outStream, "US-ASCII"));

    out.println("Status: " + response.getStatus());
    out.println("Content-Type: " + response.getContentType(true));

    if (response.isBuffered()) {
      out.println("Content-Length: " + response.getContentLength(esxx, cx));
    }

    response.enumerateHeaders(new Response.HeaderEnumerator() {
	public void header(String name, String value) {
	  out.println(name + ": " + value);
	}
      });

    out.println();
    out.flush();

    response.writeResult(esxx, cx, outStream);

    getErrorWriter().flush();
    getDebugWriter().flush();
    outStream.flush();

    return 0;
  }

  public Integer handleError(ESXX esxx, Context cx, Throwable ex) {
    int    code = ex instanceof ESXXException ? ((ESXXException) ex).getStatus() : 500;
    String ct;
    Object res;

    try {
      ex.printStackTrace(new PrintWriter(getErrorWriter()));

      Document doc = esxx.createDocument("error");
      Element root = doc.getDocumentElement();

      XML.addChild(root, "title",    "ESXX Server Error");
      XML.addChild(root, "subtitle", "Unhandled exception: " + ex.getClass().getSimpleName());
      XML.addChild(root, "message", ex.getMessage());

      if (ex instanceof ESXXException ||
	  ex instanceof javax.xml.stream.XMLStreamException ||
	  ex instanceof javax.xml.transform.TransformerException) {
	// Done
      }
      else if (ex instanceof RhinoException) {
	XML.addChild(root, "stacktrace", 
		     ((RhinoException) ex).getScriptStackTrace(new JS.JSFilenameFilter()));
      }
      else {
	StringWriter sw = new StringWriter();
	ex.printStackTrace(new PrintWriter(sw));
	XML.addChild(root, "stacktrace", sw.toString());
      }

      res = new ByteArrayOutputStream();
      ct  = renderHTML(doc, (ByteArrayOutputStream) res);
    }
    catch (Exception ex2) {
      ex2.printStackTrace();
      // Minimal fallback
      ct  = "text/plain";
      res = ex.toString();
    }

    try {
      return handleResponse(esxx, cx, new Response(code, ct, res, null));
    }
    catch (Exception ex2) {
      // Hmm
      return 20;
    }
  }

  protected static Properties createCGIEnvironment(String request_method, String protocol,
						   URI full_request_uri, 
						   InetSocketAddress local, 
						   InetSocketAddress remote,
						   String context_path,
						   URI root_uri, 
						   File absolute_script_file)
    throws java.net.URISyntaxException {
    Properties p = new Properties();
    String query = full_request_uri.getRawQuery();

    if (query == null) {
      query = "";
    }
    
    String raw_path = full_request_uri.normalize().getRawPath();

    if (raw_path.startsWith(context_path)) {
      raw_path = raw_path.substring(context_path.length());
    }
    else {
      throw new IllegalArgumentException("Path part of " + full_request_uri + " must begin with " 
				      + context_path);
    }

    if (raw_path.charAt(0) == '/') {
      throw new IllegalArgumentException("Context path " + context_path + " should end with '/'");
    }

    URI script_filename = absolute_script_file.toURI();
    URI path_translated = root_uri.resolve(raw_path);

    p.setProperty("GATEWAY_INTERFACE", "CGI/1.1");
    p.setProperty("SERVER_SOFTWARE",   "ESXX/1.0");
    p.setProperty("DOCUMENT_ROOT",     root_uri.getPath());

    p.setProperty("REQUEST_METHOD",    request_method);
    p.setProperty("SERVER_NAME",       full_request_uri.getHost());
    p.setProperty("REQUEST_URI",       full_request_uri.getPath());
    p.setProperty("QUERY_STRING",      query);
    p.setProperty("SERVER_PROTOCOL",   protocol);

    p.setProperty("REMOTE_ADDR",       remote.getAddress().toString().replaceFirst("[^/]*/", ""));
    p.setProperty("REMOTE_PORT",       "" + remote.getPort());

    p.setProperty("SERVER_ADDR",       local.getAddress().toString().replaceFirst("[^/]*/", ""));
    p.setProperty("SERVER_PORT",       "" + local.getPort());

    p.setProperty("PATH_TRANSLATED",   path_translated.getPath());
    p.setProperty("PATH_INFO",         "/" + script_filename.relativize(path_translated).getPath());
    p.setProperty("SCRIPT_FILENAME",   script_filename.getPath());
    p.setProperty("SCRIPT_NAME",       context_path + root_uri.relativize(script_filename).getPath());

    return p;
  }

  protected static URI createURL(Properties headers)
    throws IOException {
    ESXX esxx = ESXX.getInstance();
    File file;

    if (esxx.isHandlerMode(headers.getProperty("SERVER_SOFTWARE"))) {
      String pt = headers.getProperty("PATH_TRANSLATED");
      
      if (pt == null) {
	throw new IOException("PATH_TRANSLATED not set; try --no-handler mode instead");
      }

      file = new File(headers.getProperty("PATH_TRANSLATED"));

      while (file != null && !file.exists()) {
	file = file.getParentFile();
      }

      if (file.isDirectory()) {
	throw new IOException("Unable to find a file in path "
			      + headers.getProperty("PATH_TRANSLATED"));
      }
    }
    else {
      file = new File(headers.getProperty("SCRIPT_FILENAME"));
    }

    return file.toURI();
  }

  public static String renderHTML(Document doc, OutputStream dst)
    throws Exception {
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

  private OutputStream outStream;
}
