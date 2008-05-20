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

  public JSResponse(int status, Scriptable headers, Object result, String content_type) {
    super();

    this.response = new Response(status, content_type, result, new HashMap<String, String>());
    this.headers  = headers;
  }


  static public Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr) {
    int status           = 0;
    Scriptable headers   = null;
    Object result        = null;
    String content_type  = null;

    if (args.length < 1) {
      throw Context.reportRuntimeError("Response() constructor requires 1-4 arguments.");
    }

    status = (int) Context.toNumber(args[0]);

    if (args.length >= 2 && args[1] != null && args[1] != Context.getUndefinedValue()) {
      if (args[1] instanceof Scriptable) {
	headers = (Scriptable) args[1];
      }
      else {
	throw Context.reportRuntimeError("Second Response() arguments must be an JS Object.");
      }
    }
    else {
      headers = cx.newObject(ctorObj);
    }

    if (args.length >= 3 && args[2] != null && args[2] != Context.getUndefinedValue()) {
      result = args[2];
    }

    if (args.length >= 4 && args[3] != null && args[3] != Context.getUndefinedValue()) {
      content_type = Context.toString(args[3]);
    }

    return new JSResponse(status, headers, result, content_type);
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

    if (this.headers != null) {
      for (Object hdr : this.headers.getIds()) {
	if (hdr instanceof String) {
	  String name = (String) hdr;
	  headers.put(name, Context.toString(this.headers.get(name, this.headers)));
	}
      }
    }

    return response;
  }
  
  private Response response;
  private Scriptable headers;
}
