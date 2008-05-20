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

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import javax.xml.stream.*;
//import javax.xml.xpath.*;
import org.esxx.js.JSGlobal;
import org.esxx.js.JSESXX;
import org.esxx.js.JSURI;
import org.esxx.util.RequestMatcher;
import org.esxx.util.SyslogHandler;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.w3c.dom.*;

import net.sf.saxon.s9api.*;
import net.sf.saxon.dom.*;

/** This class is responsible for parsing the XML file the web server
  * invokes ESXX with. The XML file may include ESXX-specific
  * processing instructions or elements from the ESXX namespace, which
  * will be interpreted.
  */

public class Application {
    public Application(ESXX esxx, Request request)
      throws IOException {

      this.esxx = esxx;
      baseURL = request.getURL();
      workingDirectory = request.getWD();
      ident = baseURL.getPath().replaceAll("^.*/", "").replaceAll("\\.[^.]*", "");

      try {
	workingDirectory = new File("").toURI().toURL();
      }
      catch (java.net.MalformedURLException ex) {
	throw new IOException("Unable to get current working directory as an URI: "
			      + ex.getMessage(), ex);
      }

      xmlInputFactory = XMLInputFactory.newInstance();

      InputStream is = esxx.openCachedURL(baseURL);

      // Check if it's an XML document or a JS file

      if (!is.markSupported()) {
	is = new BufferedInputStream(is);
      }

      is.mark(4096);

      if (is.read() == '#' &&
	  is.read() == '!') {
	// Skip shebang
	while (is.read() != '\n') {}
	importCode(baseURL, is);
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
	    importCode(baseURL, is);
	    return;
	  }
	}
      }

      is.reset();

      // Load and parse the XML document

      try {
	xml = esxx.parseXML(is, baseURL, externalURLs, null);

	// Extract ESXX information, if any

	try {
	  Processor processor = esxx.getSaxonProcessor();

	  XPathCompiler xc = processor.newXPathCompiler();
	  xc.declareNamespace("esxx", ESXX.NAMESPACE);

	  XPathSelector xs = xc.compile("//processing-instruction() | " +
					"//esxx:esxx/esxx:handlers/esxx:*").load();
	  xs.setContextItem(processor.newDocumentBuilder().wrap(xml));

	  for (XdmItem i : xs) {
	    Node n = (Node) ((NodeWrapper) i.getUnderlyingValue()).getUnderlyingNode();

	    if (n.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
	      String name = n.getNodeName();

	      if (name.equals("esxx-stylesheet")) {
		handleStylesheet(n.getNodeValue());
		n.getParentNode().removeChild(n);
	      }
	      else if (name.equals("esxx-include")) {
		handleImport(n.getNodeValue());
		n.getParentNode().removeChild(n);
	      }
	      else if (name.equals("esxx")) {
		addCode(baseURL, 0, n.getNodeValue());
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
	catch (SaxonApiException ex) {
	  // Should never happen
	  ex.printStackTrace();
	  throw new ESXXException("SaxonApiException: " + ex.getMessage(), ex);
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

    public synchronized Logger getLogger() {
      if (logger == null) {
	String name  = Application.class.getName() + "." + ident;

	logger = Logger.getLogger(name);

	if (logger.getHandlers().length == 0) {
	  try {
	    // No specific log handler configured in
	    // jre/lib/logging.properties -- log to syslog
	    logger.addHandler(new SyslogHandler("ESXX"));
	  }
	  catch (UnsupportedOperationException ex) {
	    // Never mind
	  }
	}
      }

      return logger;
    }

    public String getAppName() {
      return ident;
    }

    public Scriptable getMainDocument() {
      return mainDocument;
    }

    public JSURI getMainURI() {
      return mainURI;
    }

    public URL getWD() {
      return workingDirectory;
    }

    public Scriptable getIncludePath() {
      return includePath;
    }

    public void setIncludePath(Scriptable paths) {
      includePath = paths;
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

      // Create JS versions of the document, it's URI and the include path
      mainDocument = ESXX.domToE4X(xml, cx, applicationScope);
      mainURI = (JSURI) cx.newObject(applicationScope, "URI", new Object[] { baseURL });
      URL[] include_path = esxx.getIncludePath();

      includePath = cx.newArray(applicationScope, include_path.length);

      for (int i = 0; i < include_path.length; ++i) {
	includePath.put(i, includePath, cx.newObject(applicationScope, "URI",
							     new Object[] { include_path[i] }));
      }

      return applicationScope;
    }

    public void execute(Context cx, Scriptable scope, JSESXX js_esxx) {
      if (!hasExecuted) {
	hasExecuted = true;

	for (Code c : codeList.values()) {
	  if (!c.hasExecuted) {
	    c.hasExecuted = true;
	    JSURI old_uri = js_esxx.setLocation(cx, scope, c.url);
	    c.code.exec(cx, scope);
	    js_esxx.setLocation(old_uri);
	  }
	}
      }
    }

    public void importAndExecute(Context cx, Scriptable scope, JSESXX js_esxx,
				 URL url, InputStream is)
      throws IOException {
      Code c = importCode(url, is);

      if (c.code == null) {
	c.code = cx.compileString(c.source, c.url.toString(), c.line, null);
      }

      if (!c.hasExecuted) {
	c.hasExecuted = true;
	JSURI old_uri = js_esxx.setLocation(cx, scope, c.url);
	c.code.exec(cx, scope);
	js_esxx.setLocation(old_uri);
      }
    }


    public static class Code {
	public Code(URL u, int l, String s) {
	  url = u;
	  line = l;
	  source = s;
	  code = null;
	  hasExecuted = false;
	}

	@Override
	public String toString() {
	  return url.toString() + "::" + line + ": " + code;
	}

	public URL url;
	public int line;
	public String source;
	public Script code;
        public boolean hasExecuted;
    };


    private Code importCode(URL url)
      throws IOException {
      return importCode(url, esxx.openCachedURL(url));
    }

    private Code importCode(URL url, InputStream is)
      throws IOException {
      try {
	String key = url.toURI().normalize().toString();
	Code     c = codeList.get(key);

	if (c == null) {
	  ByteArrayOutputStream os = new ByteArrayOutputStream();

	  ESXX.copyStream(is, os);
	  c = addCode(url, 1, os.toString());
	}

	return c;
      }
      catch (URISyntaxException ex) {
	throw new IOException("Unable to include " + url + ": " + ex.getMessage(), ex);
      }
    }

    private Code addCode(URL url, int line, String data)
      throws IOException {
      try {
	Code c = new Code(url, line, data);
	codeList.put(url.toURI().normalize().toString(), c);
	externalURLs.add(url);

	return c;
      }
      catch (URISyntaxException ex) {
	throw new IOException("Unable to include " + url + ": " + ex.getMessage(), ex);
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
	new StringReader("<esxx-include " + data + "/>"));

      while (xsr.hasNext()) {
	if (xsr.next() == XMLStreamConstants.START_ELEMENT) {
	  String href = xsr.getAttributeValue(null, "href");

	  if (href == null) {
	    throw new ESXXException("<?esxx-include?> attribute 'href' " +
				    "must be specified");
	  }

	  try {
	    importCode(new URL(baseURL, href));
	  }
	  catch (MalformedURLException ex) {
	    throw new ESXXException("<?esxx-include?> attribute 'href' is invalid: " +
				    ex.getMessage(), ex);
	  }
	  catch (IOException ex) {
	    throw new ESXXException("<?esxx-include?> failed to include document: " +
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

    private ESXX esxx;
    private URL baseURL;
    private HashSet<URL> externalURLs = new HashSet<URL>();
    private URL workingDirectory;

    private String ident;
    private Logger logger;

    private JSGlobal applicationScope = null;
    private Scriptable mainDocument;
    private JSURI mainURI;
    private Scriptable includePath;
    private boolean hasExecuted = false;

    private boolean gotESXX = false;

    private Document xml;
    private LinkedHashMap<String, Code> codeList = new LinkedHashMap<String, Code>();

    private RequestMatcher requestMatcher = new RequestMatcher();
    private Map<String,String> soapActions  = new HashMap<String,String>();
    private Map<String,URL>    stylesheets  = new HashMap<String,URL>();
    private String errorHandler;
};
