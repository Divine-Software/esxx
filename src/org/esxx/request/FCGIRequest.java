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
import org.bumblescript.jfast.*;
import org.esxx.*;
import org.esxx.util.IO;
import org.mozilla.javascript.*;

public class FCGIRequest
  extends WebRequest {

  public FCGIRequest(JFastRequest jfast) {
    super(new ByteArrayInputStream(jfast.data), System.err);
    jFast = jfast;
  }

  public void initRequest()
    throws IOException {
    super.initRequest(createURL(jFast.properties), null, jFast.properties);
  }

  @Override public Integer handleResponse(Response response)
    throws Exception {
    try {
      // Output HTTP headers
      final PrintWriter out = new PrintWriter(IO.createWriter(jFast.out, "US-ASCII"));

      out.println("Status: " + response.getStatus());
      out.println("Content-Type: " + response.getContentType(true));

      if (response.isBuffered()) {
	out.println("Content-Length: " + response.getContentLength());
      }

      response.enumerateHeaders(new Response.HeaderEnumerator() {
	  public void header(String name, String value) {
	    out.println(name + ": " + value);
	  }
	});

      out.println();
      out.flush();

      response.writeResult(jFast.out);

      getErrorWriter().flush();
      jFast.out.flush();

      return 0;
    }
    finally {
      jFast.end();
    }
  }

  private static URI createURL(Properties headers)
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

  public static void runServer(int fastcgi_port)
    throws IOException {
    ESXX  esxx  = ESXX.getInstance();
    JFast jfast = new JFast(fastcgi_port);

    esxx.getLogger().logp(java.util.logging.Level.INFO, null, null, 
			  "Listening for FastCGI requests on port " + fastcgi_port);

    while (true) {
      try {
	JFastRequest req = jfast.acceptRequest();
	FCGIRequest fr = new FCGIRequest(req);

	// Fire and forget
	try {
	  fr.initRequest();
	  esxx.addRequest(fr, fr, 0);
	}
	catch (IOException ex) {
	  fr.reportInternalError(500, "ESXX Server Error", "FastCGI Error", ex.getMessage(), ex);
	}
      }
      catch (JFastException ex) {
	ex.printStackTrace();
      }
    }
  }

  private JFastRequest jFast;
}
