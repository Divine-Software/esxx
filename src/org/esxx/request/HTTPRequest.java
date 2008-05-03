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
import java.net.InetSocketAddress;
import java.net.URI;
import javax.activation.FileTypeMap;
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

  public static void runServer(int http_port, String fs_root) 
    throws IOException, java.net.URISyntaxException {
    final String     root = new File(fs_root).getCanonicalPath();
    final URI    root_uri = new File(root).toURI();
    final FileTypeMap ftm = FileTypeMap.getDefaultFileTypeMap();

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
		  
		  sb.append("<html><body><h1>Directory Listing of " + euri + "</h1>" +
			    "<ul>");

		  if (!ruri.equals("/")) {
		    sb.append("<li><a href='..'>Parent Directory</a></li>");
		  }
		  
		  File[] files = file.listFiles();
		  java.util.Arrays.sort(files);

		  for (File f : files) {
		    if (f.isHidden()) {
		      continue;
		    }

		    String p  = f.isDirectory() ? f.getName() + "/" : f.getName();
		    String fp = uri.relativize(f.toURI()).toASCIIString();

		    sb.append("<li><a href='" + encodeXMLAttribute(fp) + "'>");
		    sb.append(encodeXMLContent(p) + "</a></li>");
		  }

		  sb.append("</ul></body></html>");

		  respond(he, 200, "text/html", sb.toString());
		}
		else {
		  if (!file.getName().endsWith(".esxx")) {
		    he.getResponseHeaders().set("Content-Type", ftm.getContentType(file));
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
		    "<html><body><h1>Request Failed</h1>" +
		    "<p>The requested resource " + euri + " failed: " + 
		    encodeXMLContent(ex.getMessage()) +
		    "</p></body></html>");
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
      ct = ct + "; charset=" + java.nio.charset.Charset.defaultCharset();
    }

    he.getResponseHeaders().set("Content-Type", ct);
    he.sendResponseHeaders(status, 0);
    PrintStream ps = new PrintStream(he.getResponseBody());
    ps.print(body);
    ps.close();
  }

  private HttpExchange httpExchange;
}
