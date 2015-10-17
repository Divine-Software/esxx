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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import net.sf.saxon.s9api.DOMDestination;
import net.sf.saxon.s9api.QName;
import org.esxx.ESXX;
import org.esxx.Stylesheet;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;

public class JSStylesheet
  extends ScriptableObject {

  public JSStylesheet() {
    super();
  }

  public JSStylesheet(URI stylesheet_uri, InputStream is)
    throws IOException {
    super();
    stylesheetURI = stylesheet_uri;
    params = new HashMap<QName, Object>();

    if (stylesheetURI != null) {
      // Load stylesheet once to make sure it's valid
      ESXX.getInstance().getCachedStylesheet(stylesheetURI, is);
    }
  }

  public static JSStylesheet newJSStylesheet(Context cx, Scriptable scope, 
				     URI stylesheet, InputStream is) {
    return (JSStylesheet) JSESXX.newObject(cx, scope, "Stylesheet",
				       new Object[] { stylesheet, is });
  }

  public static Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr)
    throws IOException {
    URI uri = null;
    InputStream is = null;

    if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
      if (args[0] instanceof JSURI) {
	uri = ((JSURI) args[0]).getURI();
      }
      else if (args[0] instanceof URI) {
	uri = (URI) args[0];
      }
      else {
	try {
	  uri = new URI(Context.toString(args[0]));
	}
	catch (URISyntaxException ex) {
	  throw ScriptRuntime.constructError("URIError", ex.getMessage());
	}
      }
    }
    else {
      throw Context.reportRuntimeError("Missing argument");
    }

    if (!uri.isAbsolute()) {
      throw Context.reportRuntimeError("URI must be absolute argument");
    }

    if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
      is = (InputStream) args[1];
    }

    return new JSStylesheet(uri, is);
  }

  @Override public String getClassName() {
    return "Stylesheet";
  }


  public static Scriptable jsFunction_transformToDocument(Context cx, Scriptable thisObj,
							  Object[] args, Function funObj)
    throws Exception {
    if (thisObj == null || !(thisObj instanceof JSStylesheet)) {
      throw Context.reportRuntimeError("Called on incompatible object");
    }

    ESXX         esxx    = ESXX.getInstance();
    JSStylesheet js_this = (JSStylesheet) thisObj;
    Object       js_arg  = null;

    if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
      js_arg = args[0];
    }

    org.w3c.dom.Node node;

    if (js_arg instanceof org.w3c.dom.Node) {
      node = (org.w3c.dom.Node) js_arg;
    }
    else {
      node = ESXX.e4xToDOM((Scriptable) js_arg);
    }
    
    Stylesheet stylesheet = esxx.getCachedStylesheet(js_this.stylesheetURI, null);
    Document   result     = esxx.createDocument("dummy");
    result.removeChild(result.getDocumentElement());

    Stylesheet.transform(cx, getTopLevelScope(js_this),
			 stylesheet, js_this.params,
			 node, false, null,
			 new DOMDestination(result));

    return ESXX.domToE4X(result, cx, js_this);
  }

  public void jsFunction_setParameter(String uri, String localname, Object value) {
    params.put(new QName(uri != null ? uri : "", localname), value);
  }

  public Object jsFunction_getParameter(String uri, String localname) {
    return params.get(new QName(uri != null ? uri : "", localname));
  }

  public void jsFunction_removeParameter(String uri, String localname) {
    params.remove(new QName(uri != null ? uri : "", localname));
  }

  public void jsFunction_clearParameters() {
    params.clear();
  }

  private URI stylesheetURI;
  private HashMap<QName, Object> params;
}
