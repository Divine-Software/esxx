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

    synchronized (JDBCHandler.class) {
      if (queryCache == null) {
	queryCache = new QueryCache(10, 2 * 60000, 1000, 1 * 60000);

	new Thread(new Runnable() {
	    public void run() {
	      while (true) {
		try { Thread.sleep(1000); } catch (Exception ex) {}
		queryCache.purgeConnections();
	      }
	    }
	  }).start();
      }
    }
  }

  @Override
    public Object query(final Context cx, final Scriptable thisObj, Object[] args) {
    try {
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

      final Scriptable final_params  = params;
      final String final_result_name = result_name;
      final String final_entry_name  = entry_name;

      Object rc = queryCache.executeQuery(uri, p, query, new QueryCache.Callback() {
	  public Object execute(QueryCache.Query q)
	    throws SQLException {
	    if (q.needParams()) {
	      if (final_params == null) {
		throw Context.reportRuntimeError("Missing query() argument.");
	      }

	      q.bindParams(cx, final_params);
	    }

	    Object rc = q.execute();

	    if (rc instanceof ResultSet) {
	      ResultSet          rs = (ResultSet) rc;
	      ResultSetMetaData rmd = rs.getMetaData();

	      ESXX       esxx   = ESXX.getInstance();
	      Document   result = esxx.createDocument(final_result_name);
	      Element    root   = result.getDocumentElement();

	      int      count = rmd.getColumnCount();
	      String[] names = new String[count];

	      for (int i = 0; i < count; ++i) {
		names[i] = rmd.getColumnName(i + 1);
	      }

	      while (rs.next()) {
		Element row = result.createElementNS(null, final_entry_name);

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
	});

      return rc;
    }
    catch (SQLException ex) {
      throw Context.reportRuntimeError("SQL query failed: " + ex.getMessage());
    }
  }


  private static QueryCache queryCache;
}
