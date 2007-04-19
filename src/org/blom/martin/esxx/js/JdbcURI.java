
package org.blom.martin.esxx;

import java.sql.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Properties;
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

    protected Object query(Context cx, Scriptable thisObj,
			   String query, String type, HashMap<String,String> params)
      throws SQLException {
      
      if (type == null || type.equals("text/x-sql")) {
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
      else {
	throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() + 
					 "' can handle 'text/x-sql' queries."); 
      }
    }
}
