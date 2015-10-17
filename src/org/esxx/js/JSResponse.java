/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2015 Martin Blom <martin@blom.org>

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
  private static final long serialVersionUID = 4124297564183973874L;

  public JSResponse() {
    super();
  }

  public JSResponse(int status, Scriptable headers, Object result, String content_type,
		    Scriptable params) {
    super();

    this.response = new Response(status, content_type, result, new HashMap<String, String>());
    this.headers  = headers;
    this.params   = params;
  }


  static public Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr) {
    int status;
    Scriptable headers;
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

    return new JSResponse(status, headers, result, content_type, cx.newObject(ctorObj));
  }

  public static void finishInit(Scriptable scope, 
				FunctionObject constructor,
				Scriptable prototype) {

    int flags = ScriptableObject.CONST | ScriptableObject.PERMANENT | ScriptableObject.READONLY;

    constructor.defineProperty("CONTINUE",                        100, flags);
    constructor.defineProperty("SWITCHING_PROTOCOLS",             101, flags);
    constructor.defineProperty("PROCESSING",                      102, flags);
    constructor.defineProperty("OK",                              200, flags);
    constructor.defineProperty("CREATED",                         201, flags);
    constructor.defineProperty("ACCEPTED",                        202, flags);
    constructor.defineProperty("NON_AUTHORITATIVE_INFORMATION",   203, flags);
    constructor.defineProperty("NO_CONTENT",                      204, flags);
    constructor.defineProperty("RESET_CONTENT",                   205, flags);
    constructor.defineProperty("PARTIAL_CONTENT",                 206, flags);
    constructor.defineProperty("MULTI_STATUS",                    207, flags);
    constructor.defineProperty("MULTIPLE_CHOICES",                300, flags);
    constructor.defineProperty("MOVED_PERMANENTLY",               301, flags);
    constructor.defineProperty("FOUND",                           302, flags);
    constructor.defineProperty("SEE_OTHER",                       303, flags);
    constructor.defineProperty("NOT_MODIFIED",                    304, flags);
    constructor.defineProperty("USE_PROXY",                       305, flags);
    constructor.defineProperty("SWITCH_PROXY",                    306, flags);
    constructor.defineProperty("TEMPORARY_REDIRECT",              307, flags);
    constructor.defineProperty("BAD_REQUEST",                     400, flags);
    constructor.defineProperty("UNAUTHORIZED",                    401, flags);
    constructor.defineProperty("PAYMENT_REQUIRED",                402, flags);
    constructor.defineProperty("FORBIDDEN",                       403, flags);
    constructor.defineProperty("NOT_FOUND",                       404, flags);
    constructor.defineProperty("METHOD_NOT_ALLOWED",              405, flags);
    constructor.defineProperty("NOT_ACCEPTABLE",                  406, flags);
    constructor.defineProperty("PROXY_AUTHENTICATION_REQUIRED",   407, flags);
    constructor.defineProperty("REQUEST_TIMEOUT",                 408, flags);
    constructor.defineProperty("CONFLICT",                        409, flags);
    constructor.defineProperty("GONE",                            410, flags);
    constructor.defineProperty("LENGTH_REQUIRED",                 411, flags);
    constructor.defineProperty("PRECONDITION_FAILED",             412, flags);
    constructor.defineProperty("REQUEST_ENTITY_TOO_LARGE",        413, flags);
    constructor.defineProperty("REQUEST_URI_TOO_LONG",            414, flags);
    constructor.defineProperty("UNSUPPORTED_MEDIA_TYPE",          415, flags);
    constructor.defineProperty("REQUESTED_RANGE_NOT_SATISFIABLE", 416, flags);
    constructor.defineProperty("EXPECTATION_FAILED",              417, flags);
    constructor.defineProperty("I_AM_A_TEAPOT",                   418, flags);
    constructor.defineProperty("UNPROCESSABLE_ENTITY",            422, flags);
    constructor.defineProperty("LOCKED",                          423, flags);
    constructor.defineProperty("FAILED_DEPENDENCY",               424, flags);
    constructor.defineProperty("UNORDERED_COLLECTION",            425, flags);
    constructor.defineProperty("UPGRADE_REQUIRED",                426, flags);
    constructor.defineProperty("RETRY_WITH",                      449, flags);
    constructor.defineProperty("BLOCKED",                         450, flags);
    constructor.defineProperty("INTERNAL_SERVER_ERROR",           500, flags);
    constructor.defineProperty("NOT_IMPLEMENTED",                 501, flags);
    constructor.defineProperty("BAD_GATEWAY",                     502, flags);
    constructor.defineProperty("SERVICE_UNAVAILABLE",             503, flags);
    constructor.defineProperty("GATEWAY_TIMEOUT",                 504, flags);
    constructor.defineProperty("HTTP_VERSION_NOT_SUPPORTED",      505, flags);
    constructor.defineProperty("VARIANT_ALSO_NEGOTIATES",         506, flags);
    constructor.defineProperty("INSUFFICIENT_STORAGE",            507, flags);
    constructor.defineProperty("BANDWIDTH_LIMIT_EXCEEDED",        509, flags);
    constructor.defineProperty("NOT_EXTENDED",                    510, flags);
  }


  @Override public String toString() {
    return jsFunction_toString();
  }

  @Override public String getClassName() {
    return "Response";
  }

  @Override public Object getDefaultValue(Class<?> typeHint) {
    return "[object Response: " + jsGet_status() + ", "
      + jsGet_contentType() + ": " + jsGet_data() + "]";
  }

  public Object jsFunction_valueOf() {
    return jsGet_data();
  }

  public String jsFunction_toString() {
    return (String) getDefaultValue(String.class);
  }

  public int jsGet_status() {
    return response.getStatus();
  }

  public void jsSet_status(int status) {
    response.setStatus(status);
  }

  public String jsGet_contentType() {
    return response.getContentType(false);
  }

  public void jsSet_contentType(String content_type) {
    response.setContentType(content_type);
  }

  public boolean jsGet_buffered() {
    return response.isBuffered();
  }

  public void jsSet_buffered(boolean buffered) {
    response.setBuffered(buffered);
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

  public Scriptable jsGet_params() {
    return params;
  }

  public void jsSet_params(Scriptable params) {
    this.params = params;
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
  private Scriptable params;
}
