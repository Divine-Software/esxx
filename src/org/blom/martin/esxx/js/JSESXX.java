
package org.blom.martin.esxx.js;

import org.blom.martin.esxx.ESXX;

import java.io.PrintWriter;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import javax.xml.soap.*;
import org.blom.martin.esxx.Workload;
import org.mozilla.javascript.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.bootstrap.*;
import org.w3c.dom.*;

import org.w3c.dom.Document;

public class JSESXX {
    public JSESXX(ESXX esxx, Context cx, Scriptable scope, Workload workload,
		  Document document, URL stylesheet) {

      this.esxx  = esxx;
      this.cx    = cx;
      this.scope = scope;

      this.in    = workload.getInputStream();
      this.debug = new PrintWriter(workload.getDebugWriter());
      this.error = new PrintWriter(workload.getErrorWriter());

      this.mimeHeaders = new MimeHeaders();

      handleProperties(workload);

      this.document = esxx.domToE4X(document, cx, scope);

      this.headers = cx.newObject(scope, "Object");
      ScriptableObject.putProperty(this.headers, "Status", "200 OK");

      ScriptableObject.putProperty(this.headers, "Cookies", cx.newObject(scope, "Object"));

      this.stylesheet = (stylesheet != null ? stylesheet.toString() : "");
    }

    public InputStream in;
    public PrintWriter error;
    public PrintWriter debug;

    public Scriptable env;
    public Scriptable accept;
    public Scriptable headers;
    public Scriptable document;

    public String soapAction;
    public MimeHeaders mimeHeaders;
    public SOAPMessage soapMessage;

    public String stylesheet;


    private void handleProperties(Workload workload) {
      Document doc;
      Element  root;

      this.env = cx.newObject(scope, "Object");

      try {
	DOMImplementationRegistry reg  = DOMImplementationRegistry.newInstance();
	DOMImplementation         impl = reg.getDOMImplementation("XML 3.0");

	doc    = impl.createDocument(null, "accept", null);
	root   = doc.getDocumentElement();
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
	  handleAcceptHeader(hdr, value, doc, root);
	}
      }

      accept = esxx.domToE4X(doc, cx, scope);
    }


    private void handleAcceptHeader(String hdr, String value, Document doc, Element accept) {
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

    private ESXX esxx;
    private Context cx;
    private Scriptable scope;
}
