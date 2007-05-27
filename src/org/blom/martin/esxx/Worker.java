
package org.blom.martin.esxx;

import java.io.*;
import java.net.URL;
import java.util.Properties;
import java.util.HashMap;
import javax.xml.soap.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.blom.martin.esxx.js.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Comment;

class Worker 
  implements ContextAction {

    public Worker(ESXX esxx) {
      this.esxx = esxx;
    }

    public Object run(Context cx) {
      // Enable all optimizations
      cx.setOptimizationLevel(9);
//      cx.setOptimizationLevel(-1);

      // Provide a better mapping for primitive types on this context
      cx.getWrapFactory().setJavaPrimitiveWrap(false);

      // Store a reference to the ESXX object
      cx.putThreadLocal(ESXX.class, esxx);

      // Now wait for workloads and execute them
      while (true) {
	try {
	  Workload workload = esxx.getWorkload();
	  String request_method = workload.getProperties().getProperty("REQUEST_METHOD");

	  cx.putThreadLocal(Workload.class, workload);

	  try {
	    ESXXParser parser = esxx.getCachedESXXParser(workload.getURL());

	    // Compile all <?esxx and <?esxx-import PIs, if not already done.
	    // compile() returns the application's global scope
	    Scriptable scope = parser.compile(cx);

	    // Make the JSESXX object available as the instance-level
	    // "esxx" variable (via magic in JSGlobal).
	    JSESXX js_esxx = (JSESXX) cx.newObject(scope, "ESXX", 
						   new Object[] {esxx, workload, parser.getXML()});
	    cx.putThreadLocal(JSESXX.class, js_esxx);

	    // Execute all <?esxx and <?esxx-import PIs, if not already done
	    parser.execute(cx, scope);

	    Object    result = null;
	    Exception error  = null;

	    try {
	      // Create a Request object
	      JSRequest req = (JSRequest) cx.newObject(scope, "Request", 
						       new Object[] { esxx, workload });

	      // Execute the SOAP or HTTP handler (if available)
	      String object = getSOAPAction(req, parser);

	      if (object != null) {
		result = handleSOAPAction(object, req, cx, scope);
	      }
	      else if (parser.hasHandlers()) {
		result = handleHTTPMethod(request_method, req, parser, cx, scope);
	      }
	      else {
		// No handlers; the document is the result

		result = js_esxx.jsGet_document();
	      }
	    }
	    catch (org.mozilla.javascript.RhinoException ex) {
	      error = ex;
	    }
	    catch (ESXXException ex) {
	      error = ex;
	    }

	    // On errors, invoke error handler

	    if (error != null) {
	      if (parser.hasHandlers()) {
		result = handleError(error, parser, cx, scope);
	      }
	      else {
		// No error handler installed: throw away
		throw error;
	      }
	    }

	    // No error or error handled: Did we get a valid result?
	    if (result == null || result == Context.getUndefinedValue()) {
	      throw new ESXXException("No result from '" + workload.getURL() + "'");
	    }

	    JSResponse response;

	    if (result instanceof JSResponse) {
	      response = (JSResponse) result;
	    }
	    else {
	      response = (JSResponse) cx.newObject(scope, "Response",  new Object[] { result });
	    }

	    if (response.getResult() instanceof Node) {
	      Node   node         = (Node) response.getResult();
	      String content_type = response.getContentType();

	      Transformer tr;

	      try {
		URL stylesheet = parser.getStylesheet(response.getContentType());

		if (stylesheet == null) {
		  stylesheet = parser.getStylesheet("");
		}

		tr = esxx.getCachedStylesheet(stylesheet);

		// Set media-type on identity styleseet, if
		// specified. User-specified stylesheets should set
		// these keys directly in the stylesheet.
		if (stylesheet == null && content_type != null) {
		  HashMap<String,String> params = new HashMap<String,String>();
		  String                 ct     = ESXX.parseMIMEType(content_type, params);
		  String                 cs     = params.get("charset");

		  tr.setOutputProperty(OutputKeys.MEDIA_TYPE, ct);

		  if (cs != null) {
		    tr.setOutputProperty(OutputKeys.ENCODING, cs);
		  }
		}
	      }
	      catch (IOException ex) {
		throw new ESXXException("Unable to load stylesheet: " + ex.getMessage(), ex);
	      }

	      ByteArrayOutputStream bos = new ByteArrayOutputStream();
	      tr.transform(new DOMSource(node), new StreamResult(bos));
	      
	      content_type = tr.getOutputProperty(OutputKeys.MEDIA_TYPE) +
		";charset=" + tr.getOutputProperty(OutputKeys.ENCODING);

	      // Attach debug log to document
	      workload.getDebugWriter().close();

	      String ds = workload.getDebugWriter().toString();
	    
	      if (ds.length() != 0) {
		Writer out = workload.createWriter(bos, content_type);
		out.write("<!-- Start ESXX Debug Log\n" + 
			  ds.replaceAll("--", "\u2012\u2012") +
			  "End ESXX Debug Log -->");
		out.close();
	      }

	      bos.close();

	      response.setContentType(content_type);
	      response.setResult(bos);
	    }

	    // Return workload
	    workload.finished(0, response);
	  }
	  catch (Exception ex) {
	    Properties h = new Properties();
	    String title = "ESXX Server Error";

	    h.setProperty("Status", "500 " + title);
	    h.setProperty("Content-Type", "text/html");

	    StringWriter sw = new StringWriter();
	    PrintWriter out = new PrintWriter(sw);

	    out.println("<?xml version='1.0'?>");
	    out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" " +
			"\"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">");
	    out.println("<html><head><title>" + title + "</title></head><body>");
	    out.println("<h1>ESXX Server Error</h1>");
	    out.println("<h2>Unhandled exception</h2>");
	    if (ex instanceof ESXXException ||
		ex instanceof XMLStreamException ||
		ex instanceof TransformerException) {
	      out.println("<p><tt>" + ex.getMessage() + "</tt></p>");
	    }
	    else {
	      out.println("<pre>");
	      ex.printStackTrace(out);
	      out.println("</pre>");
	    }
	    out.println("</body></html>");
	    out.close();

	    try {
	      workload.finished(500, new JSResponse("500 " + title,
						    "text/html",
						    sw.toString()));
	    }
	    catch (ESXXException esx) {
	      // This CANNOT happen
	      throw new InternalError("new JSResponse() threw ESXXException: " +
				      ex.getMessage());
	    }
	  }
	}
	catch (InterruptedException ex) {
	  // Don't know what to do here ... die?
	  ex.printStackTrace();
	  return null;
	}
      }
    }

    private String getSOAPAction(JSRequest req, ESXXParser parser) 
      throws ESXXException, javax.xml.soap.SOAPException {
      String action = null;

      String soap_action = req.jsGet_soapAction();

      if (soap_action != null) {
	action = parser.getSOAPAction(soap_action);

	if (action == null) {
	  // Try default action object
	  action = parser.getSOAPAction("");
	}
      }

      return action;
    }


    private Object handleSOAPAction(String object, JSRequest req,
				    Context cx, Scriptable scope) 
      throws ESXXException, javax.xml.soap.SOAPException {
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

	result = callJSMethod(object,
			      soap_body.getDocumentElement().getLocalName(),
			      args, "SOAP method", cx, scope);
      }
      else {
	// No RPC handler; the SOAP message itself is the result

	result = esxx.domToE4X(message.getSOAPPart(), cx, scope);
      }

      return result;
    }


    private Object handleHTTPMethod(String request_method, JSRequest req, ESXXParser parser,
				    Context cx, Scriptable scope) 
      throws ESXXException {
      Object result;
      String handler = parser.getHandlerFunction(request_method);

      if (handler == null) {
	throw new ESXXException(501, "'" + request_method + "' handler not defined.");
      }

      Object args[] = { req };

      result = callJSMethod(handler,
			    args, "'" + request_method + "' handler", cx, scope);

      return result;
    }


    private Object handleError(Exception error, ESXXParser parser,
			       Context cx, Scriptable scope) 
      throws ESXXException {
      Object result;
      String handler = parser.getErrorHandlerFunction();

      try {
	Object args[] = { cx.javaToJS(error, scope) };

	result = callJSMethod(handler, args, "Error handler", cx, scope);
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


    private Object callJSMethod(String expr,
				Object[] args, String identifier,
				Context cx, Scriptable scope)
      throws ESXXException {
      String object;
      String method;

      int dot = expr.lastIndexOf('.');

      if (dot == -1) {
	object = null;
	method = expr;
      }
      else {
	object = expr.substring(0, dot);
	method = expr.substring(dot + 1);
      }

      return callJSMethod(object, method, args, identifier, cx, scope);
    }

    private Object callJSMethod(String object, String method,
				Object[] args, String identifier,
				Context cx, Scriptable scope)
      throws ESXXException {
      Scriptable o;

      if (object == null) {
	o = scope;
      }
      else {
	o = (Scriptable) cx.evaluateString(scope, object, identifier, 1, null);

	if (o == null || o == ScriptableObject.NOT_FOUND) {
	  throw new ESXXException(identifier + " '" + object + "." + method + "' not found.");
	}
      }

      return ((ScriptableObject) scope).callMethod(cx, o, method, args);
    }

//     private static class MyFactory extends ContextFactory {
// 	protected boolean hasFeature(Context cx, int featureIndex) {
// 	  if (featureIndex == Context.FEATURE_DYNAMIC_SCOPE) {
// 	    return true;
// 	  }
//
// 	  return super.hasFeature(cx, featureIndex);
// 	}
//     }
//
//     static {
//       // Enable dynamic scopes
//       ContextFactory.initGlobal(new MyFactory());
//     }

    private ESXX esxx;
}
