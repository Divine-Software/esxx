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
import java.net.URISyntaxException;
import java.sql.*;
import java.util.Properties;
import org.esxx.*;
import org.esxx.js.JSURI;
import org.esxx.util.QueryCache;
import org.esxx.util.QueryHandler;
import org.esxx.util.StringUtil;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JDBCHandler
  extends ProtocolHandler {
  public JDBCHandler(URI uri, JSURI jsuri)
    throws URISyntaxException {
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
      String     query       = null;
      Function   function    = null;
      Scriptable params      = null;
      String     result_name = "result";
      String     entry_name  = "entry";

      if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
	throw Context.reportRuntimeError("Missing query argument");
      }

      if (args[0] instanceof Function) {
	function = (Function) args[0];
      }
      else {
	query = Context.toString(args[0]);
      }

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

      final Function final_function = function;
      final Scriptable final_params  = params;
      final String final_entry_name  = entry_name;
      final Object[]   final_result = new Object[1];

      if (function == null) {
	ESXX esxx = ESXX.getInstance();
	final_result[0] =  esxx.createDocument(result_name);
      }

      QueryHandler qh = new QueryHandler() {
	  public void handleTransaction()
	    throws SQLException {
	    Scriptable thiz = final_function.getParentScope();
	    final_result[0] = final_function.call(cx, thiz, thiz, Context.emptyArgs);
	  }

	  public Object resolveParam(String param) 
	    throws SQLException {
	    return ProtocolHandler.evalProperty(cx, final_params, param);
	  }

	  public void handleResult(int set, int uc, ResultSet rs) 
	    throws SQLException {
	    ResultSetMetaData rmd = rs.getMetaData();
	    int             count = rmd.getColumnCount();
	    String[]        names = new String[count];
	    
	    for (int i = 0; i < count; ++i) {
	      names[i] = StringUtil.makeXMLName(rmd.getColumnLabel(i + 1).toLowerCase(), "");
	    }

	    Document doc = (Document) final_result[0];
	    Element root = doc.getDocumentElement();

	    while (rs.next()) {
	      Element row = doc.createElementNS(null, final_entry_name);

	      row.setAttributeNS(null, "resultSet", Integer.toString(set));

	      if (uc != -1) {
		row.setAttributeNS(null, "updateCount", Integer.toString(uc));
	      }

	      for (int i = 0; i < count; ++i) {
		addChild(row, names[i], rs.getString(i + 1));
	      }

	      root.appendChild(row);
	    }
	  }
	};

      if (function == null) {
	queryCache.executeQuery(uri, p, query, qh);
	return ESXX.domToE4X((Document) final_result[0], cx, thisObj);
      }
      else {
	queryCache.executeTransaction(uri, p, qh);
	return final_result[0];
      }
    }
    catch (SQLException ex) {
      throw Context.reportRuntimeError("SQL query failed: " + ex.getMessage());
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    return null;
  }


  private static QueryCache queryCache;
}
