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

import org.esxx.js.protocol.GAEConnectionManager;
import org.esxx.request.ServletRequest;
import org.esxx.request.WebRequest;
import org.esxx.util.IO;
import org.esxx.util.XML;


/** An HttpServlet that executes ESXX applications. */
public class ESXXServlet extends HttpServlet {
  public void init()
    throws ServletException {
    try {
      // (Try to) Load embedded H2 database JDBC driver into memory
      try {
	Class.forName("org.h2.Driver");
      }
      catch (ClassNotFoundException ex) {}

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

      fs_root_uri = new File(resolvePath(getInitParameter("http-root"))).getAbsoluteFile().toURI();
      esxx        = ESXX.initInstance(p, this);

      // If running on Google App Engine, install a supported HTTP connection manager
      try {
	org.esxx.js.protocol.HTTPHandler.setConnectionManager(new GAEConnectionManager());
      }
      catch (Exception ex) {
	ex.printStackTrace();
      }
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
      URI path_translated = fs_root_uri.resolve(sreq.getPathInfo());
      sr.initRequest(fs_root_uri, path_translated);
      ESXX.Workload wl = esxx.addRequest(sr, sr, 0);
      sres = null;
      wl.future.get(); // Wait for request to complete
    }
    catch (Exception ex) {
      sr.reportInternalError(500, "ESXX Server Error", "Servlet Error",  ex.getMessage(), ex);
      sres = null;
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

  private URI fs_root_uri;
  private ESXX esxx;
}
