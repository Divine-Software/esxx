
package org.blom.martin.esxx;

import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import org.blom.martin.esxx.*;
import org.mozilla.javascript.*;

public class JSURL 
  extends ScriptableObject {
    public JSURL() {
    }

    public JSURL(ESXX esxx, URL url) {
      this.esxx    = esxx;
      this.url     = url;
    }

    public String getClassName() {
      return "URL";
    }


    public static Object jsFunction_load(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
      throws MalformedURLException, IOException, UnsupportedEncodingException {
      JSURL          js_this = checkInstance(thisObj);
      ESXX           esxx    = js_this.esxx;
      URL            url     = js_this.url;
      String         charset = "UTF-8";
      StringBuilder  sb      = new StringBuilder();
      String         s;

      if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
	charset = Context.toString(args[0]);
      }

      BufferedReader br = new BufferedReader(new InputStreamReader(
					       esxx.openCachedURL(url),
					       charset));

      while ((s = br.readLine()) != null) {
	sb.append(s);
      }

      return sb.toString();
    }


    public static Object jsFunction_loadXML(Context cx, Scriptable thisObj,
                                            Object[] args, Function funObj)
      throws MalformedURLException, IOException, org.xml.sax.SAXException {
      JSURL js_this = (JSURL) thisObj;
      ESXX  esxx    = js_this.esxx;
      URL   url     = js_this.url;

      return esxx.domToE4X(esxx.parseXML(esxx.openCachedURL(url), url, null), cx, thisObj);
    }


    static public Object jsConstructor(Context cx, 
				       java.lang.Object[] args, 
				       Function ctorObj, 
				       boolean inNewExpr) {
      ESXX esxx = (ESXX) cx.getThreadLocal(ESXX.class);
      URL base_url = (URL) cx.getThreadLocal(URL.class);

      try {
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
	    return new JSURL(esxx, new URL(base_url, url));
	  }
	  else {
	    Context.reportRuntimeError("Single argument must be URL or String"); 
	  }
	}
	else if (args.length == 2) {
	  try {
	    JSURL old = (JSURL) args[0];
	    String url = (String) args[1];

	    return new JSURL(esxx, new URL(old.url, url));
	  }
	  catch (ClassCastException ex) {
	    Context.reportRuntimeError("Duble argument must be URL and String"); 
	  }
	}
      }
      catch (MalformedURLException ex) {
	Context.reportRuntimeError("MalformedURLException: " + 
				   ex.getMessage()); 
      }

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
    private URL url;
}
