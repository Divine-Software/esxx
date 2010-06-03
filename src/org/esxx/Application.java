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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.esxx.js.*;
import org.esxx.util.*;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.WrappedException;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.ModuleScript;
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider;
import org.w3c.dom.*;

import net.sf.saxon.s9api.*;
import net.sf.saxon.dom.*;

/** This class is responsible for parsing the XML file the web server
  * invokes ESXX with. The XML file may include ESXX-specific
  * processing instructions or elements from the ESXX namespace, which
  * will be interpreted.
  */

public class Application
  implements org.esxx.cache.Cached {
  public Application(Context cx, Request request)
    throws IOException {
    esxx = ESXX.getInstance();

    baseURI           = request.getScriptFilename();
    workingDirectory  = request.getWD();
    ident             = (baseURI.getSchemeSpecificPart()
			 .replaceAll("^.*/", "").replaceAll("\\.[^.]*", ""));
    started           = new Date();

    setCurrentLocation(baseURI);
    loadMainFile(cx);
    compileAndInitialize(cx);
    setCurrentLocation(null);
  }

  @Override public String toString() {
    return "[" + this.getClass().getName() + ": " + baseURI + "]";
  }

  public JSGlobal getJSGlobal() {
    return applicationScope;
  }

  public JSESXX getJSESXX() {
    return jsESXX;
  }

  public synchronized JSLogger getJSAppLogger(Context cx) {
    if (jsLogger == null) {
      jsLogger = JSLogger.newJSLogger(cx, this);
    }

    return jsLogger;
  }

  public synchronized JSURI getJSCurrentLocation(Context cx) {
    if (jsCurrentLocation == null && currentLocation != null) {
      jsCurrentLocation = (JSURI) cx.newObject(applicationScope, "URI", 
					       new Object[] { currentLocation });
    }

    return jsCurrentLocation;
  }

  public synchronized JSLRUCache getPLS(Context cx) {
    if (cache == null) {
      cache = JSLRUCache.newJSLRUCache(cx, this);
    }

    return cache;
  }

  public void clearPLS() {
    if (cache != null) {
      cache.jsFunction_clear();
    }
  }

  public JSLRUCache getTLS(Context cx) {
    TLS tls = (TLS) cx.getThreadLocal(TLS.class);

    if (tls == null) {
      tls = new TLS();
      cx.putThreadLocal(TLS.class, tls);
    }

    JSLRUCache cache = tls.caches.get(this);

    if (cache == null) {
      cache = JSLRUCache.newJSLRUCache(cx, this);
      tls.caches.put(this, cache);
    }

    return cache;
  }

  public static void clearTLS(Context cx) {
    TLS tls = (TLS) cx.getThreadLocal(TLS.class);

    if (tls != null) {
      for (JSLRUCache c : tls.caches.values()) {
	c.jsFunction_clear();
      }
    }
  }

  public JSResponse executeSOAPAction(Context cx, JSRequest req,
				      String soap_action, String path_info)
    throws Exception {
    Object result;
    RequestMatcher.Match match = soapMatcher.matchRequest(soap_action, path_info,
							  cx, applicationScope);

    if (match == null) {
      throw new ESXXException(404, "'" + soap_action + "' SOAP action object not defined for URI "
			      + "'" + path_info + "'");
    }

    req.setArgs(match.params);

    String object = match.handler;

    javax.xml.soap.SOAPMessage message = (javax.xml.soap.SOAPMessage) req.jsGet_message();

    if (!object.equals("")) {
      // RPC style SOAP handler

      org.w3c.dom.Node    soap_header = null;
      org.w3c.dom.Element soap_body;

      try {
	soap_header = message.getSOAPHeader();
      }
      catch (javax.xml.soap.SOAPException ex) {
	// The header is optional
      }

      soap_body = message.getSOAPBody().extractContentAsDocument().getDocumentElement();

      Object args[] = { req,
			ESXX.domToE4X(soap_body, cx, applicationScope),
			ESXX.domToE4X(soap_header, cx, applicationScope) };

      String prefix = soap_body.getPrefix();
      String nsuri  = soap_body.getNamespaceURI();
      String method = soap_body.getLocalName();

      try {
	result = JS.callJSMethod(object, method, args, "SOAP handler", cx, applicationScope);

	// Don't wrap result yet, but do check for null/undefined
	if (result == null || result == Context.getUndefinedValue()) {
	  throw new ESXXException("No result from '" + getAppName() + "'");
	}
      }
      catch (Exception ex) {
	result = executeErrorHandler(cx, req, ex);
      }

      // Automatically add a SOAP-Envelope, if missing. The generated
      // envelope is based on the request envelope.
      if (result instanceof org.mozilla.javascript.xml.XMLObject) {
	result = ESXX.e4xToDOM((Scriptable) result);
      }

      if (result instanceof org.w3c.dom.Node) {
	org.w3c.dom.Node node = (org.w3c.dom.Node) result;

	if (!node.getLocalName().equals("Envelope")) {
	  // Convert Envelope to a response
	  javax.xml.soap.SOAPPart     sp = message.getSOAPPart();
	  javax.xml.soap.SOAPEnvelope se = sp.getEnvelope();

	  if (se.getHeader() != null) {
	    se.getHeader().detachNode();
	  }

	  if (se.getBody() != null) {
	    se.getBody().detachNode();
	  }

	  // Add result to the now empty SOAP Envelope
	  javax.xml.soap.SOAPBody        sb = se.addBody();
	  javax.xml.soap.SOAPBodyElement be = sb.addBodyElement(se.createName(method + "Response",
									      prefix, nsuri));
	  Document sd = be.getOwnerDocument();
	  node = sd.adoptNode((org.w3c.dom.Node) result);
	  if (node == null) {
	    node = sd.importNode((org.w3c.dom.Node) result, true);
	  }

	  be.appendChild(node);
	  result = se;
	}
      }
    }
    else {
      // No RPC handler; the SOAP message itself is the result

      result = ESXX.domToE4X(message.getSOAPPart(), cx, applicationScope);
    }

    return wrapResult(cx, result);
  }

  public JSResponse executeHTTPMethod(final Context cx, final JSRequest req,
				      final String request_method, final String path_info)
    throws Exception {
    final RequestMatcher.Match match = requestMatcher.matchRequest(request_method, path_info,
								   cx, applicationScope);

    if (match == null) {
      throw new ESXXException(404, "'" + request_method + "' handler not defined for URI "
			      + "'" + path_info + "'");
    }

    req.setArgs(match.params);

    HandlerCallback hcb = new HandlerCallback() {
	public JSResponse execute(JSRequest req)
	  throws Exception {
	  JSResponse result;
	  Object args[] = { req };

	  try {
	    result = wrapResult(cx, JS.callJSMethod(match.handler, args, 
						    "'" + request_method + "' handler",
						    cx, applicationScope));
	  }
	  catch (Exception ex) {
	    result = executeErrorHandler(cx, req, ex);
	  }

	  return result;
	}
      };

    if (hasFilters()) {
      return new FilterFunction(hcb, req, request_method, path_info).execute(cx);
    }
    else {
      return hcb.execute(req);
    }
  }

  public void executeExitHandler(Context cx) {
    String handler = getExitHandlerFunction();

    if (handler != null) {
      Object args[] = { };

      JS.callJSMethod(handler, args, "Exit handler", cx, applicationScope);
    }
  }

  public JSResponse executeErrorHandler(Context cx, JSRequest req,
					Exception error)
    throws Exception {

    if (!(error instanceof RhinoException) &&
	!(error instanceof ESXXException)) {
      // Never invoke error handler for "foreign" exceptions
      throw error;
    }

    Throwable cause = error;

    if (cause instanceof WrappedException) {
      // Unwrap wrapped exceptions
      cause = ((WrappedException) cause).getWrappedException();
    }

    if (cause instanceof ESXXException.TimeOut) {
      // Never handle this exception, wrapped or not
      throw (ESXXException.TimeOut) cause;
    }

    Object result  = null;
    String handler = getErrorHandlerFunction();

    if (handler != null) {
      try {
	Object args[] = { req, Context.javaToJS(cause, applicationScope) };

	result = JS.callJSMethod(handler, args, "Error handler", cx, applicationScope);
      }
      catch (Exception ex) {
	throw new ESXXException("Failed to handle error '" + cause.toString() +
				"':\n" +
				"Error handler '" + handler +
				"' failed with message '" +
				ex.getMessage() + "'",
				ex);
      }
    }

    if (result == null || result == Context.getUndefinedValue()) {
      if (cause instanceof Exception) {
	throw (Exception) cause;
      }
      else {
	// Throw original WrappedException
	throw error;
      }
    }

    return wrapResult(cx, result);
  }

  public JSResponse executeFilter(Context cx, JSRequest req, Function next, String filter)
    throws Exception {
    JSResponse result;
    Object args[] = { req, next };

    try {
      result = wrapResult(cx, JS.callJSMethod(filter, args, 
					      "'" + filter + "' filter", 
					      cx, applicationScope));
    }
    catch (Exception ex) {
      result = executeErrorHandler(cx, req, ex);
    }

    return result;
  }


  public synchronized boolean enter() {
    if (terminated) {
      return false;
    }

    ++enterCount;

    return true;
  }

  public synchronized boolean terminate(long timeout) {
    // Cancel all timers
    for (TimerHandler th : timerHandlers) {
      if (th.future != null) {
	th.future.cancel(false);
      }
    }

    // Prevent new requests from enter()-ing
    terminated = true;

    try {
      while (enterCount > 0 && timeout > 0) {
	long t = Math.min(100, timeout);
	timeout -= t;
	this.wait(t);
      }
    }
    catch (InterruptedException ex) {
      // Preserve status
      Thread.currentThread().interrupt();
    }

    return enterCount == 0;
  }

  public synchronized void exit() {
    if (enterCount == 0) {
      throw new IllegalStateException("enterCount becomes negative!");
    }

    --enterCount;
    this.notify();
  }

  @Override /* Cached */ public synchronized void logUsage(long start_time) {
    ++invocations;
    lastAccessed  = System.currentTimeMillis();

    if (start_time != 0) {
      executionTime += (lastAccessed - start_time);
    }
  }

  public String getAppName() {
    return ident;
  }

  @Override /* Cached */ public String getFilename() {
    return baseURI.toString();
  }

  public synchronized Logger getAppLogger() {
    if (logger == null) {
      logger = esxx.createLogger(Application.class.getName() + "." + getAppName(),
				 Level.ALL,
				 "esxx");
    }

    return logger;
  }

  @Override /* Cached */ public Collection<URI> getExternalURIs() {
    return externalURIs;
  }

  @Override /* Cached */ public synchronized JMXBean getJMXBean() {
    if (jmxBean == null) {
      jmxBean = new JMXBean();
    }

    return jmxBean;
  }

  public Scriptable getMainDocument() {
    return mainDocument;
  }

  public void setMainDocument(Scriptable doc) {
    mainDocument = doc;
  }

  public JSURI getMainURI() {
    return mainURI;
  }

  public synchronized URI getCurrentLocation() {
    return currentLocation;
  }

  public synchronized URI getWorkingDirectory() {
    return workingDirectory;
  }

  public synchronized JSURI getJSWorkingDirectory(Context cx) {
    if (jsWorkingDirectory == null) {
      jsWorkingDirectory = JSURI.newJSURI(cx, this, workingDirectory);
    }

    return jsWorkingDirectory;
  }

  public synchronized URI setCurrentLocation(URI uri) {
    URI res = currentLocation;
    currentLocation    = uri;
    jsCurrentLocation  = null;
    return res;
  }

  public synchronized URI setWorkingDirectory(URI wd) {
    URI res = workingDirectory;
    workingDirectory   = wd;
    jsWorkingDirectory = null;
    return res;
  }

  public Scriptable getIncludePath() {
    return includePath;
  }

  public void setIncludePath(Scriptable paths) {
    includePath = paths;
  }

  public URI resolveURI(Context cx, URI file, URI base) {
    URI uri = null;
    InputStream is = null;

    // If base is null and if location is set, resolve files relative
    // the current JS file. Then try to resolve files relative the working
    // directory. Finally, try the include path.

    if (base == null) {
      base = getCurrentLocation();
    }

    if (base == null) {
      base = getWorkingDirectory();
    }

    try {
      uri = base.resolve(file);
      is  = esxx.openCachedURI(uri);
    }
    catch (IOException ignored) {}

    if (is == null) {
      // Failed to resolve URL relative the current file's
      // location -- try the include path

      for (Object path : cx.getElements(includePath)) {
	try {
	  uri = ((JSURI) path).getURI().resolve(file);
	  is  = esxx.openCachedURI(uri);
	  is.close();
	  break; // On success, break
	}
	catch (IOException ignored) { /* Try next */ }
      }

      if (is == null) {
	throw Context.reportRuntimeError("File '" + file + "' not found.");
      }
    }
    
    return uri;
  }

  public ESXXScript resolveScript(Context cx, URI file, URI base) 
    throws IOException {

    URI uri = resolveURI(cx, file, base);
    InputStream is = esxx.openCachedURI(uri);

    try {
      return addScript(cx, new InputStreamReader(is), uri, 1);
    }
    finally {
      is.close();
    }
  }

  public URI getStylesheet(Context cx, String media_type, String path_info) {
    try {
      RequestMatcher.Match match = xsltMatcher.matchRequest(media_type, path_info,
							    cx, applicationScope);
      return match == null ? null : new URI(match.handler);
    }
    catch (URISyntaxException ex) {
      throw new ESXXException("Stylesheet 'href' is invalid: " +
			      ex.getMessage());
    }

  }

  public boolean hasHandlers() {
    return hasHTTPHandlers() || hasSOAPHandlers();
  }

  public boolean hasHTTPHandlers() {
    return gotHTTPHandlers;
  }

  public boolean hasFilters() {
    return gotFilters;
  }

  public boolean hasSOAPHandlers() {
    return gotSOAPHandlers;
  }

  public String getErrorHandlerFunction() {
    return errorHandler;
  }

  public String getExitHandlerFunction() {
    return exitHandler;
  }

  public JSResponse wrapResult(Context cx, Object result) {
    if (result == null || result == Context.getUndefinedValue()) {
      throw new ESXXException("No result from '" + getAppName() + "'");
    }
    else if (result instanceof JSResponse) {
      return (JSResponse) result;
    }
    else if (result instanceof NativeArray) {
      // Automatically convert an JS Array into a Response
      return (JSResponse) JSESXX.newObject(cx, applicationScope, "Response",
					   cx.getElements((NativeArray) result));
    }
    else if (result instanceof Number) {
      return (JSResponse) JSESXX.newObject(cx, applicationScope, "Response",
					   new Object[] { result, null, null, null });
    }
    else {
      return (JSResponse) JSESXX.newObject(cx, applicationScope, "Response",
					   new Object[] { 200, null, result, null });
    }
  }

  private void loadMainFile(Context cx)
    throws IOException {
    InputStream is = esxx.openCachedURI(baseURI);

    try {
      externalURIs.add(baseURI);

      // Check if it's an XML document or a JS file

      if (!is.markSupported()) {
	is = new BufferedInputStream(is);
      }

      is.mark(4096);

      if (is.read() == '#' &&
	  is.read() == '!') {
	while (is.read() != '\n') { /* Skip shebang */ }
	addScript(cx, new InputStreamReader(is), baseURI, 2);
      }
      else {
	int c;

	is.reset();
	while (Character.isWhitespace(c = is.read())) { /* Skip WS */ }
	is.reset();

	if (c == '<') {
	  // '<' triggers XML mode
	  loadESXXFile(cx, is);
	}
	else if (!Character.isWhitespace(c)) {
	  // Any other character triggers direct JS-mode
	  addScript(cx, new InputStreamReader(is), baseURI, 1);
	}
      }
    }
    finally {
      is.close();
    }
  }

  private void loadESXXFile(Context cx, InputStream is)
    throws IOException {
    try {
      xml = esxx.parseXML(is, baseURI, externalURIs, null);

      // Extract ESXX information, if any

      Processor processor = esxx.getSaxonProcessor();

      XPathCompiler xc = processor.newXPathCompiler();
      xc.declareNamespace("esxx", ESXX.NAMESPACE);

      XPathSelector xs = xc.compile("//processing-instruction() | " +
				    "//esxx:esxx/esxx:handlers/esxx:* | " + 
				    "//esxx:esxx/esxx:filters/esxx:filter").load();
      xs.setContextItem(esxx.getSaxonDocumentBuilder().wrap(xml));

      int esxx_pi_cnt = 0;

      for (XdmItem i : xs) {
	Node n = (Node) ((NodeWrapper) i.getUnderlyingValue()).getUnderlyingNode();

	if (n.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
	  String name = n.getNodeName();

	  if (name.equals("esxx-stylesheet")) {
	    handleStylesheetPI(n.getNodeValue());
	    n.getParentNode().removeChild(n);
	  }
	  else if (name.equals("esxx-include")) {
	    handleImportPI(cx, n.getNodeValue());
	    n.getParentNode().removeChild(n);
	  }
	  else if (name.equals("esxx")) {
	    ++esxx_pi_cnt;
	    addScript(cx, new StringReader(n.getNodeValue()),
		      baseURI.resolve("#inline-" + esxx_pi_cnt), 1);
	    n.getParentNode().removeChild(n);
	  }
	}
	else if (n.getNodeType() == Node.ELEMENT_NODE) {
	  Element e = (Element) n;
	  String name = e.getLocalName();

	  if (name.equals("http")) {
	    // esxx/handlers/http matched.
	    gotHTTPHandlers = true;
	    handleHTTPHandler(e);
	  }
	  else if (name.equals("soap")) {
	    // esxx/handlers/soap matched.
	    gotSOAPHandlers = true;
	    handleSOAPHandler(e);
	  }
	  else if (name.equals("timer")) {
	    handleTimerHandler(e);
	  }
	  else if (name.equals("stylesheet")) {
	    handleStylesheet(e);
	  }
	  else if (name.equals("error")) {
	    handleErrorHandler(e);
	  }
	  else if (name.equals("exit")) {
	    handleExitHandler(e);
	  }
	  else if (name.equals("filter")) {
	    gotFilters = true;
	    handleFilter(e);
	  }
	}
      }
    }
    catch (SaxonApiException ex) {
      // Should never happen
      ex.printStackTrace();
      throw new ESXXException("SaxonApiException: " + ex.getMessage(), ex);
    }
    catch (DOMException ex) {
      throw new ESXXException("DOMException: " + ex.getMessage(), ex);
    }
  }

  private synchronized ESXXScript addScript(Context cx, Reader r, URI uri, int line) 
    throws IOException {
    String     key = uri.toString();
    ESXXScript es  = scriptList.get(key);

    if (es == null) {
      es = new ESXXScript(cx.compileReader(r, uri.toString(), line, null), uri);
      scriptList.put(key, es);
    }

    externalURIs.add(uri);
    return es;
  }

  private void compileAndInitialize(Context cx) {
    try {
      // Compile uri-matching regex patterns
      soapMatcher.compile();
      requestMatcher.compile();
      xsltMatcher.compile();

      // Create per-application top-level and global scopes
      applicationScope = new JSGlobal(cx);

      // Create JS versions of the document, it's URI and the include path
      mainDocument = ESXX.domToE4X(xml, cx, applicationScope);
      mainURI = (JSURI) cx.newObject(applicationScope, "URI", new Object[] { baseURI });

      URI[] include_path = esxx.getIncludePath();
      includePath = cx.newArray(applicationScope, include_path.length);

      for (int i = 0; i < include_path.length; ++i) {
	includePath.put(i, includePath, cx.newObject(applicationScope, "URI",
						     new Object[] { include_path[i] }));
      }

      // Make the JSESXX object available as "esxx" in the global
      // scope, so the set-up code has access to it. This call returns
      // the old esxx variable, if already present.
      jsESXX = applicationScope.createJSESXX(cx, this);

      // Make the CommonJS function 'require' available in the global scope
      Require require = new Require(cx, applicationScope, new ModuleScriptProvider() {
	  public ModuleScript getModuleScript(Context cx, String id, Scriptable paths)
	    throws IOException {
	    return resolveScript(cx, URI.create(id + ".js"), baseURI);
	  }
	}, false);
      
      require.setAttributes("paths", ScriptableObject.EMPTY);
      require.delete("paths");
      require.installMain(cx, applicationScope, 
			  getAppName(), getFilename(),
			  cx.newObject(applicationScope) /* exports */);

      // Execute all <?esxx and <?esxx-import PIs
      for (ESXXScript es : scriptList.values()) {
	es.exec(cx, applicationScope);
      }

      // Start timers, if any
      startTimers();
    }
    catch (IllegalAccessException ex) {
      throw new ESXXException("Failed to initialize Application: " + ex.getMessage(), ex);
    }
    catch (InstantiationException ex) {
      throw new ESXXException("Failed to initialize Application: " + ex.getMessage(), ex);
    }
    catch (java.lang.reflect.InvocationTargetException ex) {
      throw new ESXXException("Failed to initialize Application: " + ex.getMessage(), ex);
    }
  }

  private void startTimers() {
    // Start timers, if any
    for (final TimerHandler th : timerHandlers) {
      th.future = esxx.getExecutor().scheduleAtFixedRate(new Runnable() {
	  @Override public void run() {
	    esxx.addContextAction(null, new ContextAction() {
		@Override public Object run(Context cx) {
		  try{
		    Object[] args = { new Date() };

		    return JS.callJSMethod(th.handler, args,
					   getAppName() + " timer",
					   cx, applicationScope);
		  }
		  catch (Exception ex) {
		    ex.printStackTrace();
		    return null;
		  }
		}
	      }, (int) (th.period * 2) /* Timeout */);
	  }
	}, th.delay, th.period, TimeUnit.MILLISECONDS);
    }
  }




  private void handleStylesheetPI(String data) {
    InputStream is = new ByteArrayInputStream(("<esxx-stylesheet " + data + "/>").getBytes());
    Document doc = esxx.parseXML(is, baseURI, null, null);
    Element root = doc.getDocumentElement();

    String type  = root.getAttributeNS(null, "type").trim();
    String href  = root.getAttributeNS(null, "href").trim();

    if (type == null || !type.equals("text/xsl")) {
      throw new ESXXException("<?esxx-stylesheet?> attribute 'type' " +
			      "must be set to 'text/xsl'");
    }

    if (href == null) {
      throw new ESXXException("<?esxx-stylesheet?> attribute 'href' " +
			      "must be specified");
    }

    try {
      xsltMatcher.addRequestPattern("", "", baseURI.resolve(new URI(href)).toString());
    }
    catch (URISyntaxException ex) {
      throw new ESXXException("<?esxx-stylesheet?> attribute 'href' is invalid: " +
			      ex.getMessage());
    }
  }

  private void handleImportPI(Context cx, String data) {
    InputStream is = new ByteArrayInputStream(("<esxx-include " + data + "/>").getBytes());
    Document doc = esxx.parseXML(is, baseURI, null, null);
    Element root = doc.getDocumentElement();

    String href  = root.getAttributeNS(null, "href").trim();

    if (href == null) {
      throw new ESXXException("<?esxx-include?> attribute 'href' " +
			      "must be specified");
    }

    try {
      resolveScript(cx, new URI(href), baseURI);
    }
    catch (URISyntaxException ex) {
      throw new ESXXException("<?esxx-include?> attribute 'href' is invalid: " +
			      ex.getMessage(), ex);
    }
    catch (IOException ex) {
      throw new ESXXException("<?esxx-include?> failed to include document: " +
			      ex.getMessage(), ex);
    }
  }

  private void handleHTTPHandler(Element e) {
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

  private void handleSOAPHandler(Element e) {
    String action = e.getAttributeNS(null, "action").trim();
    String uri    = e.getAttributeNS(null, "uri").trim();
    String object = e.getAttributeNS(null, "object").trim();

    // (All arguments are optional)

    soapMatcher.addRequestPattern(action, uri, object);
  }

  private void handleTimerHandler(Element e) {
    String delay   = e.getAttributeNS(null, "delay").trim();
    String period  = e.getAttributeNS(null, "period").trim();
    String handler = e.getAttributeNS(null, "handler").trim();

    if (delay.equals("") && period.equals("")) {
      throw new ESXXException("<timer> attribute 'delay' or 'period' must must be specified");
    }

    if (handler.equals("")) {
      throw new ESXXException("<timer> attribute 'handler' must be specified");
    }

    if (handler.endsWith(")")) {
      throw new ESXXException("<timer> attribute 'handler' value should not include parentheses");
    }


    timerHandlers.add(new TimerHandler(delay, period, handler));
  }

  private void handleErrorHandler(Element e) {
    String handler = e.getAttributeNS(null, "handler").trim();

    if (errorHandler != null) {
      throw new ESXXException("Error handler already defined as '" + errorHandler + "'");
    }

    if (handler.endsWith(")")) {
      throw new ESXXException("<error> attribute 'handler' value " +
			      "should not include parentheses");
    }

    errorHandler = handler;
  }

  private void handleExitHandler(Element e) {
    String handler = e.getAttributeNS(null, "handler").trim();

    if (exitHandler != null) {
      throw new ESXXException("Exit handler already defined as '" + exitHandler + "'");
    }

    if (handler.endsWith(")")) {
      throw new ESXXException("<exit> attribute 'handler' value " +
			      "should not include parentheses");
    }

    exitHandler = handler;
  }

  private void handleStylesheet(Element e) {
    String media_type = e.getAttributeNS(null, "media-type").trim();
    String uri        = e.getAttributeNS(null, "uri").trim();
    String href       = e.getAttributeNS(null, "href").trim();
    String type       = e.getAttributeNS(null, "type").trim();

    if (href.equals("")) {
      throw new ESXXException("<stylesheet> attribute 'href' " +
			      "must be specified");
    }

    if (!type.equals("") && !type.equals("text/xsl")) {
      throw new ESXXException("<stylesheet> attribute 'type' " +
			      "must be set to 'text/xsl'");
    }

    try {
      xsltMatcher.addRequestPattern(media_type, uri, baseURI.resolve(new URI(href)).toString());
    }
    catch (URISyntaxException ex) {
      throw new ESXXException("<stylesheet> attribute 'href' is invalid: " +
			      ex.getMessage());
    }
  }

  private void handleFilter(Element e) {
    String method  = e.getAttributeNS(null, "method").trim();
    String uri     = e.getAttributeNS(null, "uri").trim();
    String handler = e.getAttributeNS(null, "handler").trim();

    if (handler.equals("")) {
      throw new ESXXException("<filter> attribute 'handler' must " +
			      "must be specified");
    }

    if (handler.endsWith(")")) {
      throw new ESXXException("<filter> attribute 'handler' value " +
			      "should not include parentheses");
    }

    filters.add(new FilterRule(method, uri, handler));
  }

  private static class TLS {
    HashMap<Object, JSLRUCache> caches = new HashMap<Object, JSLRUCache>();
  }

  private class TimerHandler {
    TimerHandler(String delay, String period, String handler) {
      try {
	if (!delay.isEmpty()) {
	  this.delay = (long) (1000 * Double.parseDouble(delay));
	}
      }
      catch (NumberFormatException ex) {
	throw new ESXXException("Failed to parse <timer> attribute 'delay': " + ex.getMessage());
      }

      try {
	if (!period.isEmpty()) {
	  this.period = (long) (1000 * Double.parseDouble(period));
	}
      }
      catch (NumberFormatException ex) {
	throw new ESXXException("Failed to parse <timer> attribute 'period': " + ex.getMessage());
      }

      this.handler = handler;
    }

    public long delay;
    public long period;
    public String handler;
    public ScheduledFuture<?> future;
  }

  private static class FilterRule {
    public FilterRule(String method, String uri, String filter) {
      if (method.isEmpty()) {
	method = "[^" + SEPARATOR + "]+";
      }

      if (uri.isEmpty()) {
	uri = ".*";
      }

      String regex = "(?:" + method + ")" + SEPARATOR + "(?:" + uri + ")";

      pattern = Pattern.compile(regex);
      this.filter = filter;
    }

    public String matches(String method, String uri) {
      return pattern.matcher(method + SEPARATOR + uri).matches() ? filter : null;
    }

    private Pattern pattern;
    private String filter;

    private static char SEPARATOR = '\n';
  }

  public interface HandlerCallback {
    JSResponse execute(JSRequest req) 
      throws Exception;
  }

  private class FilterFunction
    extends FunctionObject {

    private static final long serialVersionUID = -3956216539946083943L;

    public FilterFunction(HandlerCallback handler, JSRequest req,
			  String method, String path_info) {
      super("next", filterMethod, applicationScope);

      this.request = req;
      this.handler = handler;

      for (FilterRule r : filters) {
	String m = r.matches(method, path_info);
	if (m != null) {
	  matchingFilters.add(m);
	}
      }
    }

    public JSResponse execute(Context cx) 
      throws Exception {
      if (matchingFilters.isEmpty()) {
	return handler.execute(request);
      }
      else {
	return executeFilter(cx, request, this, matchingFilters.remove(0));
      }
    }

    private List<String> matchingFilters = new LinkedList<String>();
    private JSRequest request;
    private HandlerCallback handler;
  }

  static private java.lang.reflect.Method filterMethod;

  @SuppressWarnings("unused") private static JSResponse next(Context cx, Scriptable thisObj,
							     Object[] args, Function funObj)
    throws Exception {
    FilterFunction ff = (FilterFunction) funObj;

    // Update request object, if argument is present
    if (args.length > 1 && args[0] != Context.getUndefinedValue()) {
      ff.request = (JSRequest) args[0];
    }

    // Execute next filter
    return ff.execute(cx);
  }

  static {
    try {
      filterMethod = Application.class.getDeclaredMethod("next",
							 Context.class,
							 Scriptable.class,
							 Object[].class,
							 Function.class);
    }
    catch (NoSuchMethodException ex) {
      throw new ESXXException("Failed to find Application.next(): ", ex);
    }
  }

  private class JMXBean 
    extends javax.management.StandardEmitterMBean
    implements org.esxx.jmx.ApplicationMXBean {

    public JMXBean() {
      super(org.esxx.jmx.ApplicationMXBean.class, true,
	    new javax.management.NotificationBroadcasterSupport());
    }

    @Override public String getAppName() {
      return Application.this.getAppName();
    }

    @Override public String getAppFilename() {
      return Application.this.getFilename();
    }

    @Override public boolean isDebuggerEnabled() {
      return debuggerEnabled;
    }

    @Override public boolean isDebuggerActivated() {
      return debuggerActivated;
    }

    @Override public void unloadApplication() {
      esxx.removeCachedApplication(Application.this);
    }

    @Override public org.esxx.jmx.ApplicationStats getStatistics() {
      synchronized (Application.this) {
	return new org.esxx.jmx.ApplicationStats(invocations, executionTime,
						 started, new Date(lastAccessed));
      }
    }
  }

  public class ESXXScript
    extends ModuleScript
    implements Script {

    public ESXXScript(Script script, URI uri) {
      super(script, uri.toString());
      this.uri    = uri;
    }

    @Override public Script getScript() {
      return this;
    }
    
    @Override public Object exec(Context cx, Scriptable scope) {
      URI old_location = setCurrentLocation(uri);

      try { 
	return super.getScript().exec(cx, scope);
      }
      finally {
	setCurrentLocation(old_location);
      }
    }

    private URI uri;
  }

  // private class ESXXModuleScriptProvider
  //   implements ModuleScriptProvider {

  //   @Override public ModuleScript getModuleScript(Context cx, 
  // 						  String module_id, 
  // 						  Scriptable paths) 
  //     throws IOException {
      
  //   }
  // }

  
  private ESXX esxx;
  private JMXBean jmxBean;
  private URI baseURI;
  private HashSet<URI> externalURIs = new HashSet<URI>();

  private URI workingDirectory;
  private JSURI jsWorkingDirectory;

  private URI currentLocation;
  private JSURI jsCurrentLocation;

  private String ident;

  private Logger logger;
  private JSLogger jsLogger;

  private boolean debuggerEnabled;
  private boolean debuggerActivated;

  private JSGlobal applicationScope;
  private JSESXX jsESXX;
  private JSLRUCache cache;

  private int enterCount = 0;
  private boolean terminated = false;

  private long invocations;
  private long executionTime;
  private Date started;
  private long lastAccessed;

  private Scriptable mainDocument;
  private JSURI mainURI;
  private Scriptable includePath;

  private boolean gotHTTPHandlers = false;
  private boolean gotSOAPHandlers = false;
  private boolean gotFilters = false;

  private Document xml;
  private LinkedHashMap<String, ESXXScript> scriptList 
    = new LinkedHashMap<String, ESXXScript>();

  private RequestMatcher soapMatcher = new RequestMatcher();
  private RequestMatcher requestMatcher = new RequestMatcher();
  private RequestMatcher xsltMatcher = new RequestMatcher();
  private String errorHandler;
  private String exitHandler;

  private List<FilterRule> filters = new LinkedList<FilterRule>();

  private Collection<TimerHandler> timerHandlers = new LinkedList<TimerHandler>();
}
