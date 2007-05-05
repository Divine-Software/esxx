
package org.blom.martin.esxx.js;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
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
      throws Exception
    {
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

    protected ESXX esxx;
    protected URI uri;
}
