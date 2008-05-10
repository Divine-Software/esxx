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

import org.esxx.*;
import org.mozilla.javascript.*;
import java.util.Map;
import java.util.HashMap;

public class JSResponse 
  extends ScriptableObject {
  public JSResponse() {
    super();
  }

  public JSResponse(int status, String content_type, Object result) {
    this();

    responseObject = new Response(status, content_type, result, new HashMap<String, String>());
  }


  static public Object jsConstructor(Context cx, 
				     java.lang.Object[] args, 
				     Function ctorObj, 
				     boolean inNewExpr) {
    int status;
    String content_type;
    Object result;

    if (args.length == 1 && args[0] instanceof NativeArray) {
      // Automatically convert an JS Array into a Response
      args = cx.getElements((NativeArray) args[0]);
    }

    switch (args.length) {
    case 1:
      if (args[0] instanceof Number) {
	status       = ((Number) args[0]).intValue();
	content_type = null;
	result       = "";
      }
      else {
	status       = 200;
	content_type = null;
	result       = args[0];
      }
      break;
	 
    case 2:
      status       = 200;
      content_type = Context.toString(args[0]);
      result       = args[1];
      break;
	  
    case 3:
    case 4:
      status       = (int) Context.toNumber(args[0]);
      content_type = Context.toString(args[1]);
      result       = args[2];
      break;

    default:
      throw Context.reportRuntimeError("Response() constructor requires 1-4 arguments."); 
    }

    JSResponse res = new JSResponse(status, content_type, result);

    // Copy properties from fouth argument
    if (args.length == 4) {
      if (args[3] instanceof NativeObject) {
	Scriptable headers = (Scriptable) args[3];

	for (Object hdr : headers.getIds()) {
	  if (hdr instanceof String) {
	    String name  = (String) hdr;
	    String value = Context.toString(ScriptableObject.getProperty(headers, name));
	      
	    ScriptableObject.putProperty(res, name, value);
	  }
	}
      }
      else {
	throw Context.reportRuntimeError("Fourth Response() arguments must be an JS Object."); 
      }
    }

    return res;
  }

  public String getClassName() {
    return "Response";
  }

  public Response getResponse() {
    Map<String, String> headers = responseObject.headers();

    headers.clear();

    for (Object hdr : getIds()) {
      if (hdr instanceof String) {
	String name = (String) hdr;
	headers.put(name, Context.toString(ScriptableObject.getProperty(this, name)));
      }
    }

    return responseObject;
  }

  private Response responseObject;
}
