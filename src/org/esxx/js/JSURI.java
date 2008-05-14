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
import org.esxx.ESXX;
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
      this();
      this.uri  = uri;
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
      URI uri = null;

      if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
	throw Context.reportRuntimeError("Missing argument");
      }
      else if (args.length < 2 || args[1] == Context.getUndefinedValue()) {
	if (args[0] instanceof JSURI) {
	  uri = ((JSURI) args[0]).uri;
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
	  uri = ((JSURI) args[0]).uri.resolve(Context.toString(args[1]));
	}
	catch (ClassCastException ex) {
	  throw Context.reportRuntimeError("Double argument must be URI and String");
	}
      }

      return createJSURI(uri);
    }

    public static void finishInit(Scriptable scope, 
				  FunctionObject constructor,
				  Scriptable prototype) {
      // Create and make these properties in the prototype visible
      Context cx = Context.getCurrentContext();
      defineProperty(prototype, "params",  cx.newArray(prototype, 0), ScriptableObject.PERMANENT);
      defineProperty(prototype, "auth",    cx.newArray(prototype, 0), ScriptableObject.PERMANENT);
      defineProperty(prototype, "jars",    cx.newArray(prototype, 0), ScriptableObject.PERMANENT);
      defineProperty(prototype, "headers", cx.newArray(prototype, 0), ScriptableObject.PERMANENT);
    }

    static JSURI createJSURI(URI uri) {
      String scheme = uri.getScheme();

      if (scheme.equals("file")) {
	return new FileURI(uri);
      }
      else if (scheme.startsWith("http")) {
	return new HttpURI(uri);
      }
//      else if (scheme.startsWith("imap")) {
//	return new ImapURI(uri);
//      }
      else if (scheme.startsWith("ldap")) {
	return new LdapURI(uri);
      }
      else if (scheme.startsWith("mailto")) {
	return new MailToURI(uri);
      }
      else if (scheme.equals("jdbc")) {
	return new JdbcURI(uri);
      }
      else {
	try {
	  uri.toURL();
	  return new UrlURI(uri);
	}
	catch (java.net.MalformedURLException ex) {
	  return new JSURI(uri);
	}
      }
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

      return js_this.load(cx, thisObj, type, params);
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

      return js_this.save(cx, thisObj, args[0], type, params);
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

      return js_this.append(cx, thisObj, args[0], type, params);
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

      return js_this.remove(cx, thisObj, type, params);
    }

    public static Object jsFunction_query(Context cx, Scriptable thisObj,
					  Object[] args, Function funObj)
      throws Exception {
      JSURI  js_this = checkInstance(thisObj);

      return js_this.query(cx, thisObj, args);
    }


    @Override
    public String toString() {
      return uri.toString();
    }


    protected Object load(Context cx, Scriptable thisObj,
			  String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() +
				       "' does not support load().");
    }

    protected Object save(Context cx, Scriptable thisObj,
			  Object data, String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() +
				       "' does not support save().");
    }

    protected Object append(Context cx, Scriptable thisObj,
			    Object data, String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() +
				       "' does not support append().");
    }

    protected Object remove(Context cx, Scriptable thisObj,
			    String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() +
				       "' does not support delete().");
    }

    protected Object query(Context cx, Scriptable thisObj, Object[] args)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() +
				       "' does not support query().");
    }


    protected Properties getParams(Context cx, URI uri) {
      final Properties props = new Properties();

      enumerateProperty(cx, "params", new PropEnumerator() {
	  public void handleProperty(Scriptable p, int s) {
	    props.setProperty(Context.toString(p.get("name", p)), 
			      Context.toString(p.get("value", p)));
	  }
	}, uri, "");

      return props;
    }

    protected Scriptable getAuth(Context cx, URI uri, String realm) {
      return getBestProperty(cx, "auth", uri, realm);
    }

    protected Scriptable getCookieJar(Context cx, URI uri) {
      return getBestProperty(cx, "jars", uri, "");
    }

    protected void enumerateHeaders(Context cx, PropEnumerator pe, URI uri) {
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

    protected interface PropEnumerator {
      void handleProperty(Scriptable prop, int score);
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
	      pe.handleProperty((Scriptable) param, score);
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


    protected static void addChild(Element element, String name, String value) {
      Document document = element.getOwnerDocument();

      Element e = document.createElement(name);
      e.appendChild(document.createTextNode(value));
      element.appendChild(e);
    }


    protected static JSURI checkInstance(Scriptable obj) {
      if (obj == null || !(obj instanceof JSURI)) {
	throw Context.reportRuntimeError("Called on incompatible object");
      }

      return (JSURI) obj;
    }


    protected static Object evalProperty(Context cx, Scriptable obj, String key) {
      Object rc;

      if (obj instanceof org.mozilla.javascript.xml.XMLObject) {
	rc = ((org.mozilla.javascript.xml.XMLObject) obj).ecmaGet(cx, key);
      }
      else {
	rc = ScriptableObject.getProperty(obj, key);
      }

      return rc;
    }

    protected static Properties getProperties(Scriptable obj) {
      Properties properties = new Properties();

      for (Object id : ScriptableObject.getPropertyIds(obj)) {
	if (id instanceof String) {
	  String key   = (String) id;
	  String value = Context.toString(ScriptableObject.getProperty(obj, key));

	  properties.setProperty(key, value);
	}
      }

      return properties;
    }

    // This method is taken from org.mozilla.javascript.NativeGlobal

    protected static String decodeURI(String str, boolean fullUri)
      throws URISyntaxException {
      char[] buf = null;
      int bufTop = 0;

      for (int k = 0, length = str.length(); k != length;) {
	char C = str.charAt(k);
	if (C != '%') {
	  if (buf != null) {
	    buf[bufTop++] = C;
	  }
	  ++k;
	} else {
	  if (buf == null) {
	    // decode always compress so result can not be bigger then
	    // str.length()
	    buf = new char[length];
	    str.getChars(0, k, buf, 0);
	    bufTop = k;
	  }
	  int start = k;
	  if (k + 3 > length) {
	    throw new URISyntaxException(str, "Illegal URI format");
	  }
	  int B = unHex(str.charAt(k + 1), str.charAt(k + 2));
	  if (B < 0) {
	    throw new URISyntaxException(str, "Illegal URI format");
	  }
	  k += 3;
	  if ((B & 0x80) == 0) {
	    C = (char)B;
	  } else {
	    // Decode UTF-8 sequence into ucs4Char and encode it into
	    // UTF-16
	    int utf8Tail, ucs4Char, minUcs4Char;
	    if ((B & 0xC0) == 0x80) {
	      // First  UTF-8 should be ouside 0x80..0xBF
	      throw new URISyntaxException(str, "Illegal URI format");
	    } else if ((B & 0x20) == 0) {
	      utf8Tail = 1; ucs4Char = B & 0x1F;
	      minUcs4Char = 0x80;
	    } else if ((B & 0x10) == 0) {
	      utf8Tail = 2; ucs4Char = B & 0x0F;
	      minUcs4Char = 0x800;
	    } else if ((B & 0x08) == 0) {
	      utf8Tail = 3; ucs4Char = B & 0x07;
	      minUcs4Char = 0x10000;
	    } else if ((B & 0x04) == 0) {
	      utf8Tail = 4; ucs4Char = B & 0x03;
	      minUcs4Char = 0x200000;
	    } else if ((B & 0x02) == 0) {
	      utf8Tail = 5; ucs4Char = B & 0x01;
	      minUcs4Char = 0x4000000;
	    } else {
	      // First UTF-8 can not be 0xFF or 0xFE
	      throw new URISyntaxException(str, "Illegal URI format");
	    }
	    if (k + 3 * utf8Tail > length) {
	      throw new URISyntaxException(str, "Illegal URI format");
	    }
	    for (int j = 0; j != utf8Tail; j++) {
	      if (str.charAt(k) != '%') {
		throw new URISyntaxException(str, "Illegal URI format");
	      }
	      B = unHex(str.charAt(k + 1), str.charAt(k + 2));
	      if (B < 0 || (B & 0xC0) != 0x80) {
		throw new URISyntaxException(str, "Illegal URI format");
	      }
	      ucs4Char = (ucs4Char << 6) | (B & 0x3F);
	      k += 3;
	    }
	    // Check for overlongs and other should-not-present codes
	    if (ucs4Char < minUcs4Char
		|| ucs4Char == 0xFFFE || ucs4Char == 0xFFFF)
	    {
	      ucs4Char = 0xFFFD;
	    }
	    if (ucs4Char >= 0x10000) {
	      ucs4Char -= 0x10000;
	      if (ucs4Char > 0xFFFFF) {
		throw new URISyntaxException(str, "Illegal URI format");
	      }
	      char H = (char)((ucs4Char >>> 10) + 0xD800);
	      C = (char)((ucs4Char & 0x3FF) + 0xDC00);
	      buf[bufTop++] = H;
	    } else {
	      C = (char)ucs4Char;
	    }
	  }
	  if (fullUri && URI_DECODE_RESERVED.indexOf(C) >= 0) {
	    for (int x = start; x != k; x++) {
	      buf[bufTop++] = str.charAt(x);
	    }
	  } else {
	    buf[bufTop++] = C;
	  }
	}
      }
      return (buf == null) ? str : new String(buf, 0, bufTop);
    }

    private static int unHex(char c) {
      if ('A' <= c && c <= 'F') {
	return c - 'A' + 10;
      } else if ('a' <= c && c <= 'f') {
	return c - 'a' + 10;
      } else if ('0' <= c && c <= '9') {
	return c - '0';
      } else {
	return -1;
      }
    }

    private static int unHex(char c1, char c2) {
      int i1 = unHex(c1);
      int i2 = unHex(c2);
      if (i1 >= 0 && i2 >= 0) {
	return (i1 << 4) | i2;
      }
      return -1;
    }

    private static final String URI_DECODE_RESERVED = ";/?:@&=+$,#";

    protected URI uri;
}
