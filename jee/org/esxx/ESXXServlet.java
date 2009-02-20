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

import java.util.Enumeration;
import java.util.Properties;
import java.io.*;

import javax.servlet.http.*;
import javax.servlet.*;

import java.net.URI;
import org.esxx.request.WebRequest;
import org.esxx.request.ServletRequest;
import org.esxx.util.IO;


/** An HttpServlet that executes ESXX applications. */
public class ESXXServlet extends HttpServlet {
  public void init()
    throws ServletException {
    try {
      Properties p = new Properties(System.getProperties());

      // Copy esxx.* servlet init parameters to p
      for (Enumeration e = getInitParameterNames(); e.hasMoreElements(); ) {
	String name  = (String) e.nextElement();
	String value = getInitParameter(name);

	if (name.equals("esxx.app.include_path")) {
	  StringBuilder sb = new StringBuilder();

	  for (String path : value.split("\\|")) {
	    if (sb.length() != 0) {
	      sb.append(File.pathSeparator);
	    }
	  
	    sb.append(resolvePath(path));
	  }

	  p.setProperty(name, sb.toString());
	}
	else if (name.startsWith("esxx.")) {
	  p.setProperty(name, value);
	}
      }

      root     = new File(resolvePath(getInitParameter("http-root"))).getCanonicalPath();
      root_uri = new File(root).toURI();
      esxx     = ESXX.initInstance(p, this);
    }
    catch (Exception ex) {
      throw new ServletException("ESXXServlet.init() failed: " + ex.getMessage(), ex);
    }
  }


  public void destroy() {
    esxx = null;
    ESXX.destroyInstance();
  }


  protected void service(HttpServletRequest sreq, HttpServletResponse sres)
    throws ServletException, IOException {
    try {
      File file = new File(root_uri.resolve(sreq.getServletPath().substring(1))).getCanonicalFile();

      if (!file.getPath().startsWith(root)) {
	// Deny access to files outside the root
	throw new FileNotFoundException("Document is outside root");
      }
      else {
	File app_file = null;

	if (file.exists()) {
	  if (file.isDirectory()) {
	    String listing = WebRequest.getFileListing(esxx, sreq.getRequestURI(), file);
	    sres.setStatus(200);
	    sres.setContentType("text/html; charset=UTF-8");
	    sres.getWriter().print(listing);
	  }
	  else {
	    if (WebRequest.fileTypeMap.getContentType(file).equals("application/x-esxx+xml")) {
	      app_file = file;
	    }
	    else {
	      sres.setStatus(200);
	      sres.setContentType(WebRequest.fileTypeMap.getContentType(file));
	      ServletRequest.setContentLength(sres, file.length());
	      IO.copyStream(new FileInputStream(file), sres.getOutputStream());
	    }
	  }
	}
	else {
	  // Find a file that do exists
	  app_file = file;
	  while (app_file != null && !app_file.exists()) {
	    app_file = app_file.getParentFile();
	  }

	  if (app_file.isDirectory()) {
	    throw new FileNotFoundException("Not Found");
	  }

	  if (!WebRequest.fileTypeMap.getContentType(app_file).equals("application/x-esxx+xml")) {
	    throw new FileNotFoundException("Only ESXX files are directories");
	  }
	}

	if (app_file != null) {
	  ServletRequest sr = new ServletRequest(sreq, sres, root_uri, app_file);
	  ESXX.Workload wl = esxx.addRequest(sr, sr, 0);
	  sres = null;
	  wl.future.get(); // Wait for request to complete
	}
      }
    }
    catch (Exception ex) {
      int code = 500;
      String title = "Internal Server Error";

      if (ex instanceof FileNotFoundException) {
	code = 404;
	title = "Not Found";
      }
      else {
	ex.printStackTrace(sres.getWriter());
      }

      sres.setStatus(code);
      sres.setContentType("text/html; charset=UTF-8");
      sres.getWriter().print(WebRequest.getHTMLHeader(esxx) +
			     "<h2>" + title + "</h2>" +
			     "<p>The requested resource "
			     + WebRequest.encodeXMLContent(sreq.getRequestURI()) + " failed: " +
			     WebRequest.encodeXMLContent(ex.getMessage()) +
			     ".</p>" + WebRequest.getHTMLFooter(esxx));
    }
    finally {
      if (sres != null) {
	sres.flushBuffer();
      }
    }
  }

  private String resolvePath(String path) {
    if (path == null) {
      return getServletContext().getRealPath("");
    }
    else if (new File(path).isAbsolute()) {
      return path;
    }
    else {
      return getServletContext().getRealPath(path);
    }
  }

  private String root;
  private URI root_uri;
  private ESXX esxx;
}
