
package org.blom.martin.esxx;

import java.io.*;
import java.sql.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Properties;
import org.blom.martin.esxx.*;
import org.blom.martin.esxx.js.JSESXX;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JSURI 
  extends ScriptableObject {
    public JSURI() {
    }

    public JSURI(ESXX esxx, URI uri) {
      this.esxx = esxx;
      this.uri  = uri;
    }

    public String getClassName() {
      return "URI";
    }


    public static Object jsFunction_load(Context cx, Scriptable thisObj,
					 Object[] args, Function funObj)
      throws IOException, org.xml.sax.SAXException {
      JSURI  js_this = checkInstance(thisObj);
      String type    = "text/xml";
      HashMap<String,String> params = new HashMap<String,String>();

      if (args.length >= 1 && args[0] != Context.getUndefinedValue()) {
	type = parseMIMEType(Context.toString(args[0]), params);
      }

      return js_this.load(cx, thisObj, type, params);
    }


    public static Object jsFunction_query(Context cx, Scriptable thisObj,
					  Object[] args, Function funObj)
      throws SQLException, IOException, org.xml.sax.SAXException
    {
      JSURI  js_this = checkInstance(thisObj);
      String type    = "text/xml";
      HashMap<String,String> params = new HashMap<String,String>();

      if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
	throw Context.reportRuntimeError("Missing query argument"); 
      }

      if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
	type = parseMIMEType(Context.toString(args[1]), params);
      }

      return js_this.query(cx, thisObj, Context.toString(args[0]), type, params);
    }

    static public Object jsConstructor(Context cx, 
				       java.lang.Object[] args, 
				       Function ctorObj, 
				       boolean inNewExpr) 
      throws java.net.URISyntaxException {
      ESXX esxx = (ESXX) cx.getThreadLocal(ESXX.class);
      URI base_uri = ((Workload) cx.getThreadLocal(Workload.class)).getURL().toURI();

      if (args.length == 0) {
	return new JSURI(esxx, base_uri);
      }
      else if (args.length == 1) {
	if (args[0] instanceof JSURI) {
	  JSURI old = (JSURI) args[0];
	  return new JSURI(esxx, old.uri);
	}
	else {
	  String uri = Context.toString(args[0]);
	  return new JSURI(esxx, base_uri.resolve(uri));
	}
      }
      else if (args.length == 2) {
	try {
	  JSURI old = (JSURI) args[0];
	  String uri = Context.toString(args[1]);

	  return new JSURI(esxx, old.uri.resolve(uri));
	}
	catch (ClassCastException ex) {
	  throw Context.reportRuntimeError("Double argument must be URI and String"); 
	}
      }

      return null;
    }

    public String toString() {
      return uri.toString();
    }

    private Object load(Context cx, Scriptable thisObj, 
			String type, HashMap<String,String> params)
      throws IOException, org.xml.sax.SAXException {
      if (type.equals("text/xml")) {
	Document result = null;

	// If this URI is a file: URI and is also a directory, create
	// an XML directory listing.
	if (uri.getScheme().equals("file")) {
	  File dir = new File(uri);
	
	  if (dir.exists() && dir.isDirectory()) {
	    result = createDirectoryListing(dir);
	  }
	}
	
	if (result == null) {
	  // Load URI as XML
	  JSESXX js_esxx = (JSESXX) cx.getThreadLocal(JSESXX.class);

	  result = esxx.parseXML(esxx.openCachedURL(uri.toURL()), uri.toURL(), null, 
				 js_esxx.debug);
	}

	return esxx.domToE4X(result, cx, this);
      }
      else if (type.equals("text/plain")) {
	// Load URI as plain text
	return loadString(uri, params);
      }
      else {
	throw Context.reportRuntimeError("Unknown content type argument '" + type + "'"); 
      }
    }

    private String loadString(URI uri, HashMap<String,String> params)
      throws IOException {
      String        cs = params.get("charset");
      StringBuilder sb = new StringBuilder();
      String        s;

      if (cs == null) {
	cs = "UTF-8";
      }

      BufferedReader br = new BufferedReader(new InputStreamReader(
					       esxx.openCachedURL(uri.toURL()),
					       cs));

      while ((s = br.readLine()) != null) {
	sb.append(s);
      }

      return sb.toString();
    }

    private Document createDirectoryListing(File dir) {
      File[] list = dir.listFiles();

      Document result = esxx.createDocument("result");
      Element  root   = result.getDocumentElement();

      for (File f : list) {
	Element element = null;

	if (f.isDirectory()) {
	  element = result.createElement("directory");
	}
	else if (f.isFile()) {
	  element = result.createElement("file");
	  setParam(result, element, "length", Long.toString(f.length()));
	}
	      
	setParam(result, element, "name", f.getName());
	setParam(result, element, "path", f.getPath());
	setParam(result, element, "uri", f.toURI().toString());
	setParam(result, element, "isHidden", f.isHidden() ? "true" : "false");
	setParam(result, element, "lastModified", Long.toString(f.lastModified()));
	setParam(result, element, "id", Integer.toHexString(f.hashCode()));
	root.appendChild(element);
      }

      return result;
    }


    private Object query(Context cx, Scriptable thisObj,
			 String query, String type, HashMap<String,String> params)
      throws SQLException, IOException, org.xml.sax.SAXException {
      String scheme = uri.getScheme();
      
      if (scheme.equals("jdbc")) {
	Properties properties = new Properties();

	for (Object id : ScriptableObject.getPropertyIds(thisObj)) {
	  if (id instanceof String) {
	    String key   = (String) id;
	    String value = Context.toString(ScriptableObject.getProperty(thisObj, key));

	    properties.setProperty(key, value);
	  }
	}

	Connection db     = DriverManager.getConnection(uri.toString(), properties);
	Statement  sql    = db.createStatement();

	Document   result = esxx.createDocument("result");
	Element    root   = result.getDocumentElement();

	if (sql.execute(query)) {
	  ResultSet rs = sql.getResultSet();
	  ResultSetMetaData rmd = rs.getMetaData();

	  int      count = rmd.getColumnCount();
	  String[] names = new String[count];

	  for (int i = 0; i < count; ++i) {
	    names[i] = rmd.getColumnName(i + 1);
	  }

	  while (rs.next()) {
	    Element row = result.createElement("row");
	    
	    for (int i = 0; i < count; ++i) {
	      setParam(result, row, names[i], rs.getString(i + 1));
	    }

	    root.appendChild(row);
	  }
	  
	  return esxx.domToE4X(result, cx, this);
	}
	else {
	  return new Integer(sql.getUpdateCount());
	}
      }
      else {
	throw Context.reportRuntimeError("URI protocol '" + scheme + 
					 "' does not support queries."); 
      }
    }


    private void setParam(Document document, Element element, String name, String value) {
//      element.setAttribute(name, value);
      Element e = document.createElement(name);
      e.appendChild(document.createTextNode(value));
      element.appendChild(e);
    }

    private static String parseMIMEType(String ct, HashMap<String,String> params) {
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

    private static JSURI checkInstance(Scriptable obj) {
      if (obj == null || !(obj instanceof JSURI)) {
	throw Context.reportRuntimeError("Called on incompatible object");
      }

      return (JSURI) obj;
    }

    private ESXX esxx;
    private URI uri;
}
