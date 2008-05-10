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
import java.util.HashMap;
import org.esxx.*;
import org.mozilla.javascript.*;

public class ScriptRequest
  extends Request 
  implements ESXX.ResponseHandler {

  public ScriptRequest(java.net.URL url, String[] cmdline) 
    throws IOException {
    super(url, cmdline, new java.util.Properties(),
	  System.in,
	  new java.io.OutputStreamWriter(System.err));
  }

  public Integer handleResponse(ESXX esxx, Context cx, Response response) 
    throws Exception {
    // Output debug stream to stderr first
    System.err.print(getDebugWriter().toString());

    // Then write result
    response.writeResult(esxx, cx, System.out);

    try {
      int rc = response.getStatus();
	
      if (rc >= 500) {
	return 20;
      }
      else if (rc >= 400) {
	return 10;
      }
      else if (rc >= 300) {
	return 5;
      }
      else if (rc >= 200) {
	return 0;
      }
      else {
	return rc;
      }
    }
    catch (NumberFormatException ex) {
      return 20;
    }
  }

  public Integer handleError(ESXX esxx, Context cx, Throwable t) {
    if (t instanceof ESXXException) {
      ESXXException ex = (ESXXException) t;

      System.err.println(ex.getClass().getSimpleName() + " " + ex.getStatus() 
			 + ": " + ex.getMessage());
      return 10;
    }
    else if (t instanceof RhinoException) {
      RhinoException ex = (RhinoException) t;

      System.err.println(ex.getClass().getSimpleName() + ": " + ex.getMessage());
      System.err.println(ex.getScriptStackTrace(new ESXX.JSFilenameFilter()));
      return 10;
    }
    else {
      t.printStackTrace();
      return 20;
    }
  }
}
