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
import org.esxx.util.IO;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.net.URLDecoder;
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
    public JSRequest() {
      super();
    }

    public JSRequest(Request request, Context cx, Scriptable scope) {
      this();

      ESXX esxx    = ESXX.getInstance();

      env     = cx.newObject(scope);
      headers = cx.newObject(scope);
      cookies = cx.newObject(scope);
      accept  = cx.newObject(scope);
      query   = cx.newObject(scope);
      args    = null;

      mimeHeaders = new MimeHeaders();
      acceptValueOf = new FunctionObject("valueOf", acceptValueOfMethod, accept);

      for (String name :  request.getProperties().stringPropertyNames()) {
	String value = request.getProperties().getProperty(name).trim();

	// Add environtment variable to esxx.env
	ScriptableObject.putProperty(env, name, value);

	// If this is an HTTP header, get the original name back
	String hdr = esxx.cgiToHTTP(name);

	if (hdr != null) {
	  // Add real HTTP header to mimeHeaders and this.headers
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
	  handleQueryHeader(value);
	}
      }

      logger  = (JSLogger) cx.newObject(scope, "Logger", new Object[] { 
	  request, request.getProperties().getProperty("SCRIPT_NAME") });

      // Now parse the POST/PUT/etc. message
      parseMessage(request, cx, scope);
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


    public Object jsGet_message() {
      return message;
    }


    public String jsGet_soapAction() {
      return soapAction;
    }


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
    private long contentLength;
    private MimeHeaders mimeHeaders;

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
      mimeHeaders.addHeader(name, value);

      ScriptableObject.putProperty(headers, name, value);
    }


    private void handleCookieHeader(String name, String value) {
      // TODO
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


    private void handleQueryHeader(String value) {
      if (value.length() > 0) {
	String[] args = value.split("&");

	for (String arg : args) {
	  String[] nv = arg.split("=", 2);

	  try {
	    String n = URLDecoder.decode(nv[0], "UTF-8").trim();

	    if (nv.length == 1) {
	      ScriptableObject.putProperty(query, makeXMLName(n), "");
	    }
	    else if (nv.length == 2) {
	      String v = URLDecoder.decode(nv[1], "UTF-8");
	      ScriptableObject.putProperty(query, makeXMLName(n), v);
	    }
	  }
	  catch (UnsupportedEncodingException ex) {
	    // Ignore illegal headers -- Or throw?
	  }
	}
      }
    }


    public void handleContentHeader(String name, String value) {
      if (name.startsWith("Content-")) {
	if (name.equals("Content-Type")) {
	  contentType = value;
	}
	else if (name.equals("Content-Length")) {
	  contentLength = Long.parseLong(value);
	}
	else {
	  throw new ESXXException(501, "Unsupported Content header: " + name);
	}
      }
    }


    private void parseMessage(Request request, Context cx, Scriptable scope) {
      ESXX esxx = ESXX.getInstance();

      // Consume SOAP message, if any
      // TODO: Add a SOAP handler in Parser.java
      if (soapAction != null) {
	try {
	  message = MessageFactory.newInstance(
	    SOAPConstants.DYNAMIC_SOAP_PROTOCOL).createMessage(mimeHeaders,
							       request.getInputStream());
	}
	catch (IOException ex) {
	  throw new ESXXException("Unable to read SOAP message stream: " + ex.getMessage());
	}
	catch (SOAPException ex) {
	  throw new ESXXException("Invalid SOAP message: " + ex.getMessage());
	}
      }
      else if (contentType != null && contentLength > 0) {
	try {
	  HashMap<String,String> params = new HashMap<String,String>();
	  String                 ct     = ESXX.parseMIMEType(contentType, params);

	  if ("application/x-www-form-urlencoded".equals(ct)) {
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    
	    IO.copyStream(request.getInputStream(), bos);
	    handleQueryHeader(bos.toString("UTF-8"));
	  }
	  else {
	    message = esxx.parseStream(ct, params, request.getInputStream(), 
	                               request.getScriptFilename().toURL(),
				       null,
				       new java.io.PrintWriter(request.getDebugWriter()),
				       cx, scope);
	  }
	}
	catch (Exception ex) {
	  throw new ESXXException("Unable to parse request entity: " + ex.getMessage());
	}
      }
    }


    private String makeXMLName(String s) {
      char[] chars = s.toCharArray();

      if(!isNameStartChar(chars[0])) {
	chars[0] = '_';
      }

      for (int i = 1; i < chars.length; ++i) {
	if (!isNameChar(chars[i])) {
	  chars[i] = '_';
	}
      }

      return new String(chars);
    }

    private static boolean isNameStartChar(char ch) {
      return (Character.isLetter(ch) || ch == '_');
    }

    private static boolean isNameChar(char ch) {
      return (isNameStartChar(ch) || Character.isDigit(ch) || ch == '.' || ch == '-');
    }
}
