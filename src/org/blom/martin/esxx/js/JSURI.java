
package org.blom.martin.esxx;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import org.blom.martin.esxx.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JSURI 
  extends ScriptableObject {
    public JSURI() {
    }

    public JSURI(ESXX esxx, URI uri) {
      this.esxx = esxx;
      this.uri  = uri;
    }

    public String getClassName() {
      return "URI";
    }


    public static Object jsFunction_load(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
      throws IOException, org.xml.sax.SAXException {
      JSURI  js_this = checkInstance(thisObj);
      String type    = "text/xml";
      HashMap<String,String> params = new HashMap<String,String>();

      if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
	type = parseMIMEType(Context.toString(args[0]), params);
      }

      return js_this.load(cx, type, params);
    }

    static public Object jsConstructor(Context cx, 
				       java.lang.Object[] args, 
				       Function ctorObj, 
				       boolean inNewExpr) {
      ESXX esxx = (ESXX) cx.getThreadLocal(ESXX.class);
      URI base_uri = (URI) cx.getThreadLocal(URI.class);

      if (args.length == 0) {
	return new JSURI(esxx, base_uri);
      }
      else if (args.length == 1) {
	if (args[0] instanceof JSURI) {
	  JSURI old = (JSURI) args[0];
	  return new JSURI(esxx, old.uri);
	}
	else {
	  String uri = Context.toString(args[0]);
	  return new JSURI(esxx, base_uri.resolve(uri));
	}
      }
      else if (args.length == 2) {
	try {
	  JSURI old = (JSURI) args[0];
	  String uri = Context.toString(args[1]);

	  return new JSURI(esxx, old.uri.resolve(uri));
	}
	catch (ClassCastException ex) {
	  throw Context.reportRuntimeError("Double argument must be URI and String"); 
	}
      }

      return null;
    }

    public String toString() {
      return uri.toString();
    }

    private Object load(Context cx, String type, HashMap<String,String> params)
      throws IOException, org.xml.sax.SAXException {
      if (type.equals("text/xml")) {
	Document result = null;

	// If this URI is a file: URI and is also a directory, create
	// an XML directory listing.
	if (uri.getScheme().equals("file")) {
	  File dir = new File(uri);
	
	  if (dir.exists() && dir.isDirectory()) {
	    result = createDirectoryListing(dir);
	  }
	}
	
	if (result == null) {
	  // Load URI as XML
	  result = esxx.parseXML(esxx.openCachedURL(uri.toURL()), uri.toURL(), null);
	}

	return esxx.domToE4X(result, cx, this);
      }
      else if (type.equals("text/plain")) {
	// Load URI as plain text
	return loadString(uri, params);
      }
      else {
	throw Context.reportRuntimeError("Unknown content type argument '" + type + "'"); 
      }
    }

    private String loadString(URI uri, HashMap<String,String> params)
      throws IOException {
      String        cs = params.get("charset");
      StringBuilder sb = new StringBuilder();
      String        s;

      if (cs == null) {
	cs = "UTF-8";
      }

      BufferedReader br = new BufferedReader(new InputStreamReader(
					       esxx.openCachedURL(uri.toURL()),
					       cs));

      while ((s = br.readLine()) != null) {
	sb.append(s);
      }

      return sb.toString();
    }

    private Document createDirectoryListing(File dir) {
      File[] list = dir.listFiles();

      Document result = esxx.createDocument("result");
      Element  root   = result.getDocumentElement();

      for (File f : list) {
	Element element = null;

	if (f.isDirectory()) {
	  element = result.createElement("directory");
	}
	else if (f.isFile()) {
	  element = result.createElement("file");
	  element.setAttribute("length", Long.toString(f.length()));
	}
	      
	element.setAttribute("name", f.getName());
	element.setAttribute("path", f.getPath());
	element.setAttribute("uri", f.toURI().toString());
	element.setAttribute("isHidden", f.isHidden() ? "true" : "false");
	element.setAttribute("lastModified", Long.toString(f.lastModified()));
	element.setAttribute("id", Integer.toHexString(f.hashCode()));
	root.appendChild(element);
      }

      return result;
    }

    private static String parseMIMEType(String ct, HashMap<String,String> params) {
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

    private static JSURI checkInstance(Scriptable obj) {
      if (obj == null || !(obj instanceof JSURI)) {
	throw Context.reportRuntimeError("Called on incompatible object");
      }

      return (JSURI) obj;
    }

    private ESXX esxx;
    private URI uri;
}
