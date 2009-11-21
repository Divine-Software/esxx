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

package org.esxx.js;

import org.esxx.Application;
import org.mozilla.javascript.*;

public class JSGlobal
  extends ImporterTopLevel {
  private static final long serialVersionUID = -2329421357143281940L;

  public JSGlobal(Context cx)
    throws IllegalAccessException, InstantiationException,
	   java.lang.reflect.InvocationTargetException {
    super(cx, false);

    ScriptableObject.defineClass(this, JSESXX.class);
    ScriptableObject.defineClass(this, JSURI.class);
  }

  @Override public String getClassName() {
    return "ESXX.Global"; // This is the only place where the "ESXX" prefix is ok
  }

  public JSESXX createJSESXX(Context cx, Application app) {
    Object js_esxx = get("esxx", this);

    if (!(js_esxx instanceof JSESXX)) {
      js_esxx = cx.newObject(this, "ESXX", new Object[] { app });

      put("esxx", this, js_esxx);
    }

    return (JSESXX) js_esxx;
  }

  public void disallowNewGlobals() {
    globalsDisallowed = true;
  }

//   public void put(String name, Scriptable start, Object value) {
//     if (globalsDisallowed) {
//     System.err.println("Trying to put " + name);
//       if (!super.has(name, start)) {
// 	throw Context.reportRuntimeError("New global variables may only be created " +
// 					 "during application start-up.");
//       }
//     }
    
//     super.put(name, start, value);
//   }

//   public void put(int idx, Scriptable start, Object value) {
//     if (globalsDisallowed) {
//       if (!super.has(idx, start)) {
// 	throw Context.reportRuntimeError("New global variables may only be created " +
// 					 "during application start-up.");
//       }
//     }
    
//     super.put(idx, start, value);
//   }

  public static JSESXX getJSESXX(Context cx, Scriptable scope) {
    // When an application is initialized, scope will be the JSGlobal
    // object; however, once the requests are handled, scope will
    // instead be the per-request "global" scope where the JSESXX
    // object has been moved to.
    scope = getTopLevelScope(scope);
    Object result = scope.get("esxx", scope);

    if (result instanceof JSESXX) {
      return (JSESXX) result;
    }
    else {
      return null;
    }
  }

  boolean globalsDisallowed;
}
