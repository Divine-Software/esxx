
package org.blom.martin.esxx.js;

import java.sql.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Properties;
import org.blom.martin.esxx.ESXX;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JdbcURI 
  extends JSURI {
    public JdbcURI(ESXX esxx, URI uri) {
      super(esxx, uri);
    }

    protected Object load(Context cx, Scriptable thisObj, 
			  String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() + 
				       "' does not support load()."); 
    }

    protected Object query(Context cx, Scriptable thisObj, Object[] args)
      throws SQLException {
      String     query      = Context.toString(args[0]);
      Properties properties = new Properties();

      for (Object id : ScriptableObject.getPropertyIds(thisObj)) {
	if (id instanceof String) {
	  String key   = (String) id;
	  String value = Context.toString(ScriptableObject.getProperty(thisObj, key));

	  properties.setProperty(key, value);
	}
      }

// 	fixa prepared statements här, med en hashtabell:
// 	  "SELECT * from my_table where name = {name}"

      Connection db = DriverManager.getConnection(uri.toString(), properties);

      Document   result = esxx.createDocument("result");
      Element    root   = result.getDocumentElement();

      StringBuilder sb = new StringBuilder();

      boolean quote1 = false;
      boolean quote2 = false;
      char    escape = '\\';

      java.util.ArrayList<String> vars = new java.util.ArrayList<String>();

      for (int i = 0; i < query.length(); ++i) {
	if (query.charAt(i) == escape) {
	  sb.append(query.charAt(i));

	  ++i;

	  if (i != query.length()) {
	    sb.append(query.charAt(i));
	  }

	  continue;
	}
	  
	if (!quote2 && query.charAt(i) == '\'') {
	  quote1 = !quote1;
	}
	else if (!quote1 && query.charAt(i) == '"') {
	  quote2 = !quote2;
	}
	  
	if (!quote1 && 
	    !quote2 && 
	    query.charAt(i) == '{' &&
	    i + 3 < query.length() &&
	    Character.isJavaIdentifierStart(query.charAt(i + 1))) {
	  for (int j = i + 2; j < query.length(); ++j) {
	    if (query.charAt(j) == '}') {
	      // Found a variable reference. Store it and put a ? in the SQL query.
	      String var = query.substring(i + 1, j);

	      System.err.println("Found var " + var);
	      vars.add(var);

	      sb.append('?');
	      i = j + 1;
	    }
	    else if (!Character.isJavaIdentifierPart(query.charAt(j))) {
	      // Append char as-is and break out of inner loop
	      sb.append(query.charAt(i));
	      break;
	    }
	  }
	}
	else {
	  // Append char
	  sb.append(query.charAt(i));
	}
      }

      System.out.println("Original  : " + query);
      System.out.println("Translated: " + sb.toString());

      CallableStatement sql = db.prepareCall(sb.toString());
      ParameterMetaData pmd = sql.getParameterMetaData();

      System.err.println("Got pmd: " + pmd);
      for (int i = 1; i <= pmd.getParameterCount(); ++i) {
	try {
	  System.err.println(i + ": " + pmd.getParameterClassName(i));
	  System.err.println(i + ": " + pmd.getParameterMode(i));
	  System.err.println(i + ": " + pmd.getParameterType(i));
	  System.err.println(i + ": " + pmd.getParameterTypeName(i));
	}
	catch (Exception ex) {}
      }

      if (sql.execute()) {
	ResultSet rs = sql.getResultSet();
	ResultSetMetaData rmd = rs.getMetaData();

	int      count = rmd.getColumnCount();
	String[] names = new String[count];

	for (int i = 0; i < count; ++i) {
	  names[i] = rmd.getColumnName(i + 1);
	}

	while (rs.next()) {
	  Element row = result.createElement("entry");
	    
	  for (int i = 0; i < count; ++i) {
	    addChild(row, names[i], rs.getString(i + 1));
	  }

	  root.appendChild(row);
	}
	  
	return esxx.domToE4X(result, cx, this);
      }
      else {
	return new Integer(sql.getUpdateCount());
      }
    }
}
