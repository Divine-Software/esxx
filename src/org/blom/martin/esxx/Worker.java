/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007 Martin Blom <martin@blom.org>

     This program is free software; you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation; either version 2
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program; if not, write to the Free Software
     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.blom.martin.esxx;

import org.blom.martin.esxx.js.*;
import org.blom.martin.esxx.util.*;
import org.blom.martin.esxx.saxon.ESXXExpression;

import java.io.*;
import java.net.URL;
import java.util.Properties;
import java.util.HashMap;
import javax.xml.soap.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Comment;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import javax.xml.namespace.NamespaceContext;

import net.sf.saxon.s9api.*;  
import net.sf.saxon.dom.*;

class Worker {
  public Worker(ESXX esxx) {
    this.esxx = esxx;
  }

  public JSResponse handleRequest(Context cx, Request request)
    throws Exception {
    Application app = esxx.getCachedApplication(request.getURL());
    JSGlobal global;
    Scriptable scope;
    JSESXX js_esxx;

    synchronized (app) {
      // Compile all <?esxx and <?esxx-import PIs, if not already done.
      // compile() returns the application's global scope
      global = app.compile(cx);

      // Make the JSESXX object temporary available as "esxx" in the
      // global scope, so the set-up code has access to it.
      js_esxx = global.createJSESXX(cx, request, app);

      // Execute all <?esxx and <?esxx-import PIs, if not already done
      app.execute(cx, global, js_esxx);

      global.deleteJSESXX();
    }

    // Create a new per-request "global" scope and store the JSESXX object in it
    scope = cx.newObject(global);
    scope.setPrototype(global);
    scope.setParentScope(null);
    scope.put("esxx", scope, js_esxx);

    // The JSURI class' constructor requires access to the current
    // JSESXX object, so define it again in the "thread global" scope
    ScriptableObject.defineClass(scope, JSURI.class);

    Object    result = null;
    Exception error  = null;

    try {
      // Create a Request object
      JSRequest jsreq = (JSRequest) cx.newObject(scope, "Request",
						 new Object[] { request });

      // Execute the SOAP or HTTP handler (if available)
      String object = getSOAPAction(jsreq, app);

      if (object != null) {
	result = handleSOAPAction(object, jsreq, cx, scope);
      }
      else if (app.hasHandlers()) {
	String request_method = request.getProperties().getProperty("REQUEST_METHOD");
	String path_info = request.getProperties().getProperty("PATH_INFO");

	result = handleHTTPMethod(request_method, path_info, jsreq, app, cx, scope);
      }
      else if (js_esxx.jsGet_document() != null) {
	// No handlers; the document is the result

	result = js_esxx.jsGet_document();
      }
      else {
	// No handlers, no document -- call main()
	result = handleMain(request.getCommandLine(), jsreq, app, cx, scope);
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
      // handleError throws (unwrapped) error if no handler is installed
      result = handleError(error, app, cx, scope);
    }

    // No error or error handled: Did we get a valid result?
    if (result == null || result == Context.getUndefinedValue()) {
      throw new ESXXException("No result from '" + request.getURL() + "'");
    }

    JSResponse response;

    if (result instanceof JSResponse) {
      response = (JSResponse) result;
    }
    else {
      response = (JSResponse) cx.newObject(scope, "Response",  new Object[] { result });
    }

    if (response.getResult() instanceof Node) {
      handleTransformation(request, response, js_esxx, app, cx, scope);
    }

    // Return response
    return response;
  }

  private String getSOAPAction(JSRequest req, Application app)
    throws javax.xml.soap.SOAPException {
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


  private Object handleSOAPAction(String object, JSRequest req,
				  Context cx, Scriptable scope)
    throws javax.xml.soap.SOAPException {
    Object result;

    SOAPMessage message = (SOAPMessage) req.jsGet_message();

    if (!object.equals("")) {
      // RPC style SOAP handler

      org.w3c.dom.Node     soap_header = null;
      org.w3c.dom.Document soap_body   = null;

      try {
	soap_header = message.getSOAPHeader();
      }
      catch (SOAPException ex) {
	// The header is optional
      }

      soap_body = message.getSOAPBody().extractContentAsDocument();

      Object args[] = { req,
			esxx.domToE4X(soap_body, cx, scope),
			esxx.domToE4X(soap_header, cx, scope) };

      String method = soap_body.getDocumentElement().getLocalName();

      result = ESXX.callJSMethod(object, method, args, "SOAP handler", cx, scope);
    }
    else {
      // No RPC handler; the SOAP message itself is the result

      result = esxx.domToE4X(message.getSOAPPart(), cx, scope);
    }

    return result;
  }


  private Object handleHTTPMethod(String request_method, String path_info,
				  JSRequest req, Application app,
				  Context cx, Scriptable scope) {
    Object result;
    RequestMatcher.Match match = app.getHandlerFunction(request_method, path_info, cx, scope);

    if (match == null) {
      throw new ESXXException(501, "'" + request_method + "' handler not defined for URI "
			      + "'" + path_info + "'");
    }

    req.setArgs(match.params);

    Object args[] = { req };

    result = ESXX.callJSMethod(match.handler, args, "'" + request_method + "' handler", cx, scope);

    return result;
  }

  private Object handleMain(String[] cmdline, JSRequest req, Application app,
			    Context cx, Scriptable scope) {
    Object[] js_cmdline = new Object[cmdline.length];

    for (int i = 0; i < cmdline.length; ++i) {
      js_cmdline[i] = cmdline[i];
    }

    Scriptable args = cx.newArray(scope, js_cmdline);

    req.setArgs(args);

    return ESXX.callJSMethod("main", new Object[] { args }, "Program entry" , cx, scope);
  }

  private void handleTransformation(Request request, JSResponse response, 
				    JSESXX js_esxx, Application app,
				    Context cx, Scriptable scope)
    throws IOException, SaxonApiException {
    ESXX   esxx         = ESXX.getInstance();
    Node   node         = (Node) response.getResult();
    String content_type = response.getContentType();

    HashMap<String,String> params = new HashMap<String,String>();
    String                 ct     = ESXX.parseMIMEType(content_type, params);
    String                 cs     = params.get("charset");
    
    URL stylesheet = app.getStylesheet(ct);

    if (stylesheet == null) {
      stylesheet = app.getStylesheet("");
    }

    XsltExecutable  xe = esxx.getCachedStylesheet(stylesheet, js_esxx.jsGet_error());
    XsltTransformer tr = xe.load();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    Serializer s = new Serializer();
    s.setOutputStream(os);

    // This is sad, but Saxon can only transform the DOM Document Element node.
    org.w3c.dom.DOMImplementation di = node.getOwnerDocument().getImplementation();
    Document doc = di.createDocument(null, null, null);
    Node adopted = doc.adoptNode(node);
    if (adopted == null) {
      // Ugh ...
      adopted = doc.importNode(node, true);
    }
    doc.appendChild(adopted);

    // Append the debug output while we're at it, and let the
    // stylesheet decide if it should be output or not.
    js_esxx.jsGet_debug().flush();
    String ds = request.getDebugWriter().toString();
    doc.appendChild(doc.createComment("Start ESXX Debug Log\n" + 
				      ds.replaceAll("--", "\u2012\u2012") + 
				      "End ESXX Debug Log"));

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

    response.setContentType(xe.getUnderlyingCompiledStylesheet().getOutputProperties().
			    getProperty("media-type", content_type));
    response.setResult(os);
  }

  private Object handleError(Exception error, Application app,
			     Context cx, Scriptable scope) 
    throws Exception {
    Object result;
    String handler = app.getErrorHandlerFunction();

    if (handler == null) {
      // No installed error handler: throw (unwrapped) exception
      if (error instanceof WrappedException) {
	Throwable t = ((WrappedException) error).getWrappedException();

	if (t instanceof Exception) {
	  error = (Exception) t;
	}
      }

      throw error;
    }

    try {
      Object args[] = { cx.javaToJS(error, scope) };

      result = ESXX.callJSMethod(handler, args, "Error handler", cx, scope);
    }
    catch (Exception ex) {
      throw new ESXXException("Failed to handle error '" + error.toString() +
			      "':\n" +
			      "Error handler '" + handler +
			      "' failed with message '" +
			      ex.getMessage() + "'",
			      ex);
    }

    return result;
  }

  private ESXX esxx;
}
