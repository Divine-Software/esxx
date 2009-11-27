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
import org.esxx.*;
import org.esxx.shell.Shell;
import org.mozilla.javascript.Context;

public class ShellRequest
  extends ScriptRequest {
  public ShellRequest() {
    super();
  }
  
  public void initRequest() 
    throws IOException {
    URI shell_init = URI.create("esxx-rsrc:shell_init.js");
    super.initRequest(shell_init, null);
  }

  public Handler getHandler() {
    if (scriptHandler == null) {
      scriptHandler = new Handler() {
	  @Override public Object handleRequest(Context cx, Request req, Application app) {
	    new Shell(cx, app).run();
	    return 0;
	  }
	};
    }

    return scriptHandler;
  }

  private Handler scriptHandler;
  private String[] commandLine;
}
