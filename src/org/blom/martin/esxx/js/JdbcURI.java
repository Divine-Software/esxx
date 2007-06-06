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

      CallableStatement sql = db.prepareCall(query);
      ParameterMetaData pmd = sql.getParameterMetaData();

      for (int i = 1; i <= pmd.getParameterCount(); ++i) {
	switch (pmd.getParameterType(i)) {
	  default:
	    sql.setObject(i, args[i]);
	    break;
	}
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
