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

package org.esxx.js.protocol;

import java.net.URISyntaxException;
import java.util.HashMap;
import org.esxx.js.JSURI;
import org.mozilla.javascript.*;

public class ProtocolHandler {
    public ProtocolHandler(JSURI jsuri)
      throws URISyntaxException {
      this.jsuri = jsuri;
    }

    public Object load(Context cx, Scriptable thisObj,
		       String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + jsuri.getURI().getScheme() +
				       "' does not support load().");
    }

    public Object save(Context cx, Scriptable thisObj,
		       Object data, String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + jsuri.getURI().getScheme() +
				       "' does not support save().");
    }

    public Object append(Context cx, Scriptable thisObj,
			 Object data, String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + jsuri.getURI().getScheme() +
				       "' does not support append().");
    }

    public Object remove(Context cx, Scriptable thisObj,
			 String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + jsuri.getURI().getScheme() +
				       "' does not support delete().");
    }

    public Object query(Context cx, Scriptable thisObj, Object[] args)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + jsuri.getURI().getScheme() +
				       "' does not support query().");
    }

    protected static Object evalProperty(Context cx, Scriptable obj, String key) {
      Object rc;

      if (obj == null || key == null) {
	throw Context.reportRuntimeError("Can't find property " + key + " in " + obj);
      }

      if (obj instanceof org.mozilla.javascript.xml.XMLObject) {
	rc = Context.toString(((org.mozilla.javascript.xml.XMLObject) obj).ecmaGet(cx, key));
      }
      else {
	rc = ScriptableObject.getProperty(obj, key);
      }

      if (rc == Scriptable.NOT_FOUND) {
	throw Context.reportRuntimeError("Can't find property " + key + " in " + obj);
      }

      return rc;
    }

    protected JSURI jsuri;
}
