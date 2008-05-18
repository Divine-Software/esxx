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

  public JSResponse(int status, String content_type, Object result, Scriptable headers) {
    this();

    this.response = new Response(status, content_type, result, new HashMap<String, String>());
    this.headers  = headers;
  }


  static public Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr) {
    int status;
    String content_type;
    Object result;
    Scriptable headers;

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

    if (args.length == 4) {
      if (args[3] instanceof Scriptable) {
	headers = (Scriptable) args[3];
      }
      else {
	throw Context.reportRuntimeError("Fourth Response() arguments must be an JS Object.");
      }
    }
    else {
      headers = cx.newObject(ctorObj);
    }

    return new JSResponse(status, content_type, result, headers);
  }

  @Override
  public String getClassName() {
    return "Response";
  }

  public int jsGet_status() {
    return response.getStatus();
  }

  public void jsSet_status(int status) {
    response.setStatus(status);
  }

  public String jsGet_contentType() {
    return response.getContentType();
  }

  public void jsSet_contentType(String content_type) {
    response.setContentType(content_type);
  }

  public Object jsGet_data() {
    return response.getResult();
  }

  public void jsSet_data(Object data) {
    response.setResult(data);
  }

  public Scriptable jsGet_headers() {
    return headers;
  }

  public void jsSet_headers(Scriptable headers) {
    this.headers = headers;
  }

  public Response getResponse() {
    Map<String, String> headers = response.headers();

    headers.clear();

    for (Object hdr : this.headers.getIds()) {
      if (hdr instanceof String) {
	String name = (String) hdr;
	headers.put(name, Context.toString(this.headers.get(name, this.headers)));
      }
    }

    return response;
  }
  
  private Response response;
  private Scriptable headers;
}
