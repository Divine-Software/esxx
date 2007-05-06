
package org.blom.martin.esxx.js;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Properties;
import org.blom.martin.esxx.ESXX;
import org.blom.martin.esxx.Workload;
import org.htmlcleaner.HtmlCleaner;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JSURI 
  extends ScriptableObject {
    public JSURI() {
      super();
    }

    public JSURI(ESXX esxx, URI uri) {
      this();
      this.esxx = esxx;
      this.uri  = uri;
    }

    public String getClassName() {
      return "URI";
    }

    static public Object jsConstructor(Context cx, 
				       java.lang.Object[] args, 
				       Function ctorObj, 
				       boolean inNewExpr) 
      throws java.net.URISyntaxException {
      ESXX esxx    = (ESXX) cx.getThreadLocal(ESXX.class);
      URI base_uri = ((Workload) cx.getThreadLocal(Workload.class)).getURL().toURI();
      URI uri      = null;

      if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
	uri = base_uri;
      }
      else if (args.length < 2 || args[1] == Context.getUndefinedValue()) {
	if (args[0] instanceof JSURI) {
	  JSURI old = (JSURI) args[0];

	  uri = old.uri;
	}
	else {
	  uri = base_uri.resolve(Context.toString(args[0]));
	}
      }
      else if (args.length >= 2) {
	try {
	  JSURI old = (JSURI) args[0];
	  uri = old.uri.resolve(Context.toString(args[1]));
	}
	catch (ClassCastException ex) {
	  throw Context.reportRuntimeError("Double argument must be URI and String"); 
	}
      }

      String scheme = uri.getScheme();

      if (scheme.equals("file")) {
	return new FileURI(esxx, uri);
      }
      else if (scheme.startsWith("ldap")) {
	return new LdapURI(esxx, uri);
      }
      else if (scheme.startsWith("mailto")) {
	return new MailToURI(esxx, uri);
      }
      else if (scheme.equals("jdbc")) {
	return new JdbcURI(esxx, uri);
      }
      else {
	return new JSURI(esxx, uri);
      }
    }

    public static Object jsFunction_load(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
      throws Exception {
      JSURI  js_this = checkInstance(thisObj);
      String type    = null;
      HashMap<String,String> params = new HashMap<String,String>();

      if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
	type = parseMIMEType(Context.toString(args[0]), params);
      }

      return js_this.load(cx, thisObj, type, params);
    }


    public static Object jsFunction_query(Context cx, Scriptable thisObj,
					  Object[] args, Function funObj)
      throws Exception {
      JSURI  js_this = checkInstance(thisObj);
      String type    = null;
      HashMap<String,String> params = new HashMap<String,String>();

      if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
	throw Context.reportRuntimeError("Missing query argument"); 
      }

      if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
	type = parseMIMEType(Context.toString(args[1]), params);
      }

      return js_this.query(cx, thisObj, Context.toString(args[0]), type, params);
    }


    public static Object jsFunction_save(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
      throws Exception {
      JSURI  js_this = checkInstance(thisObj);
      String type    = null;
      HashMap<String,String> params = new HashMap<String,String>();

      if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
	type = parseMIMEType(Context.toString(args[1]), params);
      }

      return js_this.save(cx, thisObj, args.length != 0 ? args[0] : null, type, params);
    }


    public String toString() {
      return uri.toString();
    }


    protected Object load(Context cx, Scriptable thisObj, 
			  String type, HashMap<String,String> params)
      throws Exception {
      try{
	String[]    ct = { null };
	InputStream is = esxx.openCachedURL(uri.toURL(), ct);
      
	if (type == null) {
	  if (ct[0] != null) {
	    params.clear();
	    type = parseMIMEType(ct[0], params);
	  }
	  else {
	    type = "text/xml";
	  }
	}

	if (type.equals("text/xml")) {
	  // Load URI as XML
	  JSESXX js_esxx = (JSESXX) cx.getThreadLocal(JSESXX.class);

	  Document result = esxx.parseXML(is, uri.toURL(), null, js_esxx.debug);
	  return esxx.domToE4X(result, cx, this);
	}
	else if (type.equals("text/html")) {
	  String      cs = params.get("charset");
	  HtmlCleaner hc;

	  if (cs != null) {
	    hc = new HtmlCleaner(is, cs);
	  }
	  else {
	    hc = new HtmlCleaner(is);
	  }

	  hc.setHyphenReplacementInComment("\u2012\u2012");
	  hc.setUseCdataForScriptAndStyle(false);
	  hc.clean();
	
	  return esxx.domToE4X(hc.createDOM(), cx, this);
	}
	else if (type.equals("text/plain")) {
	  // Load URI as plain text
	  return loadString(is, params);
	}
	else {
	  throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() + 
					   "' does can't load '" + type + "'."); 
	}
      }catch (Exception ex) {
	ex.printStackTrace();
	throw ex;
      }
    }

    private String loadString(InputStream is, HashMap<String,String> params)
      throws IOException {
      String        cs = params.get("charset");
      StringBuilder sb = new StringBuilder();
      String        s;

      if (cs == null) {
	cs = "UTF-8";
      }

      BufferedReader br = new BufferedReader(new InputStreamReader(is, cs));

      while ((s = br.readLine()) != null) {
	sb.append(s);
      }

      return sb.toString();
    }


    protected Object query(Context cx, Scriptable thisObj,
			   String query, String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() + 
				       "' does not support query()."); 
    }


    protected Object save(Context cx, Scriptable thisObj, 
			  Object data, String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() + 
				       "' does not support save()."); 
    }

    protected static void addChild(Element element, String name, String value) {
      Document document = element.getOwnerDocument();

//      element.setAttribute(name, value);
      Element e = document.createElement(name);
      e.appendChild(document.createTextNode(value));
      element.appendChild(e);
    }


    protected static String parseMIMEType(String ct, HashMap<String,String> params) {
      String[] parts = ct.split(";");
      String   type  = parts[0].trim();

      // Add all attributes
      for (int i = 1; i < parts.length; ++i) {
	String[] attr = parts[i].split("=", 2);

	if (attr.length == 2) {
	  params.put(attr[0].trim(), attr[1].trim());
	}
      }
      
      return type;
    }

    protected static JSURI checkInstance(Scriptable obj) {
      if (obj == null || !(obj instanceof JSURI)) {
	throw Context.reportRuntimeError("Called on incompatible object");
      }

      return (JSURI) obj;
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
	  if (k + 3 > length)
	    throw new URISyntaxException(str, "Illegal URI format");
	  int B = unHex(str.charAt(k + 1), str.charAt(k + 2));
	  if (B < 0) throw new URISyntaxException(str, "Illegal URI format");
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
	    if (k + 3 * utf8Tail > length)
	      throw new URISyntaxException(str, "Illegal URI format");
	    for (int j = 0; j != utf8Tail; j++) {
	      if (str.charAt(k) != '%')
		throw new URISyntaxException(str, "Illegal URI format");
	      B = unHex(str.charAt(k + 1), str.charAt(k + 2));
	      if (B < 0 || (B & 0xC0) != 0x80)
		throw new URISyntaxException(str, "Illegal URI format");
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
	      if (ucs4Char > 0xFFFFF)
		throw new URISyntaxException(str, "Illegal URI format");
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

    protected ESXX esxx;
    protected URI uri;
}
