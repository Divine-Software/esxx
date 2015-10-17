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

import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import javax.mail.internet.ContentType;
import org.esxx.ESXX;
import org.esxx.Application;
import org.esxx.util.StringUtil;
import org.esxx.js.protocol.ProtocolHandler;
import org.mozilla.javascript.*;
import org.mozilla.javascript.regexp.NativeRegExp;

public class JSURI
  extends ScriptableObject {
  public JSURI() {
    super();
  }

  public JSURI(URI uri)
    throws URISyntaxException {
    super();
    setURI(uri);
    protocolHandler = getProtocolHandler();
  }

  public static JSURI newJSURI(Context cx, Application app, URI uri) {
    return (JSURI) cx.newObject(app.getJSGlobal(), "URI", new Object[] { uri });
  }

  @Override public String toString() {
    return jsFunction_toString();
  }

  @Override public String getClassName() {
    return "URI";
  }

  @Override public Object getDefaultValue(Class<?> typeHint) {
    if (uri != null) {
      return uri.toString();
    }
    else {
      return null;
    }
  }

  public URI getURI() {
    return uri;
  }

  public void setURI(URI uri) {
    this.uri = uri;
  }

  static public Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr)
    throws URISyntaxException {
    JSURI prop_src_uri = null;
    URI uri = null;
    String uri_string = null;
    String uri_relative = null;
    Scriptable params = null;

    if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
      throw Context.reportRuntimeError("Missing argument");
    }

    // First argument is always the URI
    if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
      if (args[0] instanceof JSURI) {
	prop_src_uri = (JSURI) args[0];
	uri = prop_src_uri.uri;
      }
      else if (args[0] instanceof URL) {
	uri = ((URL) args[0]).toURI();
      }
      else if (args[0] instanceof URI) {
	uri = (URI) args[0];
      }
      else {
	uri_string = Context.toString(args[0]);
      }
    }

    // Third argument can only by params
    if (args.length >= 3 && args[2] != Context.getUndefinedValue()) {
      params = (Scriptable) args[2];
    }

    // Second argument can be relative URI or params
    if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
      if (args[1] instanceof Scriptable) {
	if (params != null) {
	  throw Context.reportRuntimeError("Expected a String as second argument.");
	}

	params = (Scriptable) args[1];
      }
      else {
	// args[1] should be resolved against args[0]
	uri_relative = Context.toString(args[1]);
      }
    }

    if (params != null) {
      // Replace {...} patterns in string arguments if params was supplied
      final Scriptable final_params = params;

      StringUtil.ParamResolver resolver = new StringUtil.ParamResolver() {
	  public String resolveParam(String param) {
	    Object obj;

	    try {
	      obj = final_params.get(Integer.parseInt(param), final_params);
	    }
	    catch (NumberFormatException ex) {
	      obj = final_params.get(param, final_params);
	    }

	    try {
	      String value = Context.toString(obj);
	      return StringUtil.encodeURI(value, false /* == encodeURIComponent() */);
	    }
	    catch (URISyntaxException ex) {
	      throw new WrappedException(ex);
	    }
	  }
	};

      uri_string = StringUtil.format(uri_string, resolver);
      uri_relative = StringUtil.format(uri_relative, resolver);
    }

    if (uri_string != null) {
	// Resolve URI against current location, if possible
      JSESXX js_esxx = JSGlobal.getJSESXX(cx, ctorObj);

      if (js_esxx != null) {
	prop_src_uri = js_esxx.jsGet_wd();

	if (prop_src_uri != null) {
	  uri = resolveURI(prop_src_uri.uri, uri_string);
	}
      }

      if (uri == null) {
	// Fall back to non-relative
	uri = new URI(uri_string);
      }
    }

    if (uri_relative != null) {
      // Resolve relative part against first URI argument
      uri = resolveURI(uri, uri_relative);
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
    if (uri != null) {
      return uri.toASCIIString();
    }
    else {
      return null;
    }
  }

  public String jsFunction_toString() {
    return (String) getDefaultValue(String.class);
  }

  public String jsFunction_toSource() {
    return "(new URI(\"" + jsFunction_valueOf() + "\"))";
  }

  public static Object jsFunction_load(Context cx, Scriptable thisObj,
				       Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);
    ContentType recv_ct = null;

    if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
      recv_ct = new ContentType(Context.toString(args[0]));
    }

    return js_this.protocolHandler.load(cx, thisObj, recv_ct);
  }

  public static Object jsFunction_save(Context cx, Scriptable thisObj,
				       Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);
    ContentType send_ct = null;
    ContentType recv_ct = null;

    if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
      throw Context.reportRuntimeError("Missing save() argument");
    }

    if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
      send_ct = new ContentType(Context.toString(args[1]));
    }

    if (args.length >= 3 && args[2] != Context.getUndefinedValue()) {
      recv_ct = new ContentType(Context.toString(args[2]));
    }

    return js_this.protocolHandler.save(cx, thisObj, args[0], send_ct, recv_ct);
  }

  public static Object jsFunction_append(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);
    ContentType send_ct = null;
    ContentType recv_ct = null;

    if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
      throw Context.reportRuntimeError("Missing append() argument");
    }

    if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
      send_ct = new ContentType(Context.toString(args[1]));
    }

    if (args.length >= 3 && args[2] != Context.getUndefinedValue()) {
      recv_ct = new ContentType(Context.toString(args[2]));
    }

    return js_this.protocolHandler.append(cx, thisObj, args[0], send_ct, recv_ct);
  }

  public static Object jsFunction_modify(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);
    ContentType send_ct = null;
    ContentType recv_ct = null;

    if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
      throw Context.reportRuntimeError("Missing append() argument");
    }

    if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
      send_ct = new ContentType(Context.toString(args[1]));
    }

    if (args.length >= 3 && args[2] != Context.getUndefinedValue()) {
      recv_ct = new ContentType(Context.toString(args[2]));
    }

    return js_this.protocolHandler.modify(cx, thisObj, args[0], send_ct, recv_ct);
  }

  public static Object jsFunction_remove(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
    throws Exception {
    JSURI  js_this = checkInstance(thisObj);
    ContentType recv_ct = null;

    if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
      recv_ct = new ContentType(Context.toString(args[0]));
    }

    return js_this.protocolHandler.remove(cx, thisObj, recv_ct);
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
      }, uri, null, null);

    return props;
  }

  public URI jsGet_javaURI() {
    return uri;
  }

  public interface PropEnumerator {
    void handleProperty(Scriptable prop, int score);
  }

  public Scriptable getAuth(Context cx, URI req_uri, String realm, String mechanism) {
    String[] unp = getUsernameAndPassword();

    // If the URI already carries authorization information, use it
    if (unp != null) {
      Scriptable res = cx.newObject(this);

      ScriptableObject.putProperty(res, "username", unp[0]);
      ScriptableObject.putProperty(res, "password", unp[1]);

      return res;
    }

    // Else, search the 'auth' property for matching entries
    return getBestProperty(cx, "auth", req_uri, realm, mechanism);
  }

  public String[] getAuthMechanisms(Context cx, URI req_uri, String realm,
				    final String[] default_mechanisms) {
    final LinkedHashSet<String> result = new LinkedHashSet<String>(); // Order is important

    if (getUsernameAndPassword() != null) {
      result.addAll(Arrays.asList(default_mechanisms));
    }

    enumerateProperty(cx, "auth", new PropEnumerator() {
	public void handleProperty(Scriptable p, int s) {
	  Object mechanism = p.get("mechanism", p);

	  if (mechanism == null ||
	      mechanism == Scriptable.NOT_FOUND ||
	      mechanism == Context.getUndefinedValue()) {
	    // Any mechanism is OK
	    result.addAll(Arrays.asList(default_mechanisms));
	  }
	  else {
	    result.add(Context.toString(mechanism).toLowerCase());
	  }
	}
      }, req_uri, realm, null);

    return result.toArray(new String[result.size()]);
  }

  public Scriptable getCookieJar(Context cx, URI req_uri) {
    return getBestProperty(cx, "jars", req_uri, null, null);
  }

  public void enumerateHeaders(Context cx, PropEnumerator pe, URI req_uri) {
    enumerateProperty(cx, "headers", pe, req_uri, null, null);
  }

  private String[] getUsernameAndPassword() {
    if (uri.getRawUserInfo() != null) {
      String[] unp = uri.getRawUserInfo().split(":", 2);

      if (unp.length == 2) {
	try {
	  return new String[] { StringUtil.decodeURI(unp[0], false),
				StringUtil.decodeURI(unp[1], false) };
	}
	catch (URISyntaxException ignored) {}
      }
    }

    return null;
  }

  private Scriptable getBestProperty(Context cx, String name,
				     URI req_uri, String realm, String mechanism) {
    final Scriptable[] res = { null };
    final int[]      score = { -1 };

    enumerateProperty(cx, name, new PropEnumerator() {
	public void handleProperty(Scriptable p, int s) {
	  if (s > score[0]) {
	    res[0] = p;
	    score[0] = s;
	  }
	}
      }, req_uri, realm, mechanism);

    return res[0];
  }

  private void enumerateProperty(Context cx, String name, PropEnumerator pe,
				 URI candidate, String realm, String mechanism) {
    String  uri    = candidate.toString();
    String  scheme = candidate.getScheme();
    String  user   = candidate.getUserInfo();
    String  host   = candidate.getHost();
    Integer port   = candidate.getPort();
    String  path   = candidate.getPath();

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

	  score += filterProperty(cx, param, "realm",     realm)     * 1;
	  score += filterProperty(cx, param, "mechanism", mechanism) * 2;
	  score += filterProperty(cx, param, "scheme",    scheme)    * 4;
	  score += filterProperty(cx, param, "path",      path)      * 8;
	  score += filterProperty(cx, param, "port",      port)      * 16;
	  score += filterProperty(cx, param, "host",      host)      * 32;
	  score += filterProperty(cx, param, "user-info", user)      * 64;
	  score += filterProperty(cx, param, "uri",       uri)       * 128;

	  if (score >= 0) {
	    pe.handleProperty(param, score);
	  }
	}
      }
    }
  }

  private int filterProperty(Context cx, Scriptable param, String key, Object value) {
    Object rule = param.get(key, param);

    if (rule == null || rule == Scriptable.NOT_FOUND || value == null) {
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

  private ProtocolHandler getProtocolHandler()
    throws URISyntaxException {
    String key     = uri.getScheme();
    String handler = "org.esxx.js.protocol." + uri.getScheme().toUpperCase() + "Handler";

    ProtocolHandler res = getProtocolHandler(key, handler);

    if (res == null) {
      try {
	@SuppressWarnings("unused") java.net.URL url = uri.toURL(); // Throws if the is no protocol handler for this URL
	res = getProtocolHandler(key, "org.esxx.js.protocol.URLHandler");
      }
      catch (java.net.MalformedURLException ignored) {}
    }

    if (res == null) {
      res = getProtocolHandler(key, "org.esxx.js.protocol.ProtocolHandler");
    }

    if (res == null) {
      // This should never happen
      throw new IllegalStateException("Unable to create a ProtocolHandler for URI " + uri);
    }

    return res;
  }

  private ProtocolHandler getProtocolHandler(String key, String handler)
    throws URISyntaxException {
    try {
      Constructor<? extends ProtocolHandler> constr = schemeConstructors.get(key);

      if (constr == null) {
	Class<? extends ProtocolHandler> cls;
	cls = Class.forName(handler).asSubclass(ProtocolHandler.class);
	constr = cls.getConstructor(JSURI.class);
	schemeConstructors.put(key, constr);
      }

      return constr.newInstance(this);
    }
    catch (InvocationTargetException ex) {
      schemeConstructors.remove(key);

      if (ex.getCause() instanceof URISyntaxException) {
	throw (URISyntaxException) ex.getCause();
      }

      return null;
    }
    catch (Exception  ex) {
      schemeConstructors.remove(key);
      return null;
    }
  }

  protected static JSURI checkInstance(Scriptable obj) {
    if (obj == null || !(obj instanceof JSURI)) {
      throw Context.reportRuntimeError("Called on incompatible object");
    }

    return (JSURI) obj;
  }

  private static Pattern fragmentPart = Pattern.compile("#.*");
  private static Pattern queryPart = Pattern.compile("\\?.*");

  private static URI resolveURI(URI base, String relative)
    throws URISyntaxException {
    URI rel = new URI(relative);
    URI res;

    if (relative.startsWith("#")) {
      // Make #frag resolve against non-hierachial URIs too
      res = new URI(fragmentPart.matcher(base.toString()).replaceFirst("") + relative);
    }
    else if (relative.startsWith("?")) {
      // Make ?query resolve correctly
      res = new URI(queryPart.matcher(base.toString()).replaceFirst("") + relative );
    }
    else {
      res = base.resolve(rel);
    }

    return res;
  }

  private static ConcurrentHashMap<String, Constructor<? extends ProtocolHandler>> schemeConstructors = new ConcurrentHashMap<String, Constructor<? extends ProtocolHandler>>();

  private ProtocolHandler protocolHandler;
  private URI uri;

  static final long serialVersionUID = -5445754832118781527L;
}
