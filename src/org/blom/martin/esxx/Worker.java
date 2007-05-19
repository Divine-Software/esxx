
package org.blom.martin.esxx;

import java.io.*;
import java.net.URL;
import java.util.Properties;
import javax.xml.soap.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.blom.martin.esxx.js.*;
import org.mozilla.javascript.*;

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
					parser.getXML(),
					parser.getStylesheet());

	    cx.putThreadLocal(JSESXX.class, js_esxx);

	    // Execute all <?esxx and <?esxx-import PIs, if not already done
	    parser.execute(cx, scope);

	    Object result = null;
	    Exception error = null;

	    try {
	      // Parse SOAP message, if any
	      parseSOAPMessage(js_esxx);

	      // Execute the SOAP or HTTP handler (if available)
	      ESXXParser.SOAPAction action = null;

	      if (js_esxx.soapMessage != null) {
		action = parser.getSOAPAction(js_esxx.soapAction);

		if (action == null) {
		  // Try default action object
		  action = parser.getSOAPAction("");
		}
	      }

	      if (action != null) {
		// Install action-supplied stylesheet
		if (action.stylesheet != null && !action.stylesheet.equals("")) {
		  js_esxx.stylesheet = action.stylesheet;
		}

		if (action.object != null && !action.object.equals("")) {
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

		  result = callJSMethod(action.object,
					soap_body.getDocumentElement().getLocalName(),
					args, "SOAP method", cx, scope);
		}
		else {
		  // No RPC handler; the SOAP message itself is the result

		  result = esxx.domToE4X(js_esxx.soapMessage.getSOAPPart(), cx, scope);
		}
	      }
	      else if (parser.hasHandlers()) {
		String handler = parser.getHandlerFunction(request_method);

		if (handler == null) {
		  throw new ESXXException("'" + request_method + "' handler not defined.");
		}

		result = callJSMethod(handler,
				      null, "'" + request_method + "' handler", cx, scope);
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
	      }
	      else {
		// No error handler installed: throw away
		throw error;
	      }
	    }

	    // No error or error handled: Did we get a valid result?
	    if (result == null || result instanceof org.mozilla.javascript.Undefined) {
	      throw new ESXXException("No result from '" + workload.getURL() + "'");
	    }

	    Source src;

	    try {
	      src = new DOMSource(org.mozilla.javascript.xmlimpl.XMLLibImpl.toDomNode(result));
	    }
	    catch (Exception ex) {
	      src = new StreamSource(new StringReader(result.toString()));
	    }


	    Transformer tr;

	    try {
	      if (js_esxx.stylesheet != null && !js_esxx.stylesheet.equals("")) {
		URL stylesheet = new URL(workload.getURL(), js_esxx.stylesheet);	

		tr = esxx.getCachedStylesheet(stylesheet);
	      }
	      else {
		tr = esxx.getCachedStylesheet(null);

		// Set media-type, if specified
		Object content_type = ScriptableObject.getProperty(js_esxx.headers,
								   "Content-Type");

		if (content_type != null &&
		    !(content_type instanceof org.mozilla.javascript.Undefined)) {
		  tr.setOutputProperty("media-type", content_type.toString());
		}
	      }
	    }
	    catch (IOException ex) {
	      throw new ESXXException("Unable to load stylesheet: " + ex.getMessage());
	    }

	    tr.transform(src, new StreamResult(workload.getOutWriter()));

	    Properties p = new Properties();

// 	    for (String s : tr.getOutputProperties().stringPropertyNames()) {
// 	      p.setProperty("_" + s, tr.getOutputProperties().getProperty(s));
// 	    }

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

	    // Copy Content-Type from the stylesheet
	    p.setProperty("Content-Type", tr.getOutputProperty("media-type"));

	    // Return workload
	    workload.finished(0, p);
	  }
	  catch (Exception ex) {
	    Properties h = new Properties();
	    String title = "ESXX Server Error";

	    h.setProperty("Status", "500 " + title);
	    h.setProperty("Content-Type", "text/html");

	    PrintWriter out = new PrintWriter(workload.getOutWriter());

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

	    workload.finished(500, h);
	  }
	}
// 	catch (java.net.URISyntaxException ex) {
// 	  ex.printStackTrace();
// 	}
	catch (InterruptedException ex) {
	  // Don't know what to do here ... die?
	  ex.printStackTrace();
	  return null;
	}
      }
    }

    private void parseSOAPMessage(JSESXX js_esxx)
      throws ESXXException {

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