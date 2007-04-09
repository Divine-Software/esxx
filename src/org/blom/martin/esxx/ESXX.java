
package org.blom.martin.esxx;

import org.blom.martin.esxx.js.JSESXX;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.stream.XMLStreamException;
import javax.xml.soap.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.mozilla.javascript.*;

public class ESXX {
    public static final String NAMESPACE = "http://martin.blom.org/esxx/1.0/";

    public class ESXXException 
      extends Exception {
	public ESXXException(String why) { super(why); }
    }

    public ESXX(Properties p) {
      settings = p;

      // Custom CGI-to-HTTP translations
      cgiToHTTPMap.put("HTTP_SOAPACTION", "SOAPAction");
      cgiToHTTPMap.put("CONTENT_TYPE", "Content-Type");
      cgiToHTTPMap.put("CONTENT_LENGTH", "Content-Length");

      transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setURIResolver(new URIResolver() {
	    public Source resolve(String href,
				  String base)
	      throws TransformerException {
	      try {
		return new StreamSource(openCachedURL(new URL(new URL(base), href)));
	      }
	      catch (MalformedURLException ex) {
		throw new TransformerException("MalformedURLException: " + ex.getMessage());
	      }
	      catch (IOException ex) {
		throw new TransformerException("IOException: " + ex.getMessage());
	      }
	    }
	});


      workerThreads = new ThreadGroup("ESXX worker threads");
      workloadQueue = new LinkedBlockingQueue<Workload>(MAX_WORKLOADS);

      // Create worker threads

      int threads = Integer.parseInt(
	settings.getProperty("esxx.worker_threads", 
			     "" + Runtime.getRuntime().availableProcessors() * 2));

      for (int i = 0; i < threads; ++i) {
	Thread t = new Thread(
	  workerThreads, 
	  new Runnable() {
	      public void run() {
		// Create the JavaScript thread context and invoke workerThread()
		Context.call(new ContextAction() {
		      public Object run(Context cx) {
			workerThread(cx);
			return null;
		      }
		  });
	      }
	  },
	  "ESXX worker thread " + i);

	t.start();
      }
    }

    public void addWorkload(Workload w) {
      while (true) {
	try {
	  workloadQueue.put(w);
	  break;
	}
	catch (InterruptedException ex) {
	  // Retry
	}
      }
    }


    public String serializeNode(org.w3c.dom.Node node, boolean omit_xml_declaration) {
      try {
	StringWriter sw = new StringWriter();

	Transformer tr = transformerFactory.newTransformer();

	tr.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,
			     omit_xml_declaration ? "yes" : "no");

	DOMSource src = new DOMSource(node);
	StreamResult  dst = new StreamResult(sw);

	tr.transform(src, dst);
	return sw.toString();
      }
      catch (TransformerException ex) {
	ex.printStackTrace();
	return "";
      }
    }


    public Scriptable domToE4X(org.w3c.dom.Node node, Context cx, Scriptable scope) {
      if (node == null) {
	return null;
      }

//      String cmd = "<>" + serializeNode(node, true) + "</>;";
      String cmd = serializeNode(node, true);
      return (Scriptable) cx.evaluateString(scope, cmd, "<domToE4X>", 0, null);
    }


    public org.w3c.dom.Node e4xToDOM(Scriptable node) {
      try {
	return org.mozilla.javascript.xmlimpl.XMLLibImpl.toDomNode(node);
      }
      catch (Exception ex) {
	// Use Transformation API to convert node
      }

      try {
	Source      src = new StreamSource(new StringReader(node.toString()));
	Transformer tr  = transformerFactory.newTransformer();
	DOMResult   res = new DOMResult();

	tr.transform(src, res);
	return res.getNode();
      }
      catch (Exception ex) {
	return null;
      }
    }

    public String cgiToHTTP(String name) {
      String h = cgiToHTTPMap.get(name);

      // If there was a mapping, use it

      if (h != null) {
	return h;
      }

      if (name.startsWith("HTTP_")) {
	// "Guess" the name by capitalizing the variable name

	StringBuilder str = new StringBuilder();

	boolean cap = true;
	for (int i = 5; i < name.length(); ++i) {
	  char c = name.charAt(i);

	  if (c == '_') {
	    str.append('-');
	    cap = true;
	  }
	  else if (cap) {
	    str.append(Character.toUpperCase(c));
	    cap = false;
	  }
	  else {
	    str.append(Character.toLowerCase(c));
	  }
	}

	return str.toString();
      }
      else {
	return null;
      }
    }

    public InputStream openCachedURL(URL url) 
      throws IOException {
      return url.openStream();
    }


    public ESXXParser getCachedESXXParser(URL url)
      throws XMLStreamException, IOException {
      return new ESXXParser(this, url);
    }


    public Transformer getCachedStylesheet(URL url)
      throws ESXXException, XMLStreamException, IOException {
      try {
	Templates t = transformerFactory.newTemplates(new StreamSource(openCachedURL(url)));

	return t.newTransformer();
      }
      catch (TransformerConfigurationException ex) {
	throw new ESXXException("TransformerConfigurationException: " + ex.getMessage());
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

    private void workerThread(Context cx) {
      // Provide a better mapping for primitive types on this context

      cx.getWrapFactory().setJavaPrimitiveWrap(false);


      // Now wait for workloads and execute them

      while (true) {
	try {
	  Workload workload = workloadQueue.take();
	  String method = workload.getProperties().getProperty("REQUEST_METHOD");
	  
	  try {
	    ESXXParser parser = getCachedESXXParser(workload.getURL());

	    Scriptable scope   = new ImporterTopLevel(cx, false);
	    JSESXX     js_esxx = new JSESXX(this, cx, scope, workload, 
					    parser.getXML(), parser.getStylesheet());
	    Object     esxx    = Context.javaToJS(js_esxx, scope);
	    ScriptableObject.putProperty(scope, "esxx", esxx);
	    
	    Object result = null;
	    Exception error = null;

	    try {
	      // Execute all <?esxx and <?esxx-import PIs

	      for (ESXXParser.Code c : parser.getCodeList()) {
		cx.evaluateString(scope, c.code, c.url.toString(), c.line, null);
	      }

	      // Parse SOAP message, if any
	      parseSOAPMessage(js_esxx);

	      // Execute the SOAP or HTTP handler (if available)

	      if (js_esxx.soapMessage != null) {
		ESXXParser.SOAPAction action = parser.getSOAPAction(js_esxx.soapAction);

		if (action == null) {
		  // Try default action object
		  action = parser.getSOAPAction("");
		}
		
		if (action == null) {
		  throw new ESXXException("'" + js_esxx.soapAction + "' SOAP action not defined.");
		}

		// Install action-supplied stylesheet
		if (action.stylesheet != null && !action.stylesheet.equals("")) {
		  js_esxx.stylesheet = action.stylesheet;
		}

		org.w3c.dom.Node     soap_header = null;
		org.w3c.dom.Document soap_body   = null;

		try { 
		  soap_header = js_esxx.soapMessage.getSOAPHeader();
		}
		catch (SOAPException ex) {
		  // The header is optional
		}

		soap_body = js_esxx.soapMessage.getSOAPBody().extractContentAsDocument();
	
		String handler = (action.object + "." + 
				  soap_body.getDocumentElement().getLocalName());

		Object fobj = cx.evaluateString(scope, handler,
						"<soap/>", 1, null);
		
		if (fobj == null || 
		    fobj == ScriptableObject.NOT_FOUND) {
		  throw new ESXXException("SOAP method '" + handler + "' not found.");
		}
		else if (!(fobj instanceof Function)) {
		  throw new ESXXException("SOAP method '" + handler + 
					  "' is not a valid function.");
		} 
		else {
		  Object args[] = { domToE4X(soap_header, cx, scope),
				    domToE4X(soap_body, cx, scope) };
		  Function f = (Function) fobj;
		  result = f.call(cx, scope, scope, args);
		}

	      }
	      else if (parser.hasHandlers()) {
		String handler = parser.getHandlerFunction(method);

		if (handler == null) {
		  throw new ESXXException("'" + method + "' handler not defined.");
		}

		Object fobj = cx.evaluateString(scope, handler,
						"<handler/>", 1, null);
		if (fobj == null || 
		    fobj == ScriptableObject.NOT_FOUND) {
		  throw new ESXXException("'" + method + "' handler '" + handler + 
					  "' not found.");
		}
		else if (!(fobj instanceof Function)) {
		  throw new ESXXException("'" + method + "' handler '" + handler + 
					  "' is not a valid function.");
		} 
		else {
//		  Object args[] = { cx.javaToJS(ex, scope) };
		  Function f = (Function) fobj;
		  result = f.call(cx, scope, scope, null);
		}
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
		  Object fobj = cx.evaluateString(scope, handler, "<error-handler/>", 0, null);

		  if (fobj == null || 
		      fobj == ScriptableObject.NOT_FOUND ||
		      !(fobj instanceof Function)) {
		    // Error handler is not a function
		    throw new ESXXException("Error handler '" + handler + 
					    "' is not a valid function.");
		  } 
		  else {
		    Object args[] = { cx.javaToJS(error, scope) };
		    Function f = (Function) fobj;
		    result = f.call(cx, scope, scope, args);
		  }
		}	
		catch (Exception errex) {
		  throw new ESXXException("Failed to handle error '" + error.toString() + 
					  "':\n" +
					  "Error handler '" + handler + 
					  "' failed with message '" + 
					  errex.getMessage() + "'");
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

	    if (js_esxx.stylesheet != null && !js_esxx.stylesheet.equals("")) {
	      URL stylesheet = new URL(workload.getURL(), js_esxx.stylesheet);
	      
	      try {
		tr = getCachedStylesheet(stylesheet);
	      }
	      catch (IOException ex) {
		throw new ESXXException("Unable to load stylesheet: " + ex.getMessage());
	      }
	    }
	    else {
	      // Identity transformer
	      tr = transformerFactory.newTransformer();

	      // Set media-type, if specified
	      Object content_type = ScriptableObject.getProperty(js_esxx.headers, 
								 "Content-Type");

	      if (content_type != null && 
		  !(content_type instanceof org.mozilla.javascript.Undefined)) {
		tr.setOutputProperty("media-type", content_type.toString());
	      }
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
	catch (InterruptedException ex) {
	  // Don't know what to do here ... die?
	  ex.printStackTrace();
	  return;
	}
      }
    }
    


    private static final int MAX_WORKLOADS = 16;

    private Properties settings;
    private TransformerFactory  transformerFactory;
    private ThreadGroup workerThreads;
    private LinkedBlockingQueue<Workload> workloadQueue;
    private TreeMap<String,String> cgiToHTTPMap = new TreeMap<String,String>();
};
