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

import java.net.URISyntaxException;
import java.sql.*;
import java.util.Iterator;
import java.util.Collection;
import java.util.Properties;
import java.util.HashMap;
import org.esxx.*;
import org.esxx.js.JSURI;
import org.esxx.util.QueryCache;
import org.esxx.util.QueryHandler;
import org.esxx.util.StringUtil;
import org.esxx.util.XML;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JDBCHandler
  extends ProtocolHandler {
  public JDBCHandler(JSURI jsuri)
    throws URISyntaxException {
    super(jsuri);

    synchronized (JDBCHandler.class) {
      if (queryCache == null) {
	queryCache = new QueryCache(10, 2 * 60000, 1000, 1 * 60000);

	// Purge connections peridically
	ESXX.getInstance().getExecutor().scheduleWithFixedDelay(new Runnable() {
	    @Override public void run() {
	      queryCache.purgeConnections();
	    }
	  }, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
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

      Properties p = jsuri.getParams(cx, jsuri.getURI());
      Scriptable a = jsuri.getAuth(cx, jsuri.getURI(), null);

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

	  public int getParamLength(String param) {
	    Object obj = ScriptRuntime.getObjectElem(final_params, param, cx);
	    int    len = 1;

	    if (obj instanceof Iterable) {
	      Iterator i = ((Iterable) obj).iterator();

	      for (len = 0; i.hasNext(); i.next()) {
		++len;
	      }
	    }
	    else if (obj instanceof Scriptable) {
	      len = ((Scriptable) obj).getIds().length;
	    }

	    return len;
	  }

	  public void resolveParam(String param, int length, Collection<Object> result) {
	    Object obj = ScriptRuntime.getObjectElem(final_params, param, cx);

	    if (obj instanceof Iterable) {
	      for (Object o : ((Iterable) obj)) {
		result.add(o);
	      }
	    }
	    else if (obj instanceof Scriptable) {
	      Scriptable jsobj = (Scriptable) obj;

	      for (Object id : jsobj.getIds()) {
		result.add(ScriptRuntime.getObjectElem(jsobj, id, cx));
	      }
	    }
	    else {
	      result.add(obj);
	    }
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
		String value  = rs.getString(i + 1);
		Element child = XML.addChild(row, names[i], value);

		int type = rmd.getColumnType(i + 1);

		if (value == null) {
		  type = Types.NULL;
		}

		child.setAttributeNS(null, "type", typeToString.get(type));
	      }

	      root.appendChild(row);
	    }
	  }
	};

      if (function == null) {
	queryCache.executeQuery(jsuri.getURI(), p, query, qh);
	return ESXX.domToE4X((Document) final_result[0], cx, thisObj);
      }
      else {
	queryCache.executeTransaction(jsuri.getURI(), p, qh);
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

  private static HashMap<Integer, String> typeToString = new HashMap<Integer, String>();

  static {
    typeToString.put(Types.ARRAY,         "array");
    typeToString.put(Types.BIGINT,        "bigint");
    typeToString.put(Types.BINARY,        "binary");
    typeToString.put(Types.BIT,           "bit");
    typeToString.put(Types.BLOB,          "blob");
    typeToString.put(Types.BOOLEAN,       "boolean");
    typeToString.put(Types.CHAR,          "char");
    typeToString.put(Types.CLOB,          "clob");
    typeToString.put(Types.DATALINK,      "datalink");
    typeToString.put(Types.DATE,          "date");
    typeToString.put(Types.DECIMAL,       "decimal");
    typeToString.put(Types.DISTINCT,      "distinct");
    typeToString.put(Types.DOUBLE,        "double");
    typeToString.put(Types.FLOAT,         "float");
    typeToString.put(Types.INTEGER,       "integer");
    typeToString.put(Types.JAVA_OBJECT,   "javaobject");
    typeToString.put(Types.LONGNVARCHAR,  "longnvarchar");
    typeToString.put(Types.LONGVARBINARY, "longvarbinary");
    typeToString.put(Types.LONGVARCHAR,   "longvarchar");
    typeToString.put(Types.NCHAR,         "nchar");
    typeToString.put(Types.NCLOB,         "nclob");
    typeToString.put(Types.NULL,          "null");
    typeToString.put(Types.NUMERIC,       "numeric");
    typeToString.put(Types.NVARCHAR,      "nvarchar");
    typeToString.put(Types.OTHER,         "other");
    typeToString.put(Types.REAL,          "real");
    typeToString.put(Types.REF,           "ref");
    typeToString.put(Types.ROWID,         "rowid");
    typeToString.put(Types.SMALLINT,      "smallint");
    typeToString.put(Types.SQLXML,        "sqlxml");
    typeToString.put(Types.STRUCT,        "struct");
    typeToString.put(Types.TIME,          "time");
    typeToString.put(Types.TIMESTAMP,     "timestamp");
    typeToString.put(Types.TINYINT,       "tinyint");
    typeToString.put(Types.VARBINARY,     "varbinary");
    typeToString.put(Types.VARCHAR,       "varchar");
  }
}
