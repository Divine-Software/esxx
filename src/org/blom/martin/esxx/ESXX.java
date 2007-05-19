
package org.blom.martin.esxx;

//import org.blom.martin.esxx.js.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.util.Properties;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.mozilla.javascript.*;
import java.util.Collection;
import org.w3c.dom.Document;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.bootstrap.*;
import javax.xml.parsers.*;


public class ESXX {
    /** A string that defines the ESXX XML namespace */
    public static final String NAMESPACE = "http://martin.blom.org/esxx/1.0/";

    /** The constructor.
     *
     *  Will initialize the operating environment, start the worker
     *  threads and initialize the JavaScript contexts.
     *
     *  @param p A set of properties that can be used to tune the
     *  execution. Currently, only "esxx.worker_threads" is defined,
     *  which defaults to (number of CPUs * 2).
     *
     *  @throws ParserConfigurationException If no XML parser could be
     *  found.
     *
     *  @throws ClassNotFoundException On DOM implementation errors
     *
     *  @throws InstantiationException On DOM implementation errors
     *
     *  @throws IllegalAccessException On DOM implementation errors
     */

    public ESXX(Properties p)
      throws ParserConfigurationException, ClassNotFoundException, InstantiationException, IllegalAccessException {
      settings = p;

      memoryCache = new MemoryCache(
	this,
	Integer.parseInt(settings.getProperty("esxx.cache.max_entries", "1024")),
	Long.parseLong(settings.getProperty("esxx.cache.max_size", "16")) * 1024 * 1024,
	Long.parseLong(settings.getProperty("esxx.cache.max_age", "3600")) * 1000);

      parsers = new Parsers(this);

      // Custom CGI-to-HTTP translations
      cgiToHTTPMap.put("HTTP_SOAPACTION", "SOAPAction");
      cgiToHTTPMap.put("CONTENT_TYPE", "Content-Type");
      cgiToHTTPMap.put("CONTENT_LENGTH", "Content-Length");

      documentBuilderFactory = DocumentBuilderFactory.newInstance();

      documentBuilderFactory.setExpandEntityReferences(true);
      documentBuilderFactory.setNamespaceAware(true);
      documentBuilderFactory.setValidating(true);
      documentBuilderFactory.setXIncludeAware(true);

      DOMImplementationRegistry reg  = DOMImplementationRegistry.newInstance();
      domImplementation = reg.getDOMImplementation("XML 3.0");

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

      // Set up shared main context
      Context cx = Context.enter();

      try {
	// Create worker threads

	workerThreads = new ThreadGroup("ESXX worker threads");
	workloadQueue = new LinkedBlockingQueue<Workload>(MAX_WORKLOADS);

	int threads = Integer.parseInt(
	  settings.getProperty("esxx.worker_threads",
			       "" + Runtime.getRuntime().availableProcessors() * 2));

	for (int i = 0; i < threads; ++i) {
	  Thread t = new Thread(
	    workerThreads,
	    new Runnable() {
		public void run() {
		  // Create the JavaScript thread context and invoke
		  // run() on the new Worker object
		  Context.call(new Worker(ESXX.this));
		}
	    },
	    "ESXX worker thread " + i);

	  t.start();
	}
      }
      catch (Exception ex) {
	ex.printStackTrace();
      }
      finally {
	cx.exit();
      }
    }


    /** Adds a Workload to the work queue.
     *
     *  Once the workload has been executed, Workload.finished will be
     *  called with an ignorable returncode and a set of HTTP headers.
     *
     *  @param w  The Workload object that is to be executed.
     */

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

    public Workload getWorkload() 
      throws InterruptedException {
      return workloadQueue.take();
    }

    /** Utility method that serializes a W3C DOM Node to a String.
     *
     *  @param node  The Node to be serialized.
     *
     *  @param omit_xml_declaration  Set to 'true' if you don't want the XML decl.
     *
     *  @return A String containing the XML representation of the supplied Node.
     */

    public String serializeNode(org.w3c.dom.Node node, boolean omit_xml_declaration) {
      try {
	StringWriter sw = new StringWriter();

	Transformer tr = transformerFactory.newTransformer();

	tr.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,
			     omit_xml_declaration ? "yes" : "no");
	tr.setOutputProperty(OutputKeys.METHOD, "xml");

	DOMSource src = new DOMSource(node);
	StreamResult  dst = new StreamResult(sw);

	tr.transform(src, dst);
	return sw.toString();
      }
      catch (TransformerException ex) {
	// Should never happen
	ex.printStackTrace();
	return "";
      }
    }

    /** Utility method that converts a W3C DOM Node into an E4X XML object.
     *
     *  @param node  The Node to be converted.
     *
     *  @param cx    The current JavaScript context.
     *
     *  @param scope The current JavaScript scope.
     *
     *  @return A Scriptable representing an E4X XML object.
     */

    public Scriptable domToE4X(org.w3c.dom.Node node, Context cx, Scriptable scope) {
      if (node == null) {
	return null;
      }

      // Please, somebody kill me!

      String cmd = serializeNode(node, true);

      try {
	return cx.newObject(scope, "XML", new String[] { cmd });
      }
      catch (Exception ex) {
	return (Scriptable) cx.evaluateString(scope, cmd, "<domToE4X>", 0, null);
      }
    }


    /** Utility method that converts an E4X XML object into a W3C DOM Node.
     *
     *  @param node  The E4X XML node to be converted.
     *
     *  @return A W3C DOM Node.
     */

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


    /** Utility method to create a new W3C DOM document.
     *
     *  @param name  The name of the document element
     *
     *  @return A W3C DOM Document.
     */

    public Document createDocument(String name) {
      return domImplementation.createDocument(null, name, null);
    }


    /** Utility method that translates the name of a CGI environment
     *  variable into it's original HTTP header name.
     *
     *  @param name The name of a CGI variable.
     *
     *  @return  The name of the original HTTP header, or null if this
     *  variable name is unknown.
     */

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


    /** Utility method that parses an InputStream into a W3C DOM
     *  Document.
     *
     *  @param is  The InputStream to be parsed.
     *
     *  @param is_url  The location of the InputStream.
     *
     *  @param external_urls A Collection of URLs that will be
     *  populated with all URLs visited during the parsing. Can be
     *  'null'.
     *
     *  @param err A PrintWriter that will be used to report parser
     *  errors. Can be 'null'.
     *
     *  @return A W3C DOM Document.
     *
     *  @throws SAXException On parser errors.
     *
     *  @throws IOException On I/O errors.
     */

    public Document parseXML(InputStream is, final URL is_url,
			     final Collection<URL> external_urls,
			     final PrintWriter err)
      throws org.xml.sax.SAXException, IOException {
      try {
	DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();

	db.setErrorHandler(new org.xml.sax.ErrorHandler() {
	      public void error(org.xml.sax.SAXParseException ex) {
		if (err != null) {
		  err.println("XML parser error in " + ex.getSystemId() + "#" +
			      ex.getLineNumber() + ": " + ex.getMessage());
		}
	      }

	      public void fatalError(org.xml.sax.SAXParseException ex)
		throws org.xml.sax.SAXParseException {
		throw ex;
	      }

	      public void warning(org.xml.sax.SAXParseException ex) {
		err.println("XML parser warning in " + ex.getSystemId() + "#" +
			    ex.getLineNumber() + ": " + ex.getMessage());
	      }
	  });

	db.setEntityResolver(new org.xml.sax.EntityResolver() {
	      public org.xml.sax.InputSource resolveEntity (String publicID,
							    String systemID)
		throws org.xml.sax.SAXException {

		try {
		  if (systemID != null) {
		    URL url = new URL(is_url, systemID);

		    org.xml.sax.InputSource src = new org.xml.sax.InputSource(openCachedURL(url));
		    src.setSystemId(url.toString());

		    if (external_urls != null) {
		      external_urls.add(url);
		    }

		    return src;
		  }
		  else {
		    throw new org.xml.sax.SAXException("Missing system ID");
		  }
		}
		catch (MalformedURLException ex) {
		  throw new org.xml.sax.SAXException("Malformed URL for system ID " +
						     systemID + ": " + ex.getMessage());
		}
		catch (IOException ex) {
		  throw new org.xml.sax.SAXException("Unable to load resource for system ID " +
						     systemID + ": " + ex.getMessage());
		}
	      }
	  });

	return db.parse(is, is_url.toString());
      }
      catch (ParserConfigurationException ex) {
	throw new org.xml.sax.SAXException("ParserConfigurationException: " + ex);
      }
    }


    public InputStream openCachedURL(URL url, String[] content_type)
      throws IOException {
      return memoryCache.openCachedURL(url, content_type);
    }

    public InputStream openCachedURL(URL url)
      throws IOException {
      return memoryCache.openCachedURL(url, null);
    }

    public Object parseStream(String mime_type, HashMap<String,String> mime_params,
			      InputStream is, URL is_url,
			      Collection<URL> external_urls,
			      PrintWriter err, 
			      Context cx, Scriptable scope) 
      throws Exception {
      return parsers.parse(mime_type, mime_params, is, is_url, external_urls, err, cx, scope);
    }

    public ESXXParser getCachedESXXParser(URL url)
      throws XMLStreamException, IOException {
      return memoryCache.getCachedESXXParser(url);
    }


    public Transformer getCachedStylesheet(URL url)
      throws ESXXException, XMLStreamException, IOException {
      try {
	if (url != null) {
	  Templates t = transformerFactory.newTemplates(new StreamSource(openCachedURL(url)));

	  return t.newTransformer();
	}
	else {
	  // Identity transformer
	    return transformerFactory.newTransformer();
	}
      }
      catch (TransformerConfigurationException ex) {
	throw new ESXXException("TransformerConfigurationException: " + ex.getMessage());
      }
    }

    public static void copyStream(InputStream is, OutputStream os) 
      throws IOException {
      byte buffer[] = new byte[8192];
               
      int bytesRead;
               
      while ((bytesRead = is.read(buffer)) != -1) {
	os.write(buffer, 0, bytesRead);
      }
               
      os.flush();
      os.close();
    }


    private static final int MAX_WORKLOADS = 16;

    private MemoryCache memoryCache;
    private Parsers parsers;
    private Properties settings;
    private DocumentBuilderFactory documentBuilderFactory;
    private DOMImplementation domImplementation;
    private TransformerFactory  transformerFactory;
    private ThreadGroup workerThreads;
    private LinkedBlockingQueue<Workload> workloadQueue;
    private HashMap<String,String> cgiToHTTPMap = new HashMap<String,String>();
};
