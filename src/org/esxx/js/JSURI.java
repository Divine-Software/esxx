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

import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Constructor;
import org.esxx.ESXX;
import org.esxx.ESXXException;
import org.esxx.js.protocol.ProtocolHandler;
import org.mozilla.javascript.*;
import org.mozilla.javascript.regexp.NativeRegExp;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JSURI
  extends ScriptableObject {
  public JSURI() {
    super();
  }

  public JSURI(URI uri) {
    super();
    this.uri = uri;

    protocolHandler = getProtocolHandler();
  }

  @Override
  public String toString() {
    return jsFunction_toString();
  }

  @Override
    public String getClassName() {
    return "URI";
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object getDefaultValue(Class typeHint) {
    return "[object URI: " + uri.toString() + "]";
  }

  static public Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr)
    throws java.net.URISyntaxException {
    JSURI prop_src_uri = null;
    URI uri = null;

    if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
      throw Context.reportRuntimeError("Missing argument");
    }
    else if (args.length < 2 || args[1] == Context.getUndefinedValue()) {
      if (args[0] instanceof JSURI) {
	prop_src_uri = (JSURI) args[0];
	uri = prop_src_uri.uri;
      }
      else if (args[0] instanceof URL) {
	uri = ((URL) args[0]).toURI();
      }
      else {
	JSESXX js_esxx = JSGlobal.getJSESXX(cx, ctorObj);

	if (js_esxx != null) {
	  JSURI location = js_esxx.jsGet_wd();

	  if (location != null) {
	    uri = location.uri.resolve(Context.toString(args[0]));
	  }
	}

	if (uri == null) {
	  uri = new URI(Context.toString(args[0]));
	}
      }
    }
    else if (args.length >= 2) {
      try {
	prop_src_uri = (JSURI) args[0];
	uri = prop_src_uri.uri.resolve(Context.toString(args[1]));
      }
      catch (ClassCastException ex) {
	throw Context.reportRuntimeError("Double argument must be URI and String");
      }
    }

    JSURI rc = new JSURI(uri);

    if (prop_src_uri != null) {
      // Copy local properties from previous JSURI object
      for (Object o : prop_src_uri.getIds()) {
	if (o instanceof String) {
	  String key = (String) o;

	  rc.put(key, rc, prop_src_uri.get(key, prop_src_uri));
	}
	else {
	  int key = (Integer) o;

	  rc.put(key, rc, prop_src_uri.get(key, prop_src_uri));
	}
      }
    }

    return rc;
    //      return createJSURI(uri);
  }

  public static void finishInit(Scriptable scope, 
				FunctionObject constructor,
				Scriptable prototype) {
    // Create and make these properties in the prototype visible
    Context cx = Context.getCurrentContext();

    Scriptable jars = cx.newArray(prototype, 0);
    jars.put(0, jars, cx.newArray(jars, 0));

    defineProperty(prototype, "params",  cx.newArray(prototype, 0), ScriptableObject.PERMANENT);
    defineProperty(prototype, "auth",    cx.newArray(prototype, 0), ScriptableObject.PERMANENT);
    defineProperty(prototype, "jars",    jars,                      ScriptableObject.PERMANENT);
    defineProperty(prototype, "headers", cx.newArray(prototype, 0), ScriptableObject.PERMANENT);
  }

  public String jsFunction_valueOf() {
    return uri.toString();
  }

  public String jsFunction_toString() {
    return (String) getDefaultValue(String.class);
  }

  public static Object jsFunction_load(Context cx, Scriptable thisObj,
				       Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);
    String type    = null;
    HashMap<String,String> params = new HashMap<String,String>();

    if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
      type = ESXX.parseMIMEType(Context.toString(args[0]), params);
    }

    return js_this.protocolHandler.load(cx, thisObj, type, params);
  }

  public static Object jsFunction_save(Context cx, Scriptable thisObj,
				       Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);
    String type    = null;
    HashMap<String,String> params = new HashMap<String,String>();

    if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
      throw Context.reportRuntimeError("Missing save() argument");
    }

    if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
      type = ESXX.parseMIMEType(Context.toString(args[1]), params);
    }

    return js_this.protocolHandler.save(cx, thisObj, args[0], type, params);
  }

  public static Object jsFunction_append(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);
    String type    = null;
    HashMap<String,String> params = new HashMap<String,String>();

    if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
      throw Context.reportRuntimeError("Missing append() argument");
    }

    if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
      type = ESXX.parseMIMEType(Context.toString(args[1]), params);
    }

    return js_this.protocolHandler.append(cx, thisObj, args[0], type, params);
  }

  public static Object jsFunction_remove(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);
    String type    = null;
    HashMap<String,String> params = new HashMap<String,String>();

    if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
      type = ESXX.parseMIMEType(Context.toString(args[0]), params);
    }

    return js_this.protocolHandler.remove(cx, thisObj, type, params);
  }

  public static Object jsFunction_query(Context cx, Scriptable thisObj,
					Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);

    return js_this.protocolHandler.query(cx, thisObj, args);
  }

  public Properties getParams(Context cx, URI uri) {
    final Properties props = new Properties();

    enumerateProperty(cx, "params", new PropEnumerator() {
	public void handleProperty(Scriptable p, int s) {
	  props.setProperty(Context.toString(p.get("name", p)), 
			    Context.toString(p.get("value", p)));
	}
      }, uri, "");

    return props;
  }

  public interface PropEnumerator {
    void handleProperty(Scriptable prop, int score);
  }

  public Scriptable getAuth(Context cx, URI uri, String realm) {
    return getBestProperty(cx, "auth", uri, realm);
  }

  public Scriptable getCookieJar(Context cx, URI uri) {
    return getBestProperty(cx, "jars", uri, "");
  }

  public void enumerateHeaders(Context cx, PropEnumerator pe, URI uri) {
    enumerateProperty(cx, "headers", pe, uri, "");
  }

  private Scriptable getBestProperty(Context cx, String name, URI uri, String realm) {
    final Scriptable[] res = { null };
    final int[]      score = { -1 };

    enumerateProperty(cx, name, new PropEnumerator() {
	public void handleProperty(Scriptable p, int s) {
	  if (s > score[0]) {
	    res[0] = p;
	  }
	}
      }, uri, realm);

    return res[0];
  }

  private void enumerateProperty(Context cx, String name, PropEnumerator pe,
				 URI uri, String realm) {
    String  scheme = uri.getScheme();
    String  host   = uri.getHost();
    Integer port   = uri.getPort();
    String  path   = uri.getPath();

    Object p = ScriptableObject.getProperty(this, name);

    if (p instanceof Scriptable) {
      Scriptable params = (Scriptable) p;

      for (Object key : params.getIds()) {
	if (key instanceof Integer) {
	  p = params.get((Integer) key, params);
	}
	else {
	  p = params.get((String) key, params);
	}

	if (p instanceof Scriptable) {
	  Scriptable param = (Scriptable) p;
	      
	  int score = 0;

	  score += filterProperty(cx, param, "scheme", scheme) * 1;
	  score += filterProperty(cx, param, "realm",  realm)  * 2;
	  score += filterProperty(cx, param, "path",   path)   * 4;
	  score += filterProperty(cx, param, "port",   port)   * 8;
	  score += filterProperty(cx, param, "host",   host)   * 16;

	  if (score >= 0) {
	    pe.handleProperty(param, score);
	  }
	}
      }
    }
  }

  private int filterProperty(Context cx, Scriptable param, String key, Object value) {
    Object rule = param.get(key, param);

    if (rule == null || rule == Scriptable.NOT_FOUND) {
      return 0;
    }

    if (rule instanceof Number && value instanceof Number) {
      return ((Number) rule).doubleValue() == ((Number) value).doubleValue() ? 1 : -1000;
    }
    else if (rule instanceof NativeRegExp) {
      return ((NativeRegExp) rule).call(cx, this, (NativeRegExp) rule,
					new Object[] { value }) != null ? 1 : -1000;
    }
    else {
      return Context.toString(rule).equals(value.toString()) ? 1 : -1000;
    }
  }

  private ProtocolHandler getProtocolHandler() {
    String key     = uri.getScheme();
    String handler = "org.esxx.js.protocol." + uri.getScheme().toUpperCase() + "Handler";

    ProtocolHandler res = getProtocolHandler(key, handler);

    if (res == null) {
      try {
	java.net.URL url = uri.toURL(); // Throws if the is no protocol handler for this URL
	res = getProtocolHandler(key, "org.esxx.js.protocol.URLHandler");
      }
      catch (java.net.MalformedURLException ex) {}
    }
    
    if (res == null) {
      res = getProtocolHandler(key, "org.esxx.js.protocol.ProtocolHandler");
    }

    if (res == null) {
      throw new IllegalStateException("Unable to create a ProtocolHandler for URI " + uri);
    }
    
    return res;
  }

  private ProtocolHandler getProtocolHandler(String key, String handler) {
    try {
      Constructor<? extends ProtocolHandler> constr = schemeConstructors.get(key);

      if (constr == null) {
	Class<? extends ProtocolHandler> cls;
	cls = Class.forName(handler).asSubclass(ProtocolHandler.class);
	constr = cls.getConstructor(URI.class, JSURI.class);
	schemeConstructors.put(key, constr);
      }

      return constr.newInstance(uri, this);
    }
    catch (Exception ex) {
      return null;
    }
  }

  protected static JSURI checkInstance(Scriptable obj) {
    if (obj == null || !(obj instanceof JSURI)) {
      throw Context.reportRuntimeError("Called on incompatible object");
    }

    return (JSURI) obj;
  }

  private static ConcurrentHashMap<String, Constructor<? extends ProtocolHandler>> schemeConstructors = new ConcurrentHashMap<String, Constructor<? extends ProtocolHandler>>();

  private ProtocolHandler protocolHandler;
  protected URI uri;

  static final long serialVersionUID = -5445754832118781527L;
}
