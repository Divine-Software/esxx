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

import java.io.*;
import java.net.URI;
import java.util.Enumeration;
import java.util.Properties;
import javax.servlet.*;
import javax.servlet.http.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.esxx.request.ServletRequest;
import org.esxx.request.WebRequest;
import org.esxx.util.IO;
import org.esxx.util.XML;


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
    ServletRequest sr = new ServletRequest(sreq, sres);

    try {
      File app_file = sr.handleWebServerRequest(root_uri.resolve(sreq.getServletPath().substring(1)),
						sreq.getRequestURI(),
						sreq.getQueryString(),
						root);

      if (app_file != null) {
	sr.initRequest(root_uri, app_file);
	ESXX.Workload wl = esxx.addRequest(sr, sr, 0);
	sres = null;
	wl.future.get(); // Wait for request to complete
      }
    }
    catch (Exception ex) {
      int    code;
      String subtitle;
      String message;

      if (ex instanceof FileNotFoundException) {
	code     = 404;
	subtitle = "Not Found";
	message  = "The requested resource '" + sreq.getRequestURI() + "' could not be found: "
	  + ex.getMessage();
	ex = null;
      }
      else {
	code     = 500;
	subtitle = "Internal Server Error";
	message  = "The requested resource '" + sreq.getRequestURI() + "' failed: " 
	  + ex.getMessage();
      }

      sr.reportInternalError(code, "ESXX Server Error", subtitle, message, ex);
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
