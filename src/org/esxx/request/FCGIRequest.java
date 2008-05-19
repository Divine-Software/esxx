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
import org.mozilla.javascript.*;

public class FCGIRequest
  extends WebRequest {

  public FCGIRequest(JFastRequest jfast)
    throws IOException {
    super(createURL(jfast.properties), null, jfast.properties,
	  new ByteArrayInputStream(jfast.data),
	  System.err,
	  jfast.out);
    jFast = jfast;
  }

  @Override
  public Integer handleResponse(ESXX esxx, Context cx, Response response)
    throws Exception {
    Integer result = super.handleResponse(esxx, cx, response);
    jFast.end();
    return result;
  }

  public static void runServer(int fastcgi_port)
    throws IOException {
    ESXX  esxx  = ESXX.getInstance();
    JFast jfast = new JFast(fastcgi_port);

    while (true) {
      try {
	JFastRequest req = jfast.acceptRequest();

	// Fire and forget
	FCGIRequest fr = new FCGIRequest(req);
	esxx.addRequest(fr, fr, 0);
      }
      catch (JFastException ex) {
	ex.printStackTrace();
      }
      catch (IOException ex) {
	ex.printStackTrace();
      }
    }
  }

  private JFastRequest jFast;
}
