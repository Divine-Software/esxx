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
import java.util.logging.Level;
import javax.servlet.http.*;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.esxx.*;

public class AsyncServletRequest
  extends ServletRequest {

  public AsyncServletRequest(HttpServletRequest sreq,
			     Continuation cont)
    throws IOException {
    super(sreq, null);
    this.cont = cont;
  }

  public Integer handleResponse(Response response)
    throws Exception {
    try {
      super.sres = (HttpServletResponse) cont.getServletResponse();
      return super.handleResponse(response);
    }
    finally {
      super.sres = null;
      cont.complete();
    }
  }

  public static void handleServletRequest(HttpServletRequest  sreq,
					  HttpServletResponse sres,
					  URI                 fs_root_uri,
					  String              error_subtitle)
    throws IOException {
    ESXX esxx = ESXX.getInstance();
    Continuation cont = ContinuationSupport.getContinuation(sreq);

    if (cont.isExpired()) {
      esxx.getLogger().log(Level.WARNING, "Continuation " + cont + " expired!?");
      sres.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT);
      return;
    }

    cont.setTimeout(0);
    cont.suspend(sres);

    ServletRequest sr = new AsyncServletRequest(sreq, cont);

    try {
      // Fire and forget
      sr.initRequest(fs_root_uri, getPathTranslated(fs_root_uri, sreq));
      esxx.addRequest(sr, sr, 0);
    }
    catch (Exception ex) {
      sr.reportInternalError(500, "ESXX Server Error", error_subtitle,  ex.getMessage(), ex);
    }
  }

  private Continuation cont;
}
