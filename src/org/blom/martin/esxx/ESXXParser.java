
package org.blom.martin.esxx;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import javax.xml.stream.*;
import javax.xml.xpath.*;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.w3c.dom.*;

public class ESXXParser {

    public static class Code {
	public Code(URL u, int l, String s) {
	  url = u;
	  line = l;
	  source = s;
	  code = null;
	}

	public String toString() {
	  return url.toString() + "::" + line + ": " + code;
	}

	public URL url;
	public int line;
	public String source;
	public Script code;
    };

    public static class SOAPAction {
	public SOAPAction(String o, String s) {
	  object = o;
	  stylesheet = s;
	}

	public String object;
	public String stylesheet;
    }

    public ESXXParser(ESXX esxx, URL url)
      throws IOException, XMLStreamException {
      
      esxxObject = esxx;
      baseURL = url;
      xmlInputFactory = XMLInputFactory.newInstance();

      // Load and parse the document

      try {
	xml = esxxObject.parseXML(esxxObject.openCachedURL(url), url, externalURLs, null);

	// Extract ESXX information, if any

	try { 
	  XPath xpath = XPathFactory.newInstance().newXPath();
	  xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
		public String getNamespaceURI(String prefix) {
		  if (prefix.equals("esxx")) {
		    return ESXX.NAMESPACE;
		  }
		  else {
		    return javax.xml.XMLConstants.NULL_NS_URI;
		  }
		}

		public String getPrefix(String uri) {
		  throw new UnsupportedOperationException();
		}

		public java.util.Iterator getPrefixes(String uri) {
		  throw new UnsupportedOperationException();
		}
	    });

	  NodeList r = (NodeList) xpath.evaluate("processing-instruction() | " +
						 "esxx:esxx/esxx:settings/esxx:*", 
						 xml, XPathConstants.NODESET);
	  
	  for (int i = 0; i < r.getLength(); ++i) {
	    Node n = r.item(i);
	    
	    if (n.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
	      String name = n.getNodeName();
	      if (name.equals("esxx-stylesheet")) {
		handleStylesheet(n.getNodeValue());
	      }
	      else if (name.equals("esxx-import")) {
		handleImport(n.getNodeValue());
	      }
	      else if (name.equals("esxx")) {
		handleCode(url, 0, n.getNodeValue());
	      }
	    }
	    else if (n.getNodeType() == Node.ELEMENT_NODE) {
	      Element e = (Element) n;
	      String name = e.getLocalName();

	      // esxx/settings/* matched.
	      gotESXX = true;

	      if (name.equals("handler")) {
		handleHandler(e);
	      }
	      else if (name.equals("soap")) {
		handleSOAP(e);
	      }
	      else if (name.equals("error-handler")) {
		handleErrorHandler(e);
	      }
	    }
	  }
	}
	catch(XPathExpressionException ex) {
	  // Should never happen
	  ex.printStackTrace();
	  throw new XMLStreamException(ex.getMessage());
	}
      }
      catch (org.xml.sax.SAXException ex) {
	throw new XMLStreamException(ex.getMessage());
      }
    }

    public Collection<URL> getExternalURLs() {
      return externalURLs;
    }

    public Document getXML() {
      return xml;
    }

    public Collection<Code> getCodeList() {
      return codeList;
    }

    public URL getStylesheet() {
      return stylesheet;
    }

    public boolean hasHandlers() {
      return gotESXX;
    }

    public String getHandlerFunction(String http_method) {
      return handlers.get(http_method);
    }

    public SOAPAction getSOAPAction(String action) {
      return soapActions.get(action);
    }

    public String getErrorHandlerFunction() {
      return errorHandler;
    }

    public synchronized Scriptable compile(Context cx, Scriptable shared_scope) {
      if (applicationScope != null) {
	return applicationScope;
      }

      for (Code c : codeList) {
	c.code = cx.compileString(c.source, c.url.toString(), c.line, null);
      }

      // Use the shared top-level scope, but put global variables in the application scope
      applicationScope = cx.newObject(shared_scope);
      applicationScope.setPrototype(shared_scope);
      applicationScope.setParentScope(null);

      return applicationScope;
    }

    public synchronized void execute(Context cx, org.blom.martin.esxx.js.JSESXX js_esxx) {
      if (!hasExecuted) {
	// Temporary add the top-level "esxx" variable to the application scope
	ScriptableObject.putProperty(applicationScope, "esxx", 
				     Context.javaToJS(js_esxx, applicationScope));

	for (Code c : codeList) {
	  c.code.exec(cx, applicationScope);
	}
	
	// Clean up the application scope
	ScriptableObject.deleteProperty(applicationScope, "esxx"); 

	hasExecuted = true;
      }
    }

    private void handleStylesheet(String data)
      throws XMLStreamException {

      XMLStreamReader xsr = xmlInputFactory.createXMLStreamReader(
	new StringReader("<esxx-stylesheet " + data + "/>"));

      while (xsr.hasNext()) {
	if (xsr.next() == XMLStreamConstants.START_ELEMENT) {
	  String type = xsr.getAttributeValue(null, "type");
	  if (type == null || !type.equals("text/xsl")) {
	    throw new XMLStreamException("<?esxx-stylesheet?> attribute 'type' "
					 + "must be set to 'text/xsl'");
	  }

	  String href = xsr.getAttributeValue(null, "href");

	  if (href == null) {
	    throw new XMLStreamException("<?esxx-stylesheet?> attribute 'href' "
					 + "must be specified");
	  }

	  try {
	    stylesheet = new URL(baseURL, href);
	  }
	  catch (MalformedURLException ex) {
	    throw new XMLStreamException("<?esxx-stylesheet?> attribute 'href' is invalid: "
					 + ex.getMessage());
	  }
	}
      }

      xsr.close();
    }

    private void handleImport(String data)
      throws XMLStreamException {

      XMLStreamReader xsr = xmlInputFactory.createXMLStreamReader(
	new StringReader("<esxx-import " + data + "/>"));

      while (xsr.hasNext()) {
	if (xsr.next() == XMLStreamConstants.START_ELEMENT) {
	  String href = xsr.getAttributeValue(null, "href");

	  if (href == null) {
	    throw new XMLStreamException("<?esxx-import?> attribute 'href' "
					 + "must be specified");
	  }

	  try {
	    URL url = new URL(baseURL, href);
	    BufferedReader br = new BufferedReader(new InputStreamReader(
						     esxxObject.openCachedURL(url)));
	    StringBuilder code = new StringBuilder();
	    String line;

	    while ((line = br.readLine()) != null) {
	      code.append(line);
	      code.append("\n");
	    }

	    handleCode(url, 1, code.toString());

	    externalURLs.add(url);
	  }
	  catch (MalformedURLException ex) {
	    throw new XMLStreamException("<?esxx-import?> attribute 'href' is invalid: "
					 + ex.getMessage());
	  }
	  catch (IOException ex) {
	    throw new XMLStreamException("<?esxx-import?> failed to include document: "
					 + ex.getMessage());
	  }
	}
      }
      
      xsr.close();
    }

    private void handleCode(URL url, int line, String data) {
      codeList.add(new Code(url, line, data));
    }

    private void handleHandler(Element e) 
      throws org.xml.sax.SAXException {
      String handler = e.getAttributeNS(null, "function").trim();

      if (handler.endsWith(")")) {
	throw new org.xml.sax.SAXException("In <handler>: handler names " +
					   "should not include parentheses");
      }

      handlers.put(e.getAttributeNS(null, "type"), handler);
    }

    private void handleSOAP(Element e) 
      throws org.xml.sax.SAXException {
      String object = e.getAttributeNS(null, "object").trim();
      String stylesheet = e.getAttributeNS(null, "stylesheet").trim();

      soapActions.put(e.getAttributeNS(null, "action"), new SOAPAction(object, stylesheet));
    }

    private void handleErrorHandler(Element e)
      throws org.xml.sax.SAXException {
      String handler = e.getAttributeNS(null, "function").trim();

      if (handler.endsWith(")")) {
	throw new org.xml.sax.SAXException("In <error-handler>: handler names " +
					   "should not include parentheses");
      }

      errorHandler = handler;
    }

    private XMLInputFactory xmlInputFactory;

    private ESXX esxxObject;
    private URL baseURL;
    private LinkedList<URL> externalURLs = new LinkedList<URL>();

    private Scriptable applicationScope = null;
    private boolean hasExecuted = false;

    private boolean gotESXX = false;

    private Document xml;
    private StringBuilder code = new StringBuilder();
    private URL stylesheet;
    private LinkedList<Code> codeList = new LinkedList<Code>();

    private HashMap<String,String> handlers = new HashMap<String,String>();
    private HashMap<String,SOAPAction> soapActions = new HashMap<String,SOAPAction>();
    private String errorHandler;
};
