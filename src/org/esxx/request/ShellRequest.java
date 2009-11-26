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
import java.sql.*;
import java.awt.event.*;
import jline.*;
import org.esxx.*;
import org.esxx.util.*;
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
    
    reader.addCompletor(new PropertyCompletor(app.getJSGlobal()));
    reader.setAutoprintThreshhold(150);
    reader.setUsePagination(true);

    System.out.println("Welcome to the ESXX Shell!");
    System.out.println("Enter JavaScript statements at the prompt. Tab completion is supported.");
    System.out.println("Use Escape to cancel the current statement and Control-D \\q to quit.");

    reader.addTriggeredAction((char) 27, new ActionListener() {
	public void actionPerformed(ActionEvent e) {
	  // Clear current command and exit from readLine
	  sb.setLength(0);
	  reader.exitReadLine(true);
	}
      });

    int line_counter = 1;
    boolean quit = false;

    while (!quit) {
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
      else if (statement.charAt(0) == '\\') {
	char cmd = statement.length() >= 2 ? statement.charAt(1) : '\0';

	switch (cmd) {
	  case 'h':
	    displayHelp(reader, statement.substring(2));
	    break;
	
	  case 'q':
	    quit = true;
	    break;

	  default:
	    System.out.println("Unknown command");
	    break;
	}

	sb.setLength(0);
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

  private synchronized void displayHelp(ConsoleReader reader, String args) 
    throws IOException {
    ESXX esxx = ESXX.getInstance();

    if (helpDB == null) {
      helpDB = File.createTempFile(getClass().getName(), "zip");
      helpDB.deleteOnExit();

      IO.copyStream(esxx.openCachedURI(URI.create("esxx-rsrc:esxx-help.zip")), 
		    new FileOutputStream(helpDB));

      helpURI = URI.create("jdbc:h2:zip:" + helpDB + "/mdc;DB_CLOSE_DELAY=-1");
    }

    if (helpQuery == null) {
      helpQuery = new QueryCache(1, 60000, 10, 60000);
    }

//     String[] terms = args.split(" ");

//     ArrayList<Integer> docs = new ArrayList<Integer>();

//     helpQuery.executeQuery(helpURI, null,
// 			   "select distinct dw.doc_id "
// 			   + "from words w "
// 			   + "inner join doc_words dw on dw.word_id = w.id "
// 			   + "where w.word = {} "
// 			   + "order by doc_id",
// 			   new QueryHandler() {
// 			     public void handleTransaction() {}

// 			     public Object resolveParam(String param, Object child) {
// 			       if (param == "0"
// 			     }
// 			   });

// 			   );
    

  }

  static private File helpDB;
  static private QueryCache helpQuery;
  static private URI helpURI;
  
  static private class MapQueryHandler 
    implements QueryHandler {
    
    public MapQueryHandler(Map<String, Object> p) {
      this.params = p;
    }

    public List<Map<String, Object>> getResult() {
      return result;
    }

    public void handleTransaction() 
      throws SQLException {
      throw new SQLException("MapQueryHandler does not support transactions");
    }
    
    public Object resolveParam(String param, Object child) {
      Object o = params.get(param);

      if (child != null) {
	if (o instanceof Map) {
	  o = ((Map) o).get(child);
	}
	else {
	  throw new UnsupportedOperationException("Param properties must implement Map");
	}
      }

      return o;
    }

    public void handleResult(int set, int update_count, ResultSet rs) 
      throws SQLException {
      if (set != 0) {
	throw new UnsupportedOperationException("Multiple result sets not supported");
      }

      if (result == null) {
	result = new ArrayList<Map<String, Object>>();
      }
    }

    private Map<String, Object> params;
    private List<Map<String, Object>> result;
  }


  private class PropertyCompletor
    implements Completor {
    public PropertyCompletor(Scriptable scope) {
      this.scope = scope;
    }

    public int complete(String buffer, int cursor, List candidates) {
      int begin = cursor;
      int trail = -1;

      // Cut anything after the cursor
      buffer = buffer.substring(0, cursor);

      while (begin > 0) {
	char c = buffer.charAt(begin - 1);

	if (c == '.') {
	  if (trail == -1) {
	    trail = begin - 1;
	  }
	}
	else if (!Character.isJavaIdentifierPart(c)) {
	  break;
	}

	--begin;
      }

      String prefix  = null;
      String postfix = null;

      if (trail != -1) {
	prefix  = buffer.substring(begin, trail);
	postfix = buffer.substring(trail + 1);
	cursor  = trail + 1;
      }
      else {
	postfix = buffer.substring(begin);
	cursor  = begin;
      }

      //      System.out.println("Looking for '" + postfix + "' in '" + prefix + "'");

      Scriptable base = JS.evaluateObjectExpr(prefix, scope);

      if (base != null) {
	Set<Object> members = getAllMembers(base);

	if (base == scope) {
	  // Add JS keywords in global scope
	  members.addAll(reserved);
	}

	for (Object o : members) {
	  String name = Context.toString(o);
	  if (name.startsWith(postfix)) {
	    candidates.add(name);
	  }
	}

	if (candidates.size() == 1 && postfix.equals(candidates.get(0))) {
	  candidates.clear();

	  Object member = ScriptableObject.getProperty(base, postfix);

	  if (member == Scriptable.NOT_FOUND ||
	      !(member instanceof Scriptable) ||
	      getAllMembers((Scriptable) member).isEmpty()) {
	    candidates.add(postfix + " ");
	  }
	  else if (member instanceof Scriptable) {
	    candidates.add(postfix + ".");
	  }
	}
      }


      return cursor;
    }

    Scriptable scope;
  }

  private Set<Object> getAllMembers(Scriptable scope) {
    Set<Object> members = new HashSet<Object>();

    while (scope != null) {
      if (scope instanceof DebuggableObject) {
	members.addAll(Arrays.asList(((DebuggableObject) scope).getAllIds()));
      }
      else {
	members.addAll(Arrays.asList(scope.getIds()));
      }

      scope = scope.getPrototype();
    }

    return members;
  }

  private static List reserved = Arrays.asList(new String[]{
      // JS reserved
      "break", "case", "catch", "continue", "default", "delete", "do", 
      "else", "finally", "for", "function", "if", "in", "instanceof", 
      "new", "return", "switch", "this", "throw", "try", "typeof", 
      "var", "void", "while", "with",

      // Special
      "false", "true", "null", "undefined",

      // Mozilla
      "const"
    });

  private Handler scriptHandler;
  private String[] commandLine;
}
