
package org.blom.martin.esxx;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
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
	    JSESXX js_esxx = new JSESXX(esxx, cx, scope,
					workload,
					null,
					parser.getXML());

	    cx.putThreadLocal(JSESXX.class, js_esxx);

	    // Execute all <?esxx and <?esxx-import PIs, if not already done
	    parser.execute(cx, scope);

	    Object    result = null;
	    Exception error  = null;

	    try {
	      // Parse input message, if any
	      parseInputMessage(js_esxx, cx, scope);

	      // Execute the SOAP or HTTP handler (if available)
	      String object = getSOAPAction(js_esxx, parser);

	      if (object != null) {
		result = handleSOAPAction(object, js_esxx, cx, scope);
	      }
	      else if (parser.hasHandlers()) {
		result = handleHTTPMethod(request_method, parser, cx, scope);
	      }
	      else {
		// No handlers; the document is the result

		result = js_esxx.document;
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

	    // If result is an Array, extract Status and Content-Type first.
	    String status       = "200 OK";;
	    String content_type = null;

	    if (result instanceof Scriptable) {
	      Scriptable s = (Scriptable) result;
	      
	      if (s.has(2, s)) {
		status       = Context.toString(s.get(0, s));
		content_type = Context.toString(s.get(1, s));
		result       = s.get(2, s);
	      }
	      else if (s.has(1, s)) {
		content_type = Context.toString(s.get(0, s));
		result       = s.get(1, s);
	      }
	    }

	    // Handle result types
	    if (result instanceof ByteBuffer) {
	      if (content_type == null) {
		content_type = "application/octent-stream";
	      }
	    }
	    else if (result instanceof String) {
	      if (content_type == null) {
		content_type = "text/plain;charset=" + 
		  java.nio.charset.Charset.defaultCharset().name();
	      }
	    }
	    else if (result instanceof BufferedImage) {
	      if (content_type == null) {
		content_type = "image/png";
	      }

	      // TODO ...
	      throw new ESXXException("BufferedImage results not supported yet.");
	    }
	    else {
	      Node node;

	      try {
		node = (org.mozilla.javascript.xmlimpl.XMLLibImpl.toDomNode(result));
	      }
	      catch (Exception ex) {
		throw new ESXXException("Unsupported result type from '" + workload.getURL() + 
					"': " + result.getClass());
	      }

	      Transformer tr;

	      try {
		URL stylesheet = parser.getStylesheet(content_type);

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
		throw new ESXXException("Unable to load stylesheet: " + ex.getMessage());
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
	      result = bos;
	    }

	    Properties p = new Properties();

	    // Copy headers from the JS object

	    for (Object o : js_esxx.headers.getIds()) {
	      if (o instanceof String) {
		String id = (String) o;

		if (id.equals("Cookies")) {
		}
		else {
		  p.setProperty(id, ScriptableObject.getProperty(js_esxx.headers, id).toString());
		}
	      }
	      else if (o instanceof Integer) {
		Integer id = (Integer) o;
		String value = ScriptableObject.getProperty(js_esxx.headers, id).toString();

		String[] kv = value.split(":", 2);

		if (kv.length == 2) {
		  p.setProperty(kv[0], kv[1]);
		}
	      }
	    }

	    // Set Status
	    p.setProperty("Status", status);

	    // Set Content-Type
	    p.setProperty("Content-Type", content_type);

	    // Return workload
	    workload.finished(0, p, result);
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

	    workload.finished(500, h, sw.toString());
	  }
	}
	catch (InterruptedException ex) {
	  // Don't know what to do here ... die?
	  ex.printStackTrace();
	  return null;
	}
      }
    }

    private String getSOAPAction(JSESXX js_esxx, ESXXParser parser) 
      throws ESXXException, javax.xml.soap.SOAPException {
      String action = null;

      if (js_esxx.soapMessage != null) {
	action = parser.getSOAPAction(js_esxx.soapAction);

	if (action == null) {
	  // Try default action object
	  action = parser.getSOAPAction("");
	}
      }

      return action;
    }


    private void parseInputMessage(JSESXX js_esxx, Context cx, Scriptable scope)
      throws ESXXException, javax.xml.soap.SOAPException  {

      // Consume SOAP message, if any
      if (js_esxx.soapAction != null) {
	try {
	  InputStream in = js_esxx.in;
	  js_esxx.in = null;

	  js_esxx.soapMessage = MessageFactory.newInstance(
	    SOAPConstants.DYNAMIC_SOAP_PROTOCOL).createMessage(js_esxx.mimeHeaders, in);

	}
	catch (IOException ex) {
	  throw new ESXXException("Unable to read SOAP message stream: " + ex.getMessage());
	}
	catch (SOAPException ex) {
	  throw new ESXXException("Invalid SOAP message: " + ex.getMessage());
	}
      }
      else {
	// TODO: FIX THIS JUNK CODE!!
	try {
	  String[]               ct        = js_esxx.mimeHeaders.getHeader("Content-Type");
	  HashMap<String,String> params    = new HashMap<String,String>();

	  if (ct != null && ct.length == 1) {
	    String                 mime_type = ESXX.parseMIMEType(ct[0], params);

	    js_esxx.inputMessage = esxx.parseStream(mime_type, params, js_esxx.in, js_esxx.baseURL,
						    null, js_esxx.debug, cx, scope);
	  }
	}
	catch (Exception ex) {
	  throw new ESXXException(ex.getMessage());
	}
      }
    }


    private Object handleSOAPAction(String object, JSESXX js_esxx,
				    Context cx, Scriptable scope) 
      throws ESXXException, javax.xml.soap.SOAPException {
      Object result;

      if (!object.equals("")) {
	// RPC style SOAP handler

	org.w3c.dom.Node     soap_header = null;
	org.w3c.dom.Document soap_body   = null;

	try {
	  soap_header = js_esxx.soapMessage.getSOAPHeader();
	}
	catch (SOAPException ex) {
	  // The header is optional
	}

	soap_body = js_esxx.soapMessage.getSOAPBody().extractContentAsDocument();

	Object args[] = { esxx.domToE4X(soap_body, cx, scope),
			  esxx.domToE4X(soap_header, cx, scope) };

	result = callJSMethod(object,
			      soap_body.getDocumentElement().getLocalName(),
			      args, "SOAP method", cx, scope);
      }
      else {
	// No RPC handler; the SOAP message itself is the result

	result = esxx.domToE4X(js_esxx.soapMessage.getSOAPPart(), cx, scope);
      }

      return result;
    }


    private Object handleHTTPMethod(String request_method, ESXXParser parser,
				    Context cx, Scriptable scope) 
      throws ESXXException {
      Object result;
      String handler = parser.getHandlerFunction(request_method);

      if (handler == null) {
	throw new ESXXException("'" + request_method + "' handler not defined.");
      }

      result = callJSMethod(handler,
			    null, "'" + request_method + "' handler", cx, scope);

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
				ex.getMessage() + "'");
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
