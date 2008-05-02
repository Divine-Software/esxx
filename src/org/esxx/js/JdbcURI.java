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

package org.esxx.js;

import java.net.URI;
import java.sql.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.esxx.ESXX;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JdbcURI 
  extends JSURI {
    public JdbcURI(URI uri) {
      super(uri);
    }

    protected Object query(Context cx, Scriptable thisObj, Object[] args) {
      try {
	ESXX       esxx       = ESXX.getInstance();
	String     query      = Context.toString(args[0]);
	Properties properties = getProperties(thisObj);

	Connection db = DriverManager.getConnection(uri.toString(), properties);

	Query q = new Query(query, db);

	if (q.needParams()) {
	  if (args.length < 2 || args[1] == Context.getUndefinedValue()) {
	    throw Context.reportRuntimeError("Missing query() argument.");
	  }
	
	  q.bindParams(cx, (Scriptable) args[1]);
	}

	Object rc = q.execute();
      
	if (rc instanceof ResultSet) {
	  ResultSet          rs = (ResultSet) rc;
	  ResultSetMetaData rmd = rs.getMetaData();

	  Document   result = esxx.createDocument("result");
	  Element    root   = result.getDocumentElement();

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
	  return rc;
	}
      }
      catch (SQLException ex) {
	throw Context.reportRuntimeError("SQL query failed: " + ex.getMessage()); 
      }
    }


    private static class Query {
	public Query(String unparsed_query, Connection db)
	  throws SQLException {
	  parseQuery(unparsed_query);

	  try {
	    sql = db.prepareCall(query);
	    pmd = sql.getParameterMetaData();
	  }
	  catch (SQLException ex) {
	    throw Context.reportRuntimeError("JDBC failed to prepare parsed SQL statement: " +
					     query + ": " + ex.getMessage());
	  }

	  if (pmd.getParameterCount() != params.size()) {
	    throw Context.reportRuntimeError("JDBC and ESXX report different " +
					     "number of arguments in SQL query"); 
	  }
	}

	public boolean needParams()
	  throws SQLException {
	  return pmd.getParameterCount() != 0;
	}

	public void bindParams(Context cx, Scriptable object) 
	  throws SQLException {
 
	  int p = 1;
	  for (String name : params) {
	    String value = Context.toString(JSURI.evalProperty(cx, object, name));

	    switch (pmd.getParameterType(p)) {
	      default:
		sql.setObject(p, value);
		break;
	    }

	    ++p;
	  }
	}

	public Object execute()
	  throws SQLException {
	  if (sql.execute()) {
	    sql.clearParameters();
	    return sql.getResultSet();
	  }
	  else {
	    sql.clearParameters();
	    return new Integer(sql.getUpdateCount());
	  }
	}

	private void parseQuery(String unparsed_query) {
	  StringBuffer s = new StringBuffer();
	  Matcher      m = paramPattern.matcher(unparsed_query);

	  params = new LinkedList<String>();

	  while (m.find()) {
	    String g = m.group();

	    if (m.start(1) != -1) {
	      // Match on group 1, which is our parameter pattern; append a single '?'
	      m.appendReplacement(s, "?");
	      params.add(g.substring(1, g.length() - 1));
	    }
	    else {
	      // Match on quoted strings, which we just copy as-is
	      m.appendReplacement(s, g);
	    }
	  }
	
	  m.appendTail(s);

	  query = s.toString();
	}

	private String query;
	private LinkedList<String> params;

	private CallableStatement sql;
	private ParameterMetaData pmd;

	private static final String quotePattern1 = "('((\\\\')|[^'])+')";
	private static final String quotePattern2 = "(`((\\\\`)|[^`])+`)";
	private static final String quotePattern3 = "(\"((\\\\\")|[^\"])+\")";

	private static final Pattern paramPattern = Pattern.compile(
	  "(\\{[^\\}]+\\})" +    // Group 1: Matches {identifier}
	  "|" + quotePattern1 + "|" + quotePattern2 + "|" + quotePattern3);
    }
}
