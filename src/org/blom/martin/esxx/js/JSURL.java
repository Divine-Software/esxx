
package org.blom.martin.esxx;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import org.blom.martin.esxx.*;
import org.mozilla.javascript.*;
import org.w3c.dom.*;
import org.w3c.dom.bootstrap.*;

public class JSURL 
  extends ScriptableObject {
    public JSURL() {
    }

    public JSURL(ESXX esxx, URI url) {
      this.esxx    = esxx;
      this.url     = url;
    }

    public String getClassName() {
      return "URL";
    }


    public static Object jsFunction_loadString(Context cx, Scriptable thisObj,
					       Object[] args, Function funObj)
      throws URISyntaxException, IOException, UnsupportedEncodingException {
      JSURL          js_this = checkInstance(thisObj);
      ESXX           esxx    = js_this.esxx;
      URI            url     = js_this.url;
      String         charset = "UTF-8";
      StringBuilder  sb      = new StringBuilder();
      String         s;

      if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
	charset = Context.toString(args[0]);
      }

      BufferedReader br = new BufferedReader(new InputStreamReader(
					       esxx.openCachedURL(url.toURL()),
					       charset));

      while ((s = br.readLine()) != null) {
	sb.append(s);
      }

      return sb.toString();
    }


    public static Object jsFunction_loadXML(Context cx, Scriptable thisObj,
                                            Object[] args, Function funObj)
      throws IOException, org.xml.sax.SAXException {
      JSURL js_this = checkInstance(thisObj);
      ESXX  esxx    = js_this.esxx;
      URI   url     = js_this.url;

      Document result = null;

      // If this URI is a file: URL and is also a directory, create an
      // XML directory listing.
      if (url.getScheme().equals("file")) {
	try {
	  File dir = new File(url);

	  if (dir.exists() && dir.isDirectory()) {
	    File[] list = dir.listFiles();
	    
	    DOMImplementationRegistry reg  = DOMImplementationRegistry.newInstance();
	    DOMImplementation         impl = reg.getDOMImplementation("XML 3.0");

	    result = impl.createDocument(null, "result", null);

	    Element root = result.getDocumentElement();

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
	      element.setAttribute("url", f.toURI().toString());
	      element.setAttribute("isHidden", f.isHidden() ? "true" : "false");
	      element.setAttribute("lastModified", Long.toString(f.lastModified()));
	      element.setAttribute("id", Integer.toHexString(f.hashCode()));

//	      element.appendChild(result.createTextNode(f.getName()));
	      root.appendChild(element);
	    }
	  }
	}
	catch (Exception ex) {
	  // Do nothing
	}
      }

      if (result == null) {
	// Load URL as normal
	result = esxx.parseXML(esxx.openCachedURL(url.toURL()), url.toURL(), null);
      }
      
      return esxx.domToE4X(result, cx, thisObj);
    }


    static public Object jsConstructor(Context cx, 
				       java.lang.Object[] args, 
				       Function ctorObj, 
				       boolean inNewExpr) {
//      try {
	ESXX esxx = (ESXX) cx.getThreadLocal(ESXX.class);
	URI base_url = (URI) cx.getThreadLocal(URI.class);

	if (args.length == 0) {
	  return new JSURL(esxx, base_url);
	}
	else if (args.length == 1) {
	  if (args[0] instanceof JSURL) {
	    JSURL old = (JSURL) args[0];
	    return new JSURL(esxx, old.url);
	  }
	  else if (args[0] instanceof String) {
	    String url = (String) args[0];
	    return new JSURL(esxx, base_url.resolve(url));
	  }
	  else {
	    throw Context.reportRuntimeError("Single argument must be URL or String"); 
	  }
	}
	else if (args.length == 2) {
	  try {
	    JSURL old = (JSURL) args[0];
	    String url = (String) args[1];

	    return new JSURL(esxx, old.url.resolve(url));
	  }
	  catch (ClassCastException ex) {
	    throw Context.reportRuntimeError("Duble argument must be URL and String"); 
	  }
	}
//       }
//       catch (URISyntaxException ex) {
// 	throw Context.reportRuntimeError("URISyntaxException: " + 
// 					 ex.getMessage()); 
//       }

      return null;
    }

    public String toString() {
      return url.toString();
    }


    private static JSURL checkInstance(Scriptable obj) {
      if (obj == null || !(obj instanceof JSURL)) {
	throw Context.reportRuntimeError("Called on incompatible object");
      }

      return (JSURL) obj;
    }

    private ESXX esxx;
    private URI url;
}
