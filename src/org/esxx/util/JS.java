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

package org.esxx.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Pattern;
import org.esxx.ESXXException;
import org.mozilla.javascript.*;

public abstract class JS {
  public static Object callJSMethod(String expr,
				    Object[] args, String identifier,
				    Context cx, Scriptable scope) {
    String object;
    String method;

    int dot = expr.lastIndexOf('.');

    if (dot == -1) {
      object = null;
      method = expr;
    }
    else {
      object = expr.substring(0, dot);
      method = expr.substring(dot + 1);
    }

    return callJSMethod(object, method, args, identifier, cx, scope);
  }

  private static Pattern dotPattern = Pattern.compile("\\.");
  
  public static Object callJSMethod(String object_expr, String method,
				    Object[] args, String identifier,
				    Context cx, Scriptable scope) {
    Scriptable object = scope;
    String     function_name;

    if (object_expr == null) {
      function_name = method;
    }
    else {
      function_name = object_expr + "." + method;

      String path[] = dotPattern.split(object_expr, 0);

      for (String p : path) {
	Object o = ScriptableObject.getProperty(object, p);

	if (o == Scriptable.NOT_FOUND) {
	  throw new ESXXException(identifier + " component " + p + " not found in " + object_expr);
	}

	if (!(o instanceof Scriptable)) {
	  throw new ESXXException(identifier + " component " + p + " in " + object_expr 
				  + " is not of required type");
	}

	object = (Scriptable) o;
      }
    }

    Object m = ScriptableObject.getProperty(object, method);

    if (m == Scriptable.NOT_FOUND) {
      throw new ESXXException(identifier + " method " + function_name + " not found");
    }

    if (!(m instanceof Function)) {
      throw new ESXXException(identifier + " " + function_name + " is not a function.");
    }

    return ((Function) m).call(cx, scope, object, args);
  }


  public static void dumpScriptState(Scriptable scope) {
    System.err.println("Scope trace:");
    printScopeTrace(scope);
    System.err.println();
    System.err.flush();
  }

  public static void dumpScope(Scriptable scope) {
    System.err.println("Dump of scope " + scope);

    for (Object i : ((ScriptableObject) scope).getAllIds()) {
      if (i instanceof Number) {
	System.err.println(i + ": " + scope.get((Integer) i, scope));
      }
      else {
	System.err.println("'" + i + "': " + scope.get((String) i, scope));
      }
    }
  }


  public static void printScriptStackTrace() {
    StackTraceElement[] trace = Thread.currentThread().getStackTrace();

    JSFilenameFilter filter = new JSFilenameFilter ();

    for (StackTraceElement e : trace) {
      File f = new File(e.getFileName());
      if (filter.accept(f.getParentFile(), f.getName())) {
	System.err.println(e);
      }
    }
  }


  public static void printScopeTrace(Scriptable scope) {
    if (scope == null) {
      return;
    }

    printPrototypeTrace(scope);
    printScopeTrace(scope.getParentScope());
  }


  public static void printPrototypeTrace(Scriptable scope) {
    if (scope == null) {
      System.err.println();
    }
    else {
      System.err.print(toString(scope) + " ");
      printPrototypeTrace(scope.getPrototype());
    }
  }


  public static String toString(Object o) {
    return o.getClass().getSimpleName() + "@" + Integer.toHexString(o.hashCode());
  }

  public static class JSFilenameFilter
    implements FilenameFilter {
    public boolean accept(File dir, String name) {
      boolean is_java = name.matches(".*\\.java");
      return !is_java;
    }
  }
}
