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

package org.esxx.js.protocol;

import java.net.URI;
import java.sql.*;
import java.util.LinkedList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.esxx.*;
import org.esxx.js.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JDBCHandler
  extends ProtocolHandler {
  public JDBCHandler(URI uri, JSURI jsuri) {
    super(uri, jsuri);
  }

  @Override
  public Object query(Context cx, Scriptable thisObj, Object[] args) {
    try {
      ESXX       esxx        = ESXX.getInstance();
      String     query       = Context.toString(args[0]);
      String     result_name = "result";
      String     entry_name  = "entry";
      Scriptable params      = null;

      if (args.length >= 2 && args[1] instanceof Scriptable) {
	Object o;

	params = (Scriptable) args[1];
	
	if ((o = params.get("$result", params)) != Scriptable.NOT_FOUND) {
	  result_name = Context.toString(o);
	}

	if ((o = params.get("$entry", params)) != Scriptable.NOT_FOUND) {
	  entry_name = Context.toString(o);
	}
      }

      Properties p = jsuri.getParams(cx, uri);
      Scriptable a = jsuri.getAuth(cx, uri, null);

      if (a != null) {
	p.setProperty("username", Context.toString(a.get("username", a)));
	p.setProperty("password", Context.toString(a.get("password", a)));
      }

      Connection db = DriverManager.getConnection(uri.toString(), p);

      Query q = new Query(query, db);

      if (q.needParams()) {
	if (params == null) {
	  throw Context.reportRuntimeError("Missing query() argument.");
	}

	q.bindParams(cx, (Scriptable) args[1]);
      }


      Object rc = q.execute();

      if (rc instanceof ResultSet) {
	ResultSet          rs = (ResultSet) rc;
	ResultSetMetaData rmd = rs.getMetaData();

	Document   result = esxx.createDocument(result_name);
	Element    root   = result.getDocumentElement();

	int      count = rmd.getColumnCount();
	String[] names = new String[count];

	for (int i = 0; i < count; ++i) {
	  names[i] = rmd.getColumnName(i + 1);
	}

	while (rs.next()) {
	  Element row = result.createElementNS(null, entry_name);

	  for (int i = 0; i < count; ++i) {
	    addChild(row, names[i].toLowerCase(), rs.getString(i + 1));
	  }

	  root.appendChild(row);
	}

	return ESXX.domToE4X(result, cx, thisObj);
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
	String value = Context.toString(ProtocolHandler.evalProperty(cx, object, name));

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
