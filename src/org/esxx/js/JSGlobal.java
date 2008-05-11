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
import org.esxx.ESXXException;
import org.esxx.Request;
import org.mozilla.javascript.*;

public class JSGlobal
  extends ImporterTopLevel {

  public JSGlobal(Context cx)
    throws IllegalAccessException, InstantiationException,
	   java.lang.reflect.InvocationTargetException {
    super(cx, false);

    ScriptableObject.defineClass(this, JSESXX.class);
    ScriptableObject.defineClass(this, JSRequest.class);
    ScriptableObject.defineClass(this, JSResponse.class);
    ScriptableObject.defineClass(this, JSURI.class);
  }

  public JSESXX createJSESXX(Context cx, Request request, Application app) {
    JSESXX js_esxx = (JSESXX) cx.newObject(this, "ESXX", new Object[] { request, app });

    put("esxx", this, js_esxx);

    return js_esxx;
  }

  public void deleteJSESXX() {
    delete("esxx");
  }

  static JSESXX getJSESXX(Context cx, Scriptable scope) {
    // When an application is initialized, scope will be the JSGlobal
    // object; however, once the requests are handled, scope will
    // instead be the per-request "global" scope where the JSESXX
    // object has been moved to.
    scope = getTopLevelScope(scope);
    Object result = scope.get("esxx", scope);

    if (result == Context.getUndefinedValue()) {
      throw new ESXXException("'esxx' not found in top-level scope");
    }

    return (JSESXX) result;
  }
}
