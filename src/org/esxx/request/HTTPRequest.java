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

import com.sun.net.httpserver.*;
import java.io.*;
import java.util.Date;
import java.net.InetSocketAddress;
import java.net.URI;
import javax.activation.FileTypeMap;
import javax.activation.MimetypesFileTypeMap;
import org.esxx.*;
import org.esxx.js.JSResponse;

public class HTTPRequest
  extends WebRequest {

  public HTTPRequest(HttpExchange he)
    throws IOException {
    super(null, null, null, 
	  he.getRequestBody(), 
	  new OutputStreamWriter(System.err),
	  null);
    httpExchange = he;
  }

  public Object handleResponse(ESXX esxx, JSResponse response) {
    // Do not call super method
    Object result = null;
      
    httpExchange.close();
    return result;
  }

  private static final String htmlHeader =
    "<?xml version='1.0' encoding='UTF-8'?>" +
    "<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Strict//EN' " +
    "'http://www.w3.org/TR/2002/REC-xhtml1-20020801/DTD/xhtml1-strict.dtd'>" +
    "<html xmlns='http://www.w3.org/1999/xhtml' xml:lang='en'><head>" +
    "<title>ESXX - The friendly ECMAscript/XML Application Server</title>" +
    "<link rel='alternate stylesheet' type='text/css' href='http://esxx.org/css/blackwhite.css' title='Black &amp; white'/>" +
    "<link rel='alternate stylesheet' type='text/css' href='http://esxx.org/css/caribbean.css' title='Caribbean'/>" +
    "<link rel='alternate stylesheet' type='text/css' href='http://esxx.org/css/plain.css' title='Plain'/>" +
    "<link rel='alternate stylesheet' type='text/css' href='http://esxx.org/css/system.css' title='System default'/>" +
    "<link rel='stylesheet' type='text/css' href='http://esxx.org/css/amiga.css' title='Workbench 1.x' />" +
    "<script type='text/javascript' src='http://esxx.org/js/styleswitch.js'></script>" +
    "</head><body>" +
    "<h1>ESXX - The friendly ECMAscript/XML Application Server</h1>";

  private static final String htmlFooter =
    "<br /><br /><br />" +
    "<table class='switcher'>" +
    "<tr>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"Black &amp; white\"); return false;'>Black &amp; white</a></td>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"Caribbean\"); return false;'>Caribbean</a></td>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"Plain\"); return false;'>Plain</a></td>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"System default\"); return false;'>System default</a></td>" +
    "<td><a href='#' onclick='setActiveStyleSheet(\"Workbench 1.x\"); return false;'>Workbench 1.x</a></td>" +
    "<td class='logo'><img src='http://esxx.org/gfx/logo.gif' alt='Leviticus, Divine Software' /></td>" +
    "</tr>" +
    "</table>" +
    "</body></html>";

  private static final java.text.SimpleDateFormat isoFormat = 
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  
  private static final FileTypeMap fileTypeMap = new ESXXFileTypeMap();

  public static void runServer(int http_port, String fs_root) 
    throws IOException, java.net.URISyntaxException {
    final String     root = new File(fs_root).getCanonicalPath();
    final URI    root_uri = new File(root).toURI();

    HttpServer  hs = HttpServer.create(new InetSocketAddress(http_port), 0);
    HttpContext hc = hs.createContext("/", new HttpHandler() {
	public void handle(HttpExchange he) 
	  throws IOException {
	  String ruri = he.getRequestURI().toString();
	  String euri = encodeXMLContent(ruri);

	  try {
	    File file = new File(root_uri.resolve(ruri.substring(1)));
	    URI  uri  = file.toURI();

	    if (!file.getCanonicalPath().startsWith(root)) {
	      // Deny access to files outside the root
	      throw new FileNotFoundException("Not Found");
	    }
	    else {
	      String body = null;

	      if (file.exists()) {
		if (file.isDirectory()) {
		  
		  // Directory URIs must end with '/', or else the
		  // client will fail to resolve our relative URIs in
		  // the file listing.

		  if (!ruri.endsWith("/")) {
		    throw new FileNotFoundException("Directory URIs must end with '/'");
		  }

		  StringBuilder sb = new StringBuilder();
		  
		  sb.append(htmlHeader +
			    "<table summary='Directory Listing of " + 
			    encodeXMLAttribute(ruri) + "'>" +
			    "<caption>Directory Listing of " + euri + "</caption>" +
			    "<thead><tr>" +
			    "<td>Name</td>" + 
			    "<td>Last Modified</td>" + 
			    "<td>Size</td>" + 
			    "<td>Type</td>" + 
			    "</tr></thead>" +
			    "<tbody>");

		  if (!ruri.equals("/")) {
		    sb.append("<tr>" + 
			      "<td><a href='..'>Parent Directory</a></td>" +
			      "<td>&#160;</td>" +
			      "<td>&#160;</td>" +
			      "<td>&#160;</td>" +
			      "</tr>");
		  }
		  
		  File[] files = file.listFiles();
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

		  sb.append("</tbody></table>" + htmlFooter);

		  respond(he, 200, "text/html", sb.toString());
		}
		else {
		  if (!file.getName().endsWith(".esxx")) {
		    he.getResponseHeaders().set("Content-Type", fileTypeMap.getContentType(file));
		    he.sendResponseHeaders(200, file.length());
		    ESXX.copyStream(new FileInputStream(file), he.getResponseBody());
		  }
		}
	      }
	      else {
		// Assume code
	      }
	      // Fire and forget

	      //HTTPRequest hr = new HTTPRequest(he);
	      //esxx.addRequest(hr, hr, 0);
	    }
	  }
	  catch (Exception ex) {
	    int code = 500;

	    if (ex instanceof FileNotFoundException) {
	      code = 404;
	    }
	    else {
	      ex.printStackTrace();
	    }

	    respond(he, code, "text/html", 
		    htmlHeader + "<h2>Request Failed</h2>" +
		    "<p>The requested resource " + euri + " failed: " + 
		    encodeXMLContent(ex.getMessage()) +
		    "</p>" + htmlFooter);
	  }
	  finally {
	    he.close();
	  }
	}
      });

    hs.start();
	
    while (true) {
      try {
	Thread.sleep(1000);
      }
      catch (InterruptedException ex) {
	Thread.currentThread().interrupt();
	break;
      }
    }
  }

  private static void respond(HttpExchange he, int status, String ct, String body)
    throws IOException {
    if (ct.startsWith("text/") && 
	!ct.contains("charset")) {
      ct = ct + "; charset=UTF-8";
    }

    he.getResponseHeaders().set("Content-Type", ct);
    he.sendResponseHeaders(status, 0);
    PrintStream ps = new PrintStream(he.getResponseBody(), false, "UTF-8");
    ps.print(body);
    ps.close();
  }

  private static class ESXXFileTypeMap
    extends MimetypesFileTypeMap {
    public ESXXFileTypeMap() {
      super();

      addIfMissing("css",   "text/css");
      addIfMissing("esxx",  "application/x-esxx");
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

  private HttpExchange httpExchange;
}
