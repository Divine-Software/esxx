
package org.blom.martin.esxx.js;

import org.blom.martin.esxx.ESXX;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPMessage;
import org.blom.martin.esxx.Workload;
import org.mozilla.javascript.*;
import org.w3c.dom.*;
import org.w3c.dom.bootstrap.*;

public class JSESXX {
    public JSESXX(ESXX esxx, Context cx, Scriptable scope, Workload workload,
		  Scriptable shared, Document document) {

      this.esxx    = esxx;
      this.cx      = cx;
      this.scope   = scope;
      this.baseURL = workload.getURL();

      this.in    = workload.getInputStream();
      this.debug = new PrintWriter(workload.getDebugWriter());
      this.error = new PrintWriter(workload.getErrorWriter());

      this.shared = shared;

      this.mimeHeaders = new MimeHeaders();

      handleProperties(workload);

      this.document = esxx.domToE4X(document, cx, scope);

      this.headers = cx.newObject(scope);
      ScriptableObject.putProperty(this.headers, "Cookies", cx.newObject(scope));
    }

    public Synchronizer sync(Function f) {
      return new Synchronizer(f);
    }

    public InputStream in;
    public PrintWriter error;
    public PrintWriter debug;

    public Scriptable shared;

    public Scriptable env;
    public Scriptable accept;
    public Scriptable query;
    public Scriptable headers;
    public Scriptable document;

    public String soapAction;
    public MimeHeaders mimeHeaders;
    public SOAPMessage soapMessage;


    private void handleProperties(Workload workload) {
      Document accept_doc;
      Document query_doc;

      this.env = cx.newObject(scope);
      this.query = cx.newObject(scope);

      try {
	DOMImplementationRegistry reg  = DOMImplementationRegistry.newInstance();
	DOMImplementation         impl = reg.getDOMImplementation("XML 3.0");

	accept_doc = impl.createDocument(null, "accept", null);
	query_doc  = impl.createDocument(null, "query", null);
      }
      catch (Exception ex) {
	// No DOM implementation registry??
	return;
      }

      for (String name :  workload.getProperties().stringPropertyNames()) {
	String value = workload.getProperties().getProperty(name).trim();
	
	// Add environtment variable to esxx.env
	ScriptableObject.putProperty(env, name, value);

	// If this is an HTTP header, get the original name back
	String hdr = esxx.cgiToHTTP(name);

	if (hdr != null) {
	  // Add real HTTP header to mime_headers
	  mimeHeaders.addHeader(hdr, value);

	  // Handle SOAPAction
	  if (hdr.equals("SOAPAction")) {
	    soapAction = value;
	  }

	  // Decode Accept* HTTP headers
	  handleAcceptHeader(hdr, value, accept_doc);
	}

	handleQueryHeader(name, value, cx, query);
      }

      accept = esxx.domToE4X(accept_doc, cx, scope);
//      query  = esxx.domToE4X(query_doc, cx, scope);
    }


    private void handleAcceptHeader(String hdr, String value, Document doc) {
      Element accept = doc.getDocumentElement();
      String  subname;

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

      TreeMap<Double, List<Element>> elements = new TreeMap<Double, List<Element>>();

      String[] values = value.split(",");

      for (String v : values) {
	double   q     = 1.0;
	double   w     = 0.0;
	String[] parts = v.split(";");

	Element element = doc.createElement(subname);
	element.setAttribute("type", parts[0].trim());
	element.appendChild(doc.createTextNode(parts[0].trim()));

	// Add all attributes
	for (int i = 1; i < parts.length; ++i) {
	  String[] attr = parts[i].split("=", 2);

	  if (attr.length == 2) {
	    // Parse Q factor
	    if (attr[0].trim().equals("q")) {
	      q = Double.parseDouble(attr[1].trim());
	    }
	    else {
	      element.setAttribute(attr[0].trim(), attr[1].trim());
	    }
	  }
	}

	element.setAttribute("q", "" + q);
	
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

	List<Element> l = elements.get(key);

	if (l == null) {
	  l = new ArrayList<Element>();
	  elements.put(key, l);
	}
	
	l.add(element);
      }

      for (List<Element> l : elements.values()) {
	for (Element e : l) {
	  accept.appendChild(e);
	}
      }
    }

    private void handleQueryHeader(String name, String value, 
				   Context cx, Scriptable var) {
      if (name.equals("QUERY_STRING") && value.length() > 0) {
	String[] args = value.split("&");

	for (String arg : args) {
	  String[] nv = arg.split("=", 2);

	  try {
	    String n = URLDecoder.decode(nv[0], "UTF-8").trim();

	    if (nv.length == 1) {
	      ScriptableObject.putProperty(var, makeXMLName(n), "");
	    }
	    else if (nv.length == 2) {
	      String v = URLDecoder.decode(nv[1], "UTF-8");
	      ScriptableObject.putProperty(var, makeXMLName(n), v);
	    }
	  }
	  catch (UnsupportedEncodingException ex) {
	    // Ignore illegal headers
	  }
	}
      }
    }

//     private void handleQueryHeader(String name, String value, Document doc) {
//       Element query = doc.getDocumentElement();

//       if (name.equals("REQUEST_METHOD")) {
// 	query.setAttribute("method", value);
//       }
//       else if (name.equals("PATH_INFO")) {
// 	query.setAttribute("path", value);
//       }
//       else if (name.equals("QUERY_STRING") && value.length() > 0) {
// 	String[] args = value.split("&");

// 	for (String arg : args) {
// 	  String[] nv = arg.split("=", 2);

// 	  try {
// 	    String n = URLDecoder.decode(nv[0], "UTF-8").trim();
	    
// 	    // TODO: Handle . or / to create sub-elements?
// 	    Element element = doc.createElement(makeXMLName(n));

// 	    if (nv.length == 2) {
// 	      String v = URLDecoder.decode(nv[1], "UTF-8");
// 	      element.appendChild(doc.createTextNode(v));
// 	    }
	  
// 	    query.appendChild(element);
// 	  }
// 	  catch (java.io.UnsupportedEncodingException ex) {
// 	    // Ignore illegal headers
// 	  }
// 	}
//     }

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


    private ESXX esxx;
    private Context cx;
    private Scriptable scope;
    private URL baseURL;
}
