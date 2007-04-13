
package org.blom.martin.esxx;

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

    private ESXX esxx;
    private URL url;
}
