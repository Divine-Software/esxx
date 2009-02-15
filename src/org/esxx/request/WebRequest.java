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
import javax.activation.FileTypeMap;
import javax.activation.MimetypesFileTypeMap;
import org.esxx.*;
import org.esxx.util.IO;
import org.esxx.util.JS;
import org.mozilla.javascript.*;


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
    String title = "ESXX Server Error";
    int    code  = 500;

    if (ex instanceof ESXXException) {
      code = ((ESXXException) ex).getStatus();
    }

    StringWriter sw = new StringWriter();
    PrintWriter out = new PrintWriter(sw);

    out.println(getHTMLHeader(esxx));
    out.println("<h2>" + title + "</h2>");
    out.println("<h3>Unhandled exception: " + ex.getClass().getSimpleName() + "</h3>");
    if (ex instanceof ESXXException ||
	ex instanceof javax.xml.stream.XMLStreamException ||
	ex instanceof javax.xml.transform.TransformerException) {
      out.println("<p><tt>" + encodeXMLContent(ex.getMessage()) + "</tt></p>");
    }
    else if (ex instanceof RhinoException) {
      out.println("<pre>");
      out.println(ex.getClass().getSimpleName() + ": " + encodeXMLContent(ex.getMessage()));
      out.println(((RhinoException) ex).getScriptStackTrace(new JS.JSFilenameFilter()));
      out.println("</pre>");
    }
    else {
      out.println("<pre>");
      ex.printStackTrace(out);
      out.println("</pre>");
    }
    out.println(getHTMLFooter(esxx));
    out.close();

    // Dump exception on error stream too
    ex.printStackTrace(new PrintWriter(getErrorWriter()));

    try {
      return handleResponse(esxx, cx,
			    new Response(code, "text/html; charset=UTF-8",
					 sw.toString(), null));
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

  public static String encodeXMLContent(String str) {
    if (str == null) {
      return "";
    }

    return str.replaceAll("&", "&amp;").replaceAll("<", "&lt;");
  }

  public static String encodeXMLAttribute(String str) {
    return encodeXMLContent(str).replaceAll("'", "&apos;").replaceAll("\"", "&quot;");
  }

  public static String getHTMLHeader(ESXX esxx) {
    return htmlHeader.replaceAll("@RESOURCE_URI@", 
				 esxx.settings().getProperty("esxx.resource-uri", 
							     "http://esxx.org/"));
  }

  public static String getHTMLFooter(ESXX esxx) {
    return htmlFooter.replaceAll("@RESOURCE_URI@", 
				 esxx.settings().getProperty("esxx.resource-uri", 
							     "http://esxx.org/"));
  }

  public static String getFileListing(ESXX esxx, String req_uri, File dir) 
    throws FileNotFoundException {
    // Directory URIs must end with '/', or else the client will fail
    // to resolve our relative URIs in the file listing.
    if (!req_uri.endsWith("/")) {
      throw new FileNotFoundException("Directory URIs must end with '/'");
    }

    URI uri = dir.toURI();
    StringBuilder sb = new StringBuilder();

    sb.append(getHTMLHeader(esxx) +
	      "<table summary='Directory Listing of " + encodeXMLAttribute(req_uri) + "'>" +
	      "<caption>Directory Listing of " + encodeXMLContent(req_uri) + "</caption>" +
	      "<thead><tr>" +
	      "<td>Name</td>" +
	      "<td>Last Modified</td>" +
	      "<td>Size</td>" +
	      "<td>Type</td>" +
	      "</tr></thead>" +
	      "<tbody>");

    if (!req_uri.equals("/")) {
      sb.append("<tr>" +
		"<td><a href='..'>Parent Directory</a></td>" +
		"<td>&#160;</td>" +
		"<td>&#160;</td>" +
		"<td>&#160;</td>" +
		"</tr>");
    }

    File[] files = dir.listFiles();
    java.util.Arrays.sort(files);

    for (File f : files) {
      if (f.isHidden()) {
	continue;
      }

      String p  = f.isDirectory() ? f.getName() + "/" : f.getName();
      String fp = uri.relativize(f.toURI()).toASCIIString();
      String d  = isoFormat.format(new Date(f.lastModified()));
      String l  = f.isDirectory() ? "&#160;" : "" + f.length();
      String t  = f.isDirectory() ? "Directory" : fileTypeMap.getContentType(f);

      sb.append("<tr>");
      sb.append("<td><a href='" + encodeXMLAttribute(fp) + "'>");
      sb.append(encodeXMLContent(p) + "</a></td>");
      sb.append("<td>" + d + "</td>");
      sb.append("<td>" + l + "</td>");
      sb.append("<td>" + t + "</td>");
      sb.append("</tr>");
    }

    sb.append("</tbody></table>" + getHTMLFooter(esxx));
    
    return sb.toString();
  }


  public static final FileTypeMap fileTypeMap = new ESXXFileTypeMap();

  private static final String htmlHeader =
    "<?xml version='1.0' encoding='UTF-8'?>" +
    "<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Strict//EN' " +
    "'http://www.w3.org/TR/2002/REC-xhtml1-20020801/DTD/xhtml1-strict.dtd'>" +
    "<html xmlns='http://www.w3.org/1999/xhtml' xml:lang='en'><head>" +
    "<title>ESXX - The friendly ECMAscript/XML Application Server</title>" +
    "<link href='@RESOURCE_URI@favicon.ico' rel='shortcut icon' type='image/vnd.microsoft.icon'/>" +
    "<link rel='alternale stylesheet' type='text/css' href='@RESOURCE_URI@css/blackwhite.css' title='Black &amp; white'/>" +
    "<link rel='alternate stylesheet' type='text/css' href='@RESOURCE_URI@css/pastel.css' title='Pastel'/>" +
    "<link rel='alternate stylesheet' type='text/css' href='@RESOURCE_URI@css/plain.css' title='Plain'/>" +
    "<link rel='alternate stylesheet' type='text/css' href='@RESOURCE_URI@css/system.css' title='System default'/>" +
    "<link rel='alternate stylesheet' type='text/css' href='@RESOURCE_URI@css/amiga.css' title='Workbench 1.x' class='default'/>" +
    "<script type='text/javascript' src='@RESOURCE_URI@js/styleswitch.js' defer='defer'></script>" +
    "</head><body>" +
    "<h1>ESXX - The friendly ECMAscript/XML Application Server</h1>";

  private static final String htmlFooter =
    "<p><br /><br /><br /></p>" +
    "<table id='switcher'>" +
    "<tr>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"Black &amp; white\"); return false;'>Black &amp; white</a></td>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"Pastel\"); return false;'>Pastel</a></td>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"Plain\"); return false;'>Plain</a></td>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"System default\"); return false;'>System default</a></td>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"Workbench 1.x\"); return false;'>Workbench 1.x</a></td>" +
    "<td class='logo'><img src='@RESOURCE_URI@gfx/logo.gif' alt='Leviticus, Divine Software' /></td>" +
    "</tr>" +
    "</table>" +
    "</body></html>";

  private static final java.text.SimpleDateFormat isoFormat =
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private static class ESXXFileTypeMap
    extends MimetypesFileTypeMap {
    public ESXXFileTypeMap() {
      super();

      addIfMissing("css",   "text/css");
      addIfMissing("esxx",  "application/x-esxx+xml");
      addIfMissing("gif",   "image/gif");
      addIfMissing("html",  "text/html");
      addIfMissing("jpg",   "image/jpeg");
      addIfMissing("js",    "application/x-javascript");
      addIfMissing("pdf",   "application/pdf");
      addIfMissing("png",   "image/png");
      addIfMissing("txt",   "text/plain");
      addIfMissing("xhtml", "application/xhtml+xml");
      addIfMissing("xml",   "application/xml");
      addIfMissing("xsl",   "text/xsl");
    }

    private void addIfMissing(String ext, String type) {
      if (getContentType("file." + ext).equals("application/octet-stream")) {
	addMimeTypes(type + " " + ext + " " + ext.toUpperCase());
      }
    }
  }

  private OutputStream outStream;
}
