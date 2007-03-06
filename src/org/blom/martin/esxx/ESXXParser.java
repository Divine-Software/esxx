
package org.blom.martin.esxx;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.TreeMap;

import javax.xml.stream.*;
import javax.xml.stream.events.*;
import javax.xml.stream.util.*;
import javax.xml.transform.*;
import javax.xml.transform.stax.*;

public class ESXXParser {

    public static class Code {
	public Code(URL u, int l, String c) {
	  url = u;
	  line = l;
	  code = c;
	}

	public String toString() {
	  return url.toString() + "::" + line + ": " + code;
	}

	public URL url;
	public int line;
	public String code;
    };

    public ESXXParser(URL url)
      throws java.io.IOException, XmlException, XMLStreamException {
      this(url.openStream(), url);
    }

    public ESXXParser(InputStream is, final URL url)
      throws XmlException, XMLStreamException {
      
      baseURL = url;

      xmlInputFactory = XMLInputFactory.newInstance();
      
//      xmlInputFactory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.TRUE);
      xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.TRUE);
      xmlInputFactory.setXMLResolver(new XMLResolver() {
	    public Object resolveEntity(String publicID,
					String systemID,
					String baseURI,
					String namespace)
	      throws XMLStreamException {
// 	      System.out.println("p: " + publicID);
// 	      System.out.println("s: " + systemID);
// 	      System.out.println("b: " + baseURI);
// 	      System.out.println("n: " + namespace);
	      try {
		if (systemID != null) {
		  if (baseURI != null) {
		    return new URL(new URL(baseURI), systemID).openStream();
		  }
		  else {
		    return new URL(ESXXParser.this.baseURL, systemID).openStream();
		  }
		}
		return null;
	      }
	      catch (MalformedURLException ex) {
		throw new XMLStreamException("Malformed URL for system ID " + 
					     systemID + ": " + ex.getMessage());
	      }
	      catch (IOException ex) {
		return null;
// 		throw new XMLStreamException("I/O error for system ID " +
// 					     systemID + ": " + ex.getMessage());
	      }
	    }
	});

      XMLStreamReader xsr = new StreamReaderDelegate(xmlInputFactory.createXMLStreamReader(is)) {
	    private boolean insideESXX = false;
	    private boolean insideSettings = false;

	    public int next()
	      throws XMLStreamException {
	      int rc = super.next();
	      
	      switch (rc) {
		case XMLStreamConstants.COMMENT: {
		  // Hide all comments
		  return next();
		}
		
		case XMLStreamConstants.START_ELEMENT: {
		  String namespace = getNamespaceURI();
		  String element   = getLocalName();
		  
		  if (namespace.equals(ESXX.NAMESPACE)) {
		    if (!insideESXX && element.equals("esxx")) {
		      insideESXX = true;
		      gotESXX = true;
		    }
		    else if (insideESXX && element.equals("settings")) {
		      insideSettings = true;
		    }
		    else if (insideSettings) {
		      if (element.equals("handler")) {
			handleHandler(this);
		      }
		      else if (element.equals("error-handler")) {
			handleErrorHandler(this);
		      }
		    }
		  }
		  break;
		}
		  
		case XMLStreamConstants.END_ELEMENT: {
		  String namespace = getNamespaceURI();
		  String element   = getLocalName();
		  
		  if (namespace.equals(ESXX.NAMESPACE)) {
		    if (insideSettings && element.equals("settings")) {
		      insideSettings = false;
		    }
		    else if (insideESXX && element.equals("esxx")) {
		      insideESXX = false;
		    }
		  }
		  break;
		}
		  
		case XMLStreamConstants.PROCESSING_INSTRUCTION: {
		  String target = getPITarget();

		  if (target.equals("esxx-stylesheet")) {
		    handleStylesheet(getPIData());
		    // Hide this PI
		    return next();
		  }
		  else if (target.equals("esxx-import")) {
		    handleImport(getPIData());
		    // Hide this PI
		    return next();
		  }
		  else if (target.equals("esxx")) {
		    handleCode(url, 0, getPIData());
		    // Hide this PI
		    return next();
		  }

		  break;
		}
	      }

	      return rc;
	    }
	};

      xml = XmlObject.Factory.parse(xsr);
      xsr.close();
    }


    public XmlObject getXML() {
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


    public String getErrorHandlerFunction() {
      return errorHandler;
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
	    BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
	    StringBuilder code = new StringBuilder();
	    String line;

	    while ((line = br.readLine()) != null) {
	      code.append(line);
	      code.append("\n");
	    }

	    handleCode(url, 1, code.toString());
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

    private void handleHandler(XMLStreamReader xsr) 
      throws XMLStreamException {
      String handler = xsr.getAttributeValue(null, "function").trim();

      if (handler.endsWith(")")) {
	throw new XMLStreamException("In <handler>: handler names " +
				     "should not include parentheses");
      }

      handlers.put(xsr.getAttributeValue(null, "type"), handler);
    }

    private void handleErrorHandler(XMLStreamReader xsr)
      throws XMLStreamException {
      String handler = xsr.getAttributeValue(null, "function").trim();

      if (handler.endsWith(")")) {
	throw new XMLStreamException("In <error-handler>: handler names " +
				     "should not include parentheses");
      }

      errorHandler = handler;
    }


    private URL baseURL;
    private XMLInputFactory xmlInputFactory;

    private boolean gotESXX;

    private XmlObject xml;
    private StringBuilder code = new StringBuilder();
    private URL stylesheet;
    private LinkedList<Code> codeList = new LinkedList<Code>();

    private TreeMap<String,String> handlers = new TreeMap<String,String>();
    private String errorHandler;
};
