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

package org.esxx.js;

import org.esxx.*;
import org.esxx.util.StringUtil;

import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import org.mozilla.javascript.*;


public class JSRequest
  extends ScriptableObject {
    private static final long serialVersionUID = -777379647478473562L;

    public JSRequest() {
      super();
    }

    public JSRequest(Request request, Context cx, Scriptable scope) {
      this();

      ESXX esxx = ESXX.getInstance();

      this.request = request;

      requestURI = (JSURI) cx.newObject(scope, "URI", new Object[] { request.getRequestURI() });
      scriptURI  = (JSURI) cx.newObject(scope, "URI", new Object[] { request.getScriptURI() });

      env     = cx.newObject(scope);
      headers = cx.newObject(scope);
      cookies = cx.newObject(scope);
      accept  = cx.newObject(scope);
      query   = cx.newObject(scope);
      args    = null;

      acceptValueOf = new FunctionObject("valueOf", acceptValueOfMethod, accept);

      for (String name :  request.getProperties().stringPropertyNames()) {
	String value = request.getProperties().getProperty(name).trim();

	// Add environtment variable to esxx.env
	ScriptableObject.putProperty(env, name, value);

	// If this is an HTTP header, get the original name back
	String hdr = esxx.cgiToHTTP(name);

	if (hdr != null) {
	  // Add real HTTP header to this.headers
	  addHeader(hdr, value);

	  // Decode cookies
	  handleCookieHeader(hdr, value);

	  // Decode Accept* HTTP headers
	  handleAcceptHeader(hdr, value, cx, accept);

	  // Decode Content-* HTTP headers
	  handleContentHeader(hdr, value);

	  // Handle SOAPAction
	  if (hdr.equals("SOAPAction")) {
	    soapAction = value;
	  }
	}

	if (name.equals("QUERY_STRING")) {
	  try {
	    StringUtil.decodeFormVariables(value, query);
	  }
	  catch (UnsupportedEncodingException ex) {
	    throw new ESXXException("Unable to parse request entity: " + ex.getMessage(), ex);
	  }
	}
      }

      logger  = (JSLogger) JSESXX.newObject(cx, scope, "Logger", new Object[] { 
	  request, request.getScriptName() });
    }

    public void setArgs(Scriptable uri_params) {
      args = uri_params;
    }

    static public Object jsConstructor(Context cx,
				       java.lang.Object[] args,
				       Function ctorObj,
				       boolean inNewExpr) {
      return new JSRequest((Request) args[0], cx, ctorObj);
    }


    @Override
    public String getClassName() {
      return "Request";
    }

    public String jsGet_requestMethod() {
      return request.getRequestMethod();
    }

    public JSURI jsGet_requestURI() {
      return requestURI;
    }

    public JSURI jsGet_scriptURI() {
      return scriptURI;
    }

    public String jsGet_scriptName() {
      return request.getScriptName();
    }

    public String jsGet_pathInfo() {
      return request.getPathInfo();
    }

    public Scriptable jsGet_env() {
      return env;
    }

    public Scriptable jsGet_headers() {
      return headers;
    }

    public Scriptable jsGet_cookies() {
      return cookies;
    }

    public Scriptable jsGet_accept() {
      return accept;
    }


    public Scriptable jsGet_query() {
      return query;
    }

    public Scriptable jsGet_args() {
      return args;
    }


    public Scriptable jsGet_log() {
      return logger;
    }

    public Object jsGet_contentType() {
      return contentType;
    }

    public long jsGet_contentLength() {
      return contentLength;
    }

    public synchronized Object jsGet_message() {
      if (message == null) {
	// Now parse the POST/PUT/etc. message
	message = parseMessage();
      }

      return message;
    }


    public String jsGet_soapAction() {
      return soapAction;
    }

    private Request request;

    private JSURI requestURI;
    private JSURI scriptURI;

    private Scriptable env;
    private Scriptable headers;

    private Scriptable cookies;
    private Scriptable accept;
    private Scriptable query;
    private Scriptable args;

    private Scriptable logger;

    private Object message;

    private String soapAction;
    private String contentType;
    private long contentLength = -1;
    private HashMap<String,String> contentTypeParams;

    private Scriptable acceptValueOf;
    static private java.lang.reflect.Method acceptValueOfMethod;
    @SuppressWarnings("unused")
	private static Object acceptValueOf(Context cx, Scriptable thisObj,
					Object[] args, Function funObj) {
      return thisObj.get("value", thisObj);
    }
    static {
      try {
	acceptValueOfMethod = JSRequest.class.getDeclaredMethod("acceptValueOf",
								Context.class,
								Scriptable.class,
								Object[].class,
								Function.class);
      }
      catch (NoSuchMethodException ex) {
	throw new ESXXException("Failed to find JSRequest.acceptValueOf(): ", ex);
      }
    }


    private void addHeader(String name, String value) {
      ScriptableObject.putProperty(headers, name, value);
    }


    private void handleCookieHeader(String hdr, String value) {
      if (hdr.equals("Cookie")) {
	for (String cookie : value.split(";")) {
	  String[] parts = cookie.split("=", 2);
	  String cn = parts[0];
	  String cv = parts.length < 2 || parts[1] == null ? "" : parts[1];

	  ScriptableObject.putProperty(cookies, cn, cv);
	}
      }
    }


    private void handleAcceptHeader(String hdr, String value, Context cx, Scriptable accept) {
      String subname;

      if (hdr.equals("Accept")) {
	subname = "media";
      }
      else if (hdr.startsWith("Accept-")) {
	subname = hdr.substring(7).toLowerCase();
      }
      else {
	// Do nothing
	return;
      }

      Map<Double, List<Scriptable>> objects = new TreeMap<Double, List<Scriptable>>();

      String[] values = value.split(",");

      for (String v : values) {
	double   q     = 1.0;
	double   w     = 0.0;
	String[] parts = v.split(";");

	Scriptable object = cx.newObject(accept);
	object.put("valueOf", object, acceptValueOf);
	object.put("value", object, parts[0].trim());

	// Add all attributes
	for (int i = 1; i < parts.length; ++i) {
	  String[] attr = parts[i].split("=", 2);

	  if (attr.length == 2) {
	    // Parse Q factor
	    if (attr[0].trim().equals("q")) {
	      q = Double.parseDouble(attr[1].trim());
	    }
	    else {
	      object.put(attr[0].trim(), object, attr[1].trim());
	    }
	  }
	}

	object.put("q", object, "" + q);

	// Calculate implicit weight
	if (parts[0].trim().equals("*/*")) {
	  w = 0.0000;
	}
	else if (parts[0].trim().endsWith("/*")) {
	  w = 0.0001;
	}
	else {
	  w = 0.0002;
	}

	// Attributes give extra points
	w += parts.length * 0.00001;


	// Add to tree multi-map, inverse order
	double key = -(q + w);

	List<Scriptable> l = objects.get(key);

	if (l == null) {
	  l = new ArrayList<Scriptable>();
	  objects.put(key, l);
	}

	l.add(object);
      }

      Scriptable object = cx.newArray(accept, objects.size());
      accept.put(subname, accept, object);

      int i = 0;
      for (List<Scriptable> l : objects.values()) {
	for (Scriptable s : l) {
	  object.put(i++, object, s);
	}
      }
    }




    public void handleContentHeader(String name, String value) {
      if (name.startsWith("Content-")) {
	if (name.equals("Content-Type")) {
	  contentTypeParams = new HashMap<String,String>();
	  contentType       = ESXX.parseMIMEType(value, contentTypeParams);
	}
	else if (name.equals("Content-Length")) {
	  contentLength = Long.parseLong(value);
	}
	else {
	  throw new ESXXException(501, "Unsupported Content header: " + name);
	}
      }
    }


    private Object parseMessage() {
      // Consume SOAP message, if any
      // TODO: Add a SOAP handler in Parser.java
      if (soapAction != null) {
	try {
	  MimeHeaders mime_headers = new MimeHeaders();

	  for (Object k : headers.getIds()) {
	    if (k instanceof String) {
	      String name  = (String) k;
	      String value = Context.toString(ScriptableObject.getProperty(headers, name));

	      mime_headers.addHeader(name, value);
	    }
	  }

	  return MessageFactory.newInstance(SOAPConstants.DYNAMIC_SOAP_PROTOCOL)
	    .createMessage(mime_headers, request.getInputStream());
	}
	catch (IOException ex) {
	  throw new ESXXException("Unable to read SOAP message stream: " + ex.getMessage());
	}
	catch (SOAPException ex) {
	  throw new ESXXException("Invalid SOAP message: " + ex.getMessage());
	}
	finally {
	  try { request.getInputStream().close(); } catch (Exception ex) {}
	}
      }
      else if (contentType != null) {
	try {
	  ESXX esxx = ESXX.getInstance();
	  return esxx.parseStream(contentType, contentTypeParams, request.getInputStream(), 
				  request.getScriptFilename(),
				  null,
				  new java.io.PrintWriter(request.getErrorWriter()),
				  Context.getCurrentContext(), this);
	}
	catch (Exception ex) {
	  throw new ESXXException("Unable to parse request entity: " + ex.getMessage(), ex);
	}
	finally {
	  try { request.getInputStream().close(); } catch (Exception ex) {}
	}
      }
      else {
	// Return a dummy object
	return Context.getCurrentContext().newObject(this);
      }
    }
}
