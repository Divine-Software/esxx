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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Properties;
import org.mozilla.javascript.Context;
import org.esxx.Response;
import org.esxx.util.IO;

public class CGIRequest
  extends WebRequest {

  public CGIRequest() {
    this(System.in, System.out, System.err);
  }

  protected CGIRequest(InputStream in, OutputStream out, OutputStream err) {
    super(in, err);
    outStream = out;
  }

  public void initRequest(Properties cgi)
    throws java.io.IOException {
    super.initRequest(createURL(cgi), null, cgi);
  }

  @Override public Integer handleResponse(Response response)
    throws Exception {
    // Output HTTP headers
    final PrintWriter out = new PrintWriter(IO.createWriter(outStream, "US-ASCII"));

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

    response.writeResult(outStream);

    getErrorWriter().flush();
    getDebugWriter().flush();
    outStream.flush();

    return 0;
  }

  private OutputStream outStream;
}
