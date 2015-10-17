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
import java.net.URISyntaxException;
import java.io.InputStream;
import java.io.IOException;
import org.esxx.ESXX;
import org.esxx.Schema;
import org.mozilla.javascript.*;

public class JSSchema
  extends ScriptableObject {

  public JSSchema() {
    super();
  }

  public JSSchema(URI schema_uri, InputStream is, String mime_type)
    throws IOException {
    super();
    schemaURI = schema_uri;
    mimeType  = mime_type;

    if (schemaURI != null) {
      // Load schema once to make sure it's valid
      ESXX.getInstance().getCachedSchema(schemaURI, is, mimeType);
    }
  }

  public static JSSchema newJSSchema(Context cx, Scriptable scope, 
				     URI schema, InputStream is, String mime_type) {
    return (JSSchema) JSESXX.newObject(cx, scope, "Schema",
				       new Object[] { schema, is, mime_type });
  }

  public static Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr)
    throws IOException {
    URI uri = null;
    InputStream is = null;
    String type = null;

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

    if (args.length >= 3 && args[2] != Context.getUndefinedValue()) {
      type = (String) args[2];
    }

    return new JSSchema(uri, is, type);
  }

  @Override public String getClassName() {
    return "Schema";
  }

  public void jsFunction_validate(Object o)
    throws IOException {
    ESXX esxx = ESXX.getInstance();

    org.w3c.dom.Node node;

    if (o instanceof org.w3c.dom.Node) {
      node = (org.w3c.dom.Node) o;
    }
    else {
      node = ESXX.e4xToDOM((Scriptable) o);
    }

    Schema schema = esxx.getCachedSchema(schemaURI, null, mimeType);
    schema.validate(node);
  }

  private URI schemaURI;
  private String mimeType;

  static final long serialVersionUID = -9064181451882970599L;
}
