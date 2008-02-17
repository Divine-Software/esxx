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

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.*;
import org.mozilla.javascript.*;
import org.w3c.dom.*;
import org.w3c.dom.bootstrap.*;
import org.w3c.dom.ls.*;


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
     *  @throws ClassNotFoundException On DOM implementation errors
     *
     *  @throws InstantiationException On DOM implementation errors
     *
     *  @throws IllegalAccessException On DOM implementation errors
     */

    public ESXX(Properties p)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException {
      settings = p;

      memoryCache = new MemoryCache(
	this,
	Integer.parseInt(settings.getProperty("esxx.cache.max_entries", "1024")),
	Long.parseLong(settings.getProperty("esxx.cache.max_size", "16")) * 1024 * 1024,
	Long.parseLong(settings.getProperty("esxx.cache.max_age", "3600")) * 1000);

      parsers = new Parsers(this);

      // Custom CGI-to-HTTP translations
      cgiToHTTPMap = new HashMap<String,String>();
      cgiToHTTPMap.put("HTTP_SOAPACTION", "SOAPAction");
      cgiToHTTPMap.put("CONTENT_TYPE", "Content-Type");
      cgiToHTTPMap.put("CONTENT_LENGTH", "Content-Length");

      DOMImplementationRegistry reg  = DOMImplementationRegistry.newInstance();
      domImplementation = reg.getDOMImplementation("XML 3.0");
      domImplementationLS = (DOMImplementationLS) domImplementation;

      lsSerializer = domImplementationLS.createLSSerializer();

      DOMConfiguration dc = lsSerializer.getDomConfig();
      dc.setParameter("xml-declaration", false);

      transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setURIResolver(new URIResolver(null));

      numWorkerThreads = Integer.parseInt(
	settings.getProperty("esxx.worker_threads",
			     "" + Runtime.getRuntime().availableProcessors() * 2));

      // Set up shared main context
      Context cx = Context.enter();

      try {
	// Create worker threads

	workerThreads = new ThreadGroup("ESXX worker threads");
	workloadQueue = new LinkedBlockingQueue<Workload>(MAX_WORKLOADS);

	for (int i = 0; i < numWorkerThreads; ++i) {
	  createWorker();
	}
      }
      catch (Exception ex) {
	ex.printStackTrace();
      }
      finally {
	cx.exit();
      }
    }

    public synchronized void createWorker() {
      Thread t = new Thread(
	workerThreads,
	new Runnable() {
	    public void run() {
	      // Create the JavaScript thread context and invoke
	      // run() on the new Worker object
	      Context.call(new Worker(ESXX.this));
	    }
	},
	"ESXX worker thread");

      t.start();
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
     *  @return A String containing the XML representation of the supplied Node.
     */

    public String serializeNode(org.w3c.dom.Node node) {
      try {
	return lsSerializer.writeToString(node);
      }
      catch (LSException ex) {
	// Should never happen
	ex.printStackTrace();
	throw new ESXXException("Unable to serialize DOM Node: " + ex.getMessage(), ex);
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

      return cx.newObject(scope, "XML", new org.w3c.dom.Node[] { node });
    }


    /** Utility method that converts an E4X XML object into a W3C DOM Node.
     *
     *  @param node  The E4X XML node to be converted.
     *
     *  @return A W3C DOM Node.
     */

    public org.w3c.dom.Node e4xToDOM(Scriptable node) {
      return org.mozilla.javascript.xmlimpl.XMLLibImpl.toDomNode(node);
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
      throws ESXXException {
      LSInput in = domImplementationLS.createLSInput();
      
      in.setSystemId(is_url.toString());
      in.setByteStream(is);

      LSParser p = domImplementationLS.createLSParser(DOMImplementationLS.MODE_SYNCHRONOUS, 
						      null);

      DOMErrorHandler eh = new DOMErrorHandler() {
	    public boolean handleError(DOMError error) {
	      DOMLocator  dl = error.getLocation();
	      String     pos = (dl.getUri() + ", line " + dl.getLineNumber() + 
				", column " + dl.getColumnNumber());
	      Throwable  rel = (Throwable) error.getRelatedException();

	      switch (error.getSeverity()) {
		case DOMError.SEVERITY_FATAL_ERROR:
		case DOMError.SEVERITY_ERROR:
		  if (rel instanceof ESXXException) {
		    throw (ESXXException) rel;
		  }
		  else {
		    throw new ESXXException(pos + ": " + error.getMessage(), rel);
		  }

		case DOMError.SEVERITY_WARNING:
		  err.println(pos + ": " + error.getMessage());
		  return true;
	      }

	      return false;
	    }
	};

      DOMConfiguration dc = p.getDomConfig();

      dc.setParameter("comments", false);
      dc.setParameter("cdata-sections", false);
      dc.setParameter("entities", false);
      dc.setParameter("validate-if-schema", true);
      dc.setParameter("error-handler", eh);
      dc.setParameter("resource-resolver", new URIResolver(external_urls));

      return p.parse(in);
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
      throws IOException {
      return memoryCache.getCachedESXXParser(url);
    }


    public Transformer getCachedStylesheet(URL url)
      throws IOException {
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

    public static String parseMIMEType(String ct, HashMap<String,String> params) {
      String[] parts = ct.split(";");
      String   type  = parts[0].trim();

      // Add all attributes
      for (int i = 1; i < parts.length; ++i) {
	String[] attr = parts[i].split("=", 2);

	if (attr.length == 2) {
	  params.put(attr[0].trim(), attr[1].trim());
	}
      }
      
      return type;
    }


    private class URIResolver
      implements javax.xml.transform.URIResolver, LSResourceResolver {
	public URIResolver(Collection<URL> log_visited) {
	  logVisited = log_visited;
	}

	public Source resolve(String href,
			      String base) {
	  URL url = getURL(href, base);
	  return new StreamSource(getIS(url));
	}


	public LSInput resolveResource(String type,
				       String namespaceURI,
				       String publicId,
				       String systemId,
				       String baseURI) {
	  LSInput lsi = domImplementationLS.createLSInput();
	  URL     url = getURL(systemId, baseURI);
	  
	  lsi.setSystemId(url.toString());
	  lsi.setByteStream(getIS(url));

	  return lsi;
	}

	private URL getURL(String uri, String base_uri) {
	  try {
	    URL url = null;

	    if (base_uri != null) {
	      return new URL(new URL(base_uri), uri);
	    }
	    else {
	      return new URL(uri);
	    }
	  }
	  catch (MalformedURLException ex) {
	    throw new ESXXException("URIResolver error: " + ex.getMessage(), ex);
	  }
	}

	private InputStream getIS(URL url) {
	  try {
	    InputStream is = openCachedURL(url);

	    if (logVisited != null) {
	      // Log visited URLs if successfully opened
	      logVisited.add(url);
	    }

	    return is;
	  }
	  catch (IOException ex) {
	    throw new ESXXException("URIResolver error: " + ex.getMessage(), ex);
	  }
	}

	private Collection<URL> logVisited;
    }



    private static final int MAX_WORKLOADS = 16;

    private MemoryCache memoryCache;
    private Parsers parsers;
    private Properties settings;
    private HashMap<String,String> cgiToHTTPMap;

    private DOMImplementation domImplementation;
    private DOMImplementationLS domImplementationLS;
    private LSSerializer lsSerializer;
    private TransformerFactory  transformerFactory;

    private ThreadGroup workerThreads;
    private int numWorkerThreads;
    private LinkedBlockingQueue<Workload> workloadQueue;
};
