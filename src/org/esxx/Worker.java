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
import javax.xml.soap.*;
import javax.xml.transform.dom.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import net.sf.saxon.s9api.*;

class Worker {
  public Worker(ESXX esxx) {
    this.esxx = esxx;
  }

  public Response handleRequest(Context cx, Request request)
    throws Exception {
    Application app = esxx.getCachedApplication(request);
    JSGlobal global;
    JSESXX js_esxx;

    synchronized (app) {
      // Compile all <?esxx and <?esxx-import PIs, if not already done.
      // compile() returns the application's global scope
      global = app.compile(cx);

      // Make the JSESXX object available as "esxx" in the global
      // scope, so the set-up code has access to it. This call returns
      // the old esxx variable, if already present.
      js_esxx = global.createJSESXX(cx, request, app);

      // Execute all <?esxx and <?esxx-import PIs, if not already done
      app.execute(cx, global, js_esxx);

      // Prevent handler from adding global variables
      global.disallowNewGlobals();
    }

    Object    result = null;
    Exception error  = null;

    try {
      // Create a Request object
      JSRequest jsreq = (JSRequest) cx.newObject(global, "Request",
						 new Object[] { request });

      // Execute the SOAP or HTTP handler (if available)
      String object = getSOAPAction(jsreq, app);

      if (object != null) {
	result = handleSOAPAction(object, jsreq, cx, global);
      }
      else if (app.hasHandlers()) {
	String request_method = request.getProperties().getProperty("REQUEST_METHOD");
	String path_info = request.getProperties().getProperty("PATH_INFO");

	if (path_info == null || path_info.isEmpty()) {
	  path_info = "/";
	}

	result = handleHTTPMethod(request_method, path_info, jsreq, app, cx, global);
      }
      else if (js_esxx.jsGet_document() != null) {
	// No handlers; the document is the result

	result = js_esxx.jsGet_document();
      }
      else {
	// No handlers, no document -- call main()
	result = handleMain(request.getCommandLine(), jsreq, app, cx, global);
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
      result = handleError(error, app, cx, global);
    }

    // No error or error handled: Did we get a valid result?
    if (result == null || result == Context.getUndefinedValue()) {
      throw new ESXXException("No result from '" + request.getURL() + "'");
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
			ESXX.domToE4X(soap_body, cx, scope),
			ESXX.domToE4X(soap_header, cx, scope) };

      String method = soap_body.getDocumentElement().getLocalName();

      result = ESXX.callJSMethod(object, method, args, "SOAP handler", cx, scope);
    }
    else {
      // No RPC handler; the SOAP message itself is the result

      result = ESXX.domToE4X(message.getSOAPPart(), cx, scope);
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

    return ESXX.callJSMethod("main", js_cmdline, "Program entry" , cx, scope);
    //return ESXX.callJSMethod("main", new Object[] { args }, "Program entry" , cx, scope);
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
      Object args[] = { Context.javaToJS(error, scope) };

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
