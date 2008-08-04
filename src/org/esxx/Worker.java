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

package org.esxx;

import org.esxx.js.*;
import org.esxx.util.*;
import org.esxx.saxon.ESXXExpression;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Properties;
import javax.xml.transform.dom.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import net.sf.saxon.s9api.*;
import static net.sf.saxon.s9api.Serializer.Property.*;

import org.mozilla.javascript.tools.debugger.*;

class Worker {
  public Worker(ESXX esxx) {
    this.esxx = esxx;
  }

  public Response handleRequest(Context cx, Request request)
    throws Exception {
    Application app = esxx.getCachedApplication(cx, request);
    JSGlobal global = app.getJSGlobal();
    JSESXX js_esxx  = app.getJSESXX();

    try {
      //     if (app.isDebuggerActivated()) {
      //       org.mozilla.javascript.tools.debugger.Main.mainEmbedded(esxx.getContextFactory(),
      // 							      global,
      // 							      app.getAppName());
      //     }

      Object    result = null;
      Exception error  = null;

      // Create a Request object
      JSRequest jsreq = (JSRequest) cx.newObject(global, "Request",
						 new Object[] { request });

      try {
	// Execute the SOAP or HTTP handler (if available)
	String object = getSOAPAction(jsreq, app);

	if (object != null) {
	  result = app.executeSOAPAction(cx, jsreq, object);
	}
	else if (app.hasHandlers()) {
	  String request_method = request.getProperties().getProperty("REQUEST_METHOD");
	  String path_info = "/" + request.getPathInfo().toString();

	  if (path_info == null || path_info.isEmpty()) {
	    path_info = "/";
	  }

	  result = app.executeHTTPMethod(cx, jsreq, request_method, path_info);
	}
	else if (app.getMainDocument() != null) {
	  // No handlers; the document is the result

	  result = app.getMainDocument();
	}
	else {
	  // No handlers, no document -- call main()
	  result = app.executeMain(cx, jsreq, request.getCommandLine());
	}
      }
      catch (ESXXException.TimeOut ex) {
	// Never handle this exception
	throw ex;
      }
      catch (WrappedException ex) {
	Throwable t = ex.getWrappedException();

	if (t instanceof ESXXException.TimeOut) {
	  throw (ESXXException.TimeOut) t;
	}
	else {
	  error = ex;
	}
      }
      catch (RhinoException ex) {
	error = ex;
      }
      catch (ESXXException ex) {
	error = ex;
      }

      // On errors, invoke error handler

      if (error != null) {
	// executeErrorHandler throws (unwrapped) error if no handler is installed
	result = app.executeErrorHandler(cx, jsreq, error);
      }

      // No error or error handled: Did we get a valid result?
      if (result == null || result == Context.getUndefinedValue()) {
	throw new ESXXException("No result from '" + request.getScriptFilename() + "'");
      }

      JSResponse js_response;

      if (result instanceof JSResponse) {
	js_response = (JSResponse) result;
      }
      else if (result instanceof NativeArray) {
	// Automatically convert an JS Array into a Response
	js_response = (JSResponse) cx.newObject(global, "Response",
						cx.getElements((NativeArray) result));
      }
      else if (result instanceof Number) {
	js_response = (JSResponse) cx.newObject(global, "Response",  
						new Object[] { result, null, null, null });
      }
      else {
	js_response = (JSResponse) cx.newObject(global, "Response",  
						new Object[] { 200, null, result, null });
      }

      Response response = js_response.getResponse();

      response.unwrapResult();

      if (response.getResult() instanceof Node) {
	handleTransformation(request, response, js_esxx, app, cx, global);
      }

      // Return response
      return response;
    }
    finally {
      esxx.releaseApplication(app);
    }
  }

  private String getSOAPAction(JSRequest req, Application app) {
    String action = null;

    String soap_action = req.jsGet_soapAction();

    if (soap_action != null) {
      action = app.getSOAPAction(soap_action);

      if (action == null) {
	// Try default action object
	action = app.getSOAPAction("");
      }
    }

    return action;
  }

  private void handleTransformation(Request request, Response response,
				    JSESXX js_esxx, Application app,
				    Context cx, Scriptable scope)
    throws IOException, SaxonApiException {
    ESXX   esxx         = ESXX.getInstance();
    Node   node         = (Node) response.getResult();
    String content_type = response.getContentType(true);

    HashMap<String,String> params = new HashMap<String,String>();
    String                 ct     = ESXX.parseMIMEType(content_type, params);

    URL stylesheet = app.getStylesheet(ct);

    if (stylesheet == null) {
      stylesheet = app.getStylesheet("");
    }

    XsltExecutable  xe = esxx.getCachedStylesheet(stylesheet, app);
    XsltTransformer tr = xe.load();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    Serializer s = new Serializer();
    s.setOutputStream(os);

    // Remove this code when upgrading to Saxon 9.1 (?)
    Properties op = xe.getUnderlyingCompiledStylesheet().getOutputProperties();
    s.setOutputProperty(BYTE_ORDER_MARK,        op.getProperty("byte-order-mark"));
    s.setOutputProperty(CDATA_SECTION_ELEMENTS, op.getProperty("cdata-section-elements"));
    s.setOutputProperty(DOCTYPE_PUBLIC,         op.getProperty("doctype-public"));
    s.setOutputProperty(DOCTYPE_SYSTEM,         op.getProperty("doctype-system"));
    s.setOutputProperty(ENCODING,               op.getProperty("encoding"));
    s.setOutputProperty(ESCAPE_URI_ATTRIBUTES,  op.getProperty("escape-uri-attributes"));
    s.setOutputProperty(INCLUDE_CONTENT_TYPE,   op.getProperty("include-content-type"));
    s.setOutputProperty(INDENT,                 op.getProperty("indent"));
    s.setOutputProperty(MEDIA_TYPE,             op.getProperty("media-type", content_type));
    s.setOutputProperty(METHOD,                 op.getProperty("method"));
    //    s.setOutputProperty(NORMALIZATION_FORM,     op.getProperty("normalization-form"));
    s.setOutputProperty(OMIT_XML_DECLARATION,   op.getProperty("omit-xml-declaration"));
    s.setOutputProperty(STANDALONE,             op.getProperty("standalone"));
    s.setOutputProperty(UNDECLARE_PREFIXES,     op.getProperty("undeclare-prefixes"));
    s.setOutputProperty(USE_CHARACTER_MAPS,     op.getProperty("use-character-maps"));
    s.setOutputProperty(VERSION,                op.getProperty("version"));

    // This is sad, but Saxon can only transform the DOM Document Element node.
    org.w3c.dom.DOMImplementation di = node.getOwnerDocument().getImplementation();
    Document doc = di.createDocument(null, "dummy", null);
    Node adopted = doc.adoptNode(node);
    if (adopted == null) {
      // Ugh ...
      adopted = doc.importNode(node, true);
    }
    //    doc.appendChild(adopted);
    doc.replaceChild(adopted, doc.getDocumentElement());

    // Append the debug output while we're at it, and let the
    // stylesheet decide if it should be output or not.
    String ds = request.getLogAsString();
    doc.appendChild(doc.createComment("Start ESXX Request Log\n" +
				      ds.replaceAll("--", "\u2012\u2012") +
				      "End ESXX Request Log"));

    tr.setSource(new DOMSource(doc));
    tr.setDestination(s);

    try {
      // Make current scope available to ESXXExpression and begin transformation
      cx.putThreadLocal(ESXXExpression.class, scope);
      tr.transform();
    }
    finally {
      cx.removeThreadLocal(ESXXExpression.class);
    }

    response.setContentType(s.getOutputProperty(MEDIA_TYPE));
    response.setResult(os);
  }

  private ESXX esxx;
}
