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

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.Properties;
import javax.servlet.http.*;
import org.esxx.*;

public class ServletRequest
  extends WebRequest {

  public ServletRequest(HttpServletRequest sreq, 
			HttpServletResponse sres)
    throws IOException {
    super(sreq.getInputStream(), System.err);
    this.sreq = sreq;
    this.sres = sres;
  }

  public void initRequest(URI fs_root_uri, URI path_translated) {
    StringBuffer request_url  = sreq.getRequestURL();
    String       query_string = sreq.getQueryString();

    if (query_string != null) {
      request_url.append('?');
      request_url.append(query_string);
    }

    URI full_request_uri = URI.create(request_url.toString());
			       
    Properties p = createCGIEnvironment(sreq.getMethod(), 
					sreq.getProtocol(), 
					full_request_uri,
					path_translated,
					sreq.getLocalAddr(), sreq.getLocalPort(),
					sreq.getRemoteAddr(), sreq.getRemotePort(),
					fs_root_uri);

    // Add request headers
    for (Enumeration e = sreq.getHeaderNames(); e.hasMoreElements(); ) {
      String h = (String) e.nextElement();
      p.setProperty(ESXX.httpToCGI(h), sreq.getHeader(h));
    }

    super.initRequest(sreq.getMethod(), full_request_uri, path_translated,
		      p, fs_root_uri, true);
  }

  public Integer handleResponse(Response response)
    throws Exception {
    try {
      int  status = response.getStatus();

      sres.setStatus(status);
      sres.setContentType(response.getContentType(true));

      response.enumerateHeaders(new Response.HeaderEnumerator() {
  	  public void header(String name, String value) {
  	    sres.addHeader(name, value);
  	  }
  	});

      if ((status >= 100 && status <= 199) ||
  	  status == 204 ||
  	  status == 205 ||
  	  status == 304) {
	// No body
      }
      else {
	if (response.isBuffered()) {
	  // Output Content-Length header, if size is known
	  setContentLength(sres, response.getContentLength());
	}

	// Output body
	response.writeResult(sres.getOutputStream());
      }

      return 0;
    }
    catch (IOException ex) {
      // If we fail to send response, it's probably just because
      // nobody is listening anyway.
      return 20;
    }
    finally {
      sres.flushBuffer();
    }
  }

  private static void setContentLength(HttpServletResponse sres, long length) {
    if(length <= Integer.MAX_VALUE){
      sres.setContentLength((int) length);
    }else{
      sres.addHeader("Content-Length", Long.toString(length));
    }  
  }


  public static void handleServletRequest(HttpServletRequest  sreq,
					  HttpServletResponse sres,
					  URI                 fs_root_uri,
					  String              error_subtitle)
    throws IOException {
    ServletRequest sr = new ServletRequest(sreq, sres);

    try {
      // Prefer getRequestURI(), but use getServletPath() as a fall-back
      String path    = sreq.getRequestURI();
      String context = sreq.getContextPath() != null ? sreq.getContextPath() : "/";

      if (! path.startsWith(context)) {
	path = org.esxx.util.StringUtil.encodeURI(sreq.getServletPath(), true);
	context = "/";
      }

      sr.initRequest(fs_root_uri, getPathTranslated(fs_root_uri, path, context));
      ESXX.Workload wl = ESXX.getInstance().addRequest(sr, sr, 0);
      sres = null;
      wl.future.get(); // Wait for request to complete
    }
    catch (Exception ex) {
      sr.reportInternalError(500, "ESXX Server Error", error_subtitle,  ex.getMessage(), ex);
      sres = null;
    }
    finally {
      if (sres != null) {
	sres.flushBuffer();
      }
    }
  }

  private HttpServletRequest sreq;
  private HttpServletResponse sres;
}
