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
import java.net.URL;
import java.util.*;
import javax.xml.stream.*;
import javax.xml.xpath.*;
import org.blom.martin.esxx.js.JSGlobal;
import org.blom.martin.esxx.js.JSESXX;
import org.blom.martin.esxx.js.JSRequest;
import org.blom.martin.esxx.js.JSURI;
import org.blom.martin.esxx.util.RequestMatcher;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.w3c.dom.*;

/** This class is responsible for parsing the XML file the web server
  * invokes ESXX with. The XML file may include ESXX-specific
  * processing instructions or elements from the ESXX namespace, which
  * will be interpreted. 
  */

public class Application {
    public static class Code {
	public Code(URL u, int l, String s) {
	  url = u;
	  line = l;
	  source = s;
	  code = null;
	  hasExecuted = false;
	}

	public String toString() {
	  return url.toString() + "::" + line + ": " + code;
	}

	public URL url;
	public int line;
	public String source;
	public Script code;
        public boolean hasExecuted;
    };

    public Application(ESXX esxx, URL url)
      throws IOException {
      
      esxxObject = esxx;
      baseURL = url;
      xmlInputFactory = XMLInputFactory.newInstance();

      InputStream is = esxxObject.openCachedURL(url);

      // Check if it's an XML document or a JS file

      if (!is.markSupported()) {
	is = new BufferedInputStream(is);
      }

      is.mark(4096);

      if (is.read() == '#' &&
	  is.read() == '!') {
	// Skip shebang
	while (is.read() != '\n');
	importCode(url, is);
	return;
      }
      else {
	is.reset();

	for (int i = 0; i < 4096; ++i) {
	  int c = is.read();
	    
	  if (c == '<') {
	    // '<' triggers XML mode
	    break;
	  }
	  else if (!Character.isWhitespace(c)) {
	    // Any other character except blanks triggers direct JS-mode
	    is.reset();
	    importCode(url, is);
	    return;
	  }
	}
      }

      is.reset();

      // Load and parse the XML document

      try {
	xml = esxxObject.parseXML(is, url, externalURLs, null);

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

	  NodeList r = (NodeList) xpath.evaluate("//processing-instruction() | " +
						 "//esxx:esxx/esxx:handlers/esxx:*", 
						 xml, XPathConstants.NODESET);
	  
	  for (int i = 0; i < r.getLength(); ++i) {
	    Node n = r.item(i);
	    
	    if (n.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
	      String name = n.getNodeName();

	      if (name.equals("esxx-stylesheet")) {
		handleStylesheet(n.getNodeValue());
		n.getParentNode().removeChild(n);
	      }
	      else if (name.equals("esxx-import")) {
		handleImport(n.getNodeValue());
		n.getParentNode().removeChild(n);
	      }
	      else if (name.equals("esxx")) {
		addCode(url, 0, n.getNodeValue());
		n.getParentNode().removeChild(n);
	      }
	    }
	    else if (n.getNodeType() == Node.ELEMENT_NODE) {
	      Element e = (Element) n;
	      String name = e.getLocalName();

	      // esxx/handlers/* matched.
	      gotESXX = true;

	      if (name.equals("http")) {
		handleHTTP(e);
	      }
	      else if (name.equals("soap")) {
		handleSOAP(e);
	      }
	      else if (name.equals("stylesheet")) {
		handleStylesheet(e);
	      }
	      else if (name.equals("error")) {
		handleErrorHandler(e);
	      }
	    }
	  }
	}
	catch(XPathExpressionException ex) {
	  // Should never happen
	  ex.printStackTrace();
	  throw new ESXXException("XPathExpressionException: " + ex.getMessage());
	}
      }
      catch (XMLStreamException ex) {
	throw new ESXXException("XMLStreamException: " + ex.getMessage(), ex);
      }
      catch (DOMException ex) {
	throw new ESXXException("DOMException: " + ex.getMessage(), ex);
      }
    }

    public Collection<URL> getExternalURLs() {
      return externalURLs;
    }

    public Document getXML() {
      return xml;
    }

    public URL getBaseURL() {
      return baseURL;
    }

    public Collection<Code> getCodeList() {
      return codeList.values();
    }

    public URL getStylesheet(String media_type) {
      return stylesheets.get(media_type);
    }

    public boolean hasHandlers() {
      return gotESXX;
    }

    public RequestMatcher.Match getHandlerFunction(String http_method, String path_info,
						   Context cx, Scriptable scope) {
      return requestMatcher.matchRequest(http_method, path_info, cx, scope);
    }

    public String getSOAPAction(String action) {
      return soapActions.get(action);
    }

    public String getErrorHandlerFunction() {
      return errorHandler;
    }

    public JSGlobal compile(Context cx)
      throws IllegalAccessException, InstantiationException, 
      java.lang.reflect.InvocationTargetException {
      if (applicationScope != null) {
	return applicationScope;
      }

      // Compile uri-matching regex pattern
      requestMatcher.compile();

      // Create per-application top-level and global scopes
      applicationScope = new JSGlobal(cx);

      for (Code c : codeList.values()) {
	c.code = cx.compileString(c.source, c.url.toString(), c.line, null);
      }

      return applicationScope;
    }

    public void execute(Context cx, Scriptable scope, JSESXX js_esxx) {
      if (!hasExecuted) {
	for (Code c : codeList.values()) {
	  if (!c.hasExecuted) {
	    JSURI old_uri = js_esxx.jsGet_location();
	    js_esxx.setLocation(cx, scope, c.url);
	    c.code.exec(cx, scope);
	    c.hasExecuted = true;
	    js_esxx.setLocation(old_uri);
	  }
	}

	hasExecuted = true;
      }
    }

    public void importAndExecute(Context cx, Scriptable scope, JSESXX js_esxx, URL url)
      throws IOException {
      Code c = importCode(url);

      if (c.code == null) {
	c.code = cx.compileString(c.source, c.url.toString(), c.line, null);
      }

      if (!c.hasExecuted) {
	JSURI old_uri = js_esxx.jsGet_location();
	js_esxx.setLocation(cx, scope, c.url);
	c.code.exec(cx, scope);
	c.hasExecuted = true;
	js_esxx.setLocation(old_uri);
      }
    }


    private Code importCode(URL url) 
      throws IOException {
      return importCode(url, esxxObject.openCachedURL(url));
    }

    private Code importCode(URL url, InputStream is) 
      throws IOException {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      
      ESXX.copyStream(is, os);
      return addCode(url, 1, os.toString());
    }

    private Code addCode(URL url, int line, String data) {
      String key = url.toString();
      Code     c = codeList.get(key);

      if (c == null) {
	c = new Code(url, line, data);
	codeList.put(url.toString(), c);
	externalURLs.add(url);
      }

      return c;
    }

    private void handleStylesheet(String data)
      throws XMLStreamException {

      XMLStreamReader xsr = xmlInputFactory.createXMLStreamReader(
	new StringReader("<esxx-stylesheet " + data + "/>"));

      while (xsr.hasNext()) {
	if (xsr.next() == XMLStreamConstants.START_ELEMENT) {
	  String type = xsr.getAttributeValue(null, "type");
	  if (type == null || !type.equals("text/xsl")) {
	    throw new ESXXException("<?esxx-stylesheet?> attribute 'type' " +
				    "must be set to 'text/xsl'");
	  }

	  String href = xsr.getAttributeValue(null, "href");

	  if (href == null) {
	    throw new ESXXException("<?esxx-stylesheet?> attribute 'href' " +
				    "must be specified");
	  }

	  try {
	    URL url = new URL(baseURL, href);
	    stylesheets.put("", url);
	    externalURLs.add(url);
	  }
	  catch (MalformedURLException ex) {
	    throw new ESXXException("<?esxx-stylesheet?> attribute 'href' is invalid: " +
				    ex.getMessage());
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
	    throw new ESXXException("<?esxx-import?> attribute 'href' " +
				    "must be specified");
	  }

	  try {
	    importCode(new URL(baseURL, href));
	  }
	  catch (MalformedURLException ex) {
	    throw new ESXXException("<?esxx-import?> attribute 'href' is invalid: " +
				    ex.getMessage(), ex);
	  }
	  catch (IOException ex) {
	    throw new ESXXException("<?esxx-import?> failed to include document: " +
				    ex.getMessage(), ex);
	  }
	}
      }
      
      xsr.close();
    }

    private void handleHTTP(Element e) {
      String method  = e.getAttributeNS(null, "method").trim();
      String uri     = e.getAttributeNS(null, "uri").trim();
      String handler = e.getAttributeNS(null, "handler").trim();

      if (method.equals("")) {
	throw new ESXXException("<http> attribute 'method' must " +
				"must be specified");
      }

      if (handler.equals("")) {
	throw new ESXXException("<http> attribute 'handler' must " +
				"must be specified");
      }

      if (handler.endsWith(")")) {
	throw new ESXXException("<http> attribute 'handler' value " +
				"should not include parentheses");
      }

      requestMatcher.addRequestPattern(method, uri, handler);
    }

    private void handleSOAP(Element e) {
      String object = e.getAttributeNS(null, "object").trim();

      if (object.equals("")) {
	throw new ESXXException("<soap> attribute 'object' must " +
				"must be specified");
      }

      soapActions.put(e.getAttributeNS(null, "action"), object);
    }

    private void handleStylesheet(Element e) {
      String media_type = e.getAttributeNS(null, "media-type").trim();
      String href      = e.getAttributeNS(null, "href").trim();
      String type      = e.getAttributeNS(null, "type").trim();
      
      if (href.equals("")) {
	throw new ESXXException("<stylesheet> attribute 'href' " +
				"must be specified");
      }

      if (!type.equals("") && !type.equals("text/xsl")) {
	throw new ESXXException("<stylesheet> attribute 'type' " +
				"must be set to 'text/xsl'");
      }

      try {
	URL url = new URL(baseURL, href);
	stylesheets.put(media_type, url);
	externalURLs.add(url);
      }
      catch (MalformedURLException ex) {
	throw new ESXXException("<stylesheet> attribute 'href' is invalid: " +
				ex.getMessage());
      }
    }

    private void handleErrorHandler(Element e) {
      String handler = e.getAttributeNS(null, "handler").trim();

      if (handler.endsWith(")")) {
	throw new ESXXException("<error> attribute 'handler' value " +
				"should not include parentheses");
      }

      errorHandler = handler;
    }

    private XMLInputFactory xmlInputFactory;

    private ESXX esxxObject;
    private URL baseURL;
    private LinkedList<URL> externalURLs = new LinkedList<URL>();

    private JSGlobal applicationScope = null;
    private boolean hasExecuted = false;

    private boolean gotESXX = false;

    private Document xml;
    private StringBuilder code = new StringBuilder();
    private LinkedHashMap<String, Code> codeList = new LinkedHashMap<String, Code>();

    private RequestMatcher requestMatcher = new RequestMatcher();
    private Map<String,String> soapActions  = new HashMap<String,String>();
    private Map<String,URL>    stylesheets  = new HashMap<String,URL>();
    private String errorHandler;
};
