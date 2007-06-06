/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007 Martin Blom <martin@blom.org>
     
     This program is free software; you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation; either version 2
     of the License, or (at your option) any later version.
     
     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.
     
     You should have received a copy of the GNU General Public License
     along with this program; if not, write to the Free Software
     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.blom.martin.esxx.js;

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

    public Object get(String name, Scriptable start) {
      if (name.equals("esxx")) {
	Context cx = Context.getCurrentContext();

	return (JSESXX) cx.getThreadLocal(JSESXX.class);
      }
      else {
	return super.get(name, start);
      }
    }
}
