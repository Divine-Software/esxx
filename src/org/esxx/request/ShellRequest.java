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
import java.net.*;
import java.util.*;
import java.awt.event.*;
import jline.*;
import org.esxx.*;
import org.esxx.util.JS;
import org.mozilla.javascript.*;
import org.mozilla.javascript.debug.DebuggableObject;

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
	  @Override public Object handleRequest(Context cx, Request req, Application app)
	    throws IOException {
	    return runShell(cx, app);
	  }
	};
    }

    return scriptHandler;
  }

  private Object runShell(Context cx, Application app)
    throws IOException {
    final ConsoleReader reader = new ConsoleReader();
    final StringBuilder sb     = new StringBuilder();
    
    System.out.println("Welcome to the ESXX Shell. Enter JavaScript statements at the prompt.");
    System.out.println("Use Escape to cancel current statement and Control-D to exit.");

    reader.addTriggeredAction((char) 27, new ActionListener() {
	public void actionPerformed(ActionEvent e) {
	  // Clear current command and exit from readLine
	  sb.setLength(0);
	  reader.exitReadLine(true);
	}
      });

    int line_counter = 1;

    while (true) {
      String prompt = line_counter == 1 ? "esxx> " : (line_counter + "> ");
      String line   = reader.readLine(prompt);

      if (line == null) {
	System.out.println();
	break;
      }

      sb.append(line);
      sb.append('\n');
      ++line_counter;

      String statement = sb.toString().trim();

      if (statement.length() == 0) {
	line_counter = 1;
      }
      else if (cx.stringIsCompilableUnit(statement)) {
	evaluateString(cx, app, statement);
	sb.setLength(0);
	line_counter = 1;
      }
    }

    return 0;
  }

  private void evaluateString(Context cx, Application app, String statement) {
    Object result;
    Scriptable scope = app.getJSGlobal();

    try {
      result = cx.evaluateString(scope,
				 statement,
				 "ESXX Shell", 1,
				 null);
    }
    catch (Exception ex) {
      result = ex;
    }

    JS.printObject(cx, scope, result);
  }

  private Handler scriptHandler;
  private String[] commandLine;
}
