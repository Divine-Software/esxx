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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Properties;
import javax.servlet.http.*;
import org.esxx.*;
import org.mozilla.javascript.Context;

public class ServletRequest
  extends WebRequest {

  public ServletRequest(HttpServletRequest sreq, 
			HttpServletResponse sres)
    throws IOException {
    super(sreq.getInputStream(), System.err);
    this.sreq = sreq;
    this.sres = sres;
  }


  public void initRequest(URI root_uri, File canonical_script_file)
    throws IOException, URISyntaxException {
    super.initRequest(canonical_script_file.toURI(),
		      null,
		      createCGIEnvironment(sreq, root_uri, canonical_script_file));
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


  public static void setContentLength(HttpServletResponse sres, long length) {
    if(length <= Integer.MAX_VALUE){
      sres.setContentLength((int) length);
    }else{
      sres.addHeader("Content-Length", Long.toString(length));
    }  
  }

  private static Properties createCGIEnvironment(HttpServletRequest sreq,
						 URI root_uri, 
						 File canonical_script_file) 
    throws URISyntaxException {

    URI full_request_uri = new URI(sreq.getScheme(),
				   null,
				   sreq.getServerName(),
				   sreq.getServerPort(),
				   sreq.getRequestURI(),
				   sreq.getQueryString(),
				   null);
			       
    Properties p = createCGIEnvironment(sreq.getMethod(), sreq.getProtocol(), 
					full_request_uri,
					new InetSocketAddress(sreq.getLocalAddr(), 
							      sreq.getLocalPort()),
					new InetSocketAddress(sreq.getRemoteAddr(), 
							      sreq.getRemotePort()),
					sreq.getContextPath() + "/",
					root_uri, canonical_script_file);

    // Add request headers

    for (Enumeration e = sreq.getHeaderNames(); e.hasMoreElements(); ) {
      String h = (String) e.nextElement();
      p.setProperty(ESXX.httpToCGI(h), sreq.getHeader(h));
    }

    return p;
  }

  private HttpServletRequest sreq;
  private HttpServletResponse sres;
}
