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
import org.mozilla.javascript.xml.XMLObject;
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

  @Override public Object query(Context cx, Scriptable thisObj, Object[] args) {
    try {
      if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
	throw Context.reportRuntimeError("Missing query argument");
      }

      Properties p = jsuri.getParams(cx, jsuri.getURI());
      Scriptable a = jsuri.getAuth(cx, jsuri.getURI(), null, null);

      if (a != null) {
	p.setProperty("user",     Context.toString(a.get("username", a)));
	p.setProperty("password", Context.toString(a.get("password", a)));
      }

      JDBCQueryHandler qh = new JDBCQueryHandler(cx, thisObj, args);

      if (args[0] instanceof Function) {
	queryCache.executeTransaction(jsuri.getURI(), p, qh);
      }
      else {
	queryCache.executeQuery(jsuri.getURI(), p, Context.toString(args[0]), qh);
      }

      return qh.getResult();
    }
    catch (SQLException ex) {
      ex.printStackTrace();
      throw Context.reportRuntimeError("SQL query failed: " + ex.getMessage());
    }
  }

  private static class JDBCQueryHandler 
    implements QueryHandler {

    public JDBCQueryHandler(Context cx, Scriptable jsthis, Object[] a) {
      this.cx = cx;
      jsThis  = jsthis;
      args    = a;
      batches = 0;

      for (int i = args.length - 1; i >= 1; --i) {
	if (args[i] != Context.getUndefinedValue()) {
	  ++batches;
	}
      }

      resultElem = "result";
      entryElem  = "entry";
      metaElem  = null; // Default: no meta element

      if (batches > 0 && args[1] instanceof Scriptable) {
	// (Only the first batch, if a Scriptable, may change the element names)

	Scriptable p = (Scriptable) args[1];
	Object     o;

	if ((o = p.get("$result", p)) != Scriptable.NOT_FOUND) {
	  resultElem = Context.toString(o);
	}

	if ((o = p.get("$entry", p)) != Scriptable.NOT_FOUND) {
	  entryElem = Context.toString(o);
	}

	if ((o = p.get("$meta", p)) != Scriptable.NOT_FOUND) {
	  metaElem = Context.toString(o);
	}
      }
    }

    public Object getResult() {
      return queryResult;
    }

    @Override public void handleTransaction()
      throws SQLException {
      Function   func = (Function) args[0];
      Scriptable thiz = func.getParentScope();

      queryResult = func.call(cx, thiz, thiz, Context.emptyArgs);
    }

    @Override public int getBatches() {
      return batches;
    }

    @Override public int getParamLength(int batch, String param) {
      Object params = args[1 + batch];
      Object object = ScriptRuntime.getObjectElem(params, param, cx);
      int    length;

      if (object instanceof Iterable) {
	Iterator i = ((Iterable) object).iterator();

	for (length = 0; i.hasNext(); i.next()) {
	  ++length;
	}
      }
      else if (object instanceof Scriptable) {
	length = ((Scriptable) object).getIds().length;
      }
      else {
	length = 1;
      }

      return length;
    }

    @Override public void resolveParam(int batch, String param, int length, 
				       Collection<Object> result) {
      Object params = args[1 + batch];
      Object object = ScriptRuntime.getObjectElem(params, param, cx);

      if (object instanceof Iterable) {
	for (Object o : ((Iterable) object)) {
	  result.add(o);
	}
      }
      else if (object instanceof Scriptable) {
	Scriptable jsobj = (Scriptable) object;

	for (Object id : jsobj.getIds()) {
	  Object o = ScriptRuntime.getObjectElem(jsobj, id, cx);

	  if (o instanceof XMLObject) {
	    o = o.toString();
	  }

	  result.add(o);
	}
      }
      else {
	result.add(object);
      }
    }

    @Override public void handleResult(int set, int uc, ResultSet rs) 
      throws SQLException {
      Document          doc   = ESXX.getInstance().createDocument(resultElem);
      Element           root  = doc.getDocumentElement();

      if (uc != -1) {
	root.setAttributeNS(null, "updateCount", Integer.toString(uc));
      }

      if (rs != null) {
	String[]          labels = null;
	StringBuilder     names  = new StringBuilder();
	StringBuilder     types  = new StringBuilder();
	StringBuilder     flags  = new StringBuilder();
	StringBuilder     precs  = new StringBuilder();
	StringBuilder     scale  = new StringBuilder();

	Element meta = (metaElem != null ? doc.createElementNS(null, metaElem) : null);

	ResultSetMetaData rmd = rs.getMetaData();
	labels = new String[rmd.getColumnCount() + 1];

	for (int i = 1; i < labels.length; ++i) {
	  labels[i] = StringUtil.makeXMLName(rmd.getColumnLabel(i).toLowerCase(), "");

	  // Add a metadata element, if requested
	  if (meta != null) {
	    Element child = XML.addChild(meta, labels[i], null);

	    String name = rmd.getColumnName(i);
	    name = addPart(rmd.getTableName(i), name);
	    name = addPart(rmd.getSchemaName(i), name);
	    name = addPart(rmd.getCatalogName(i), name);

	    String nullable = "unknown";

	    switch (rmd.isNullable(i)) {
	      case ResultSetMetaData.columnNullable:
		nullable = Boolean.toString(true);
		break;

	      case ResultSetMetaData.columnNoNulls:
		nullable = Boolean.toString(false);
		break;
	    }

	    child.setAttributeNS(null, "name", name);
	    child.setAttributeNS(null, "label", rmd.getColumnLabel(i));
	    child.setAttributeNS(null, "type", typeToString.get(rmd.getColumnType(i)));
	    child.setAttributeNS(null, "className", rmd.getColumnClassName(i));

	    child.setAttributeNS(null, "columnName", rmd.getColumnName(i));
	    child.setAttributeNS(null, "tableName", rmd.getTableName(i));
	    child.setAttributeNS(null, "schemaName", rmd.getSchemaName(i));
	    child.setAttributeNS(null, "catalogName", rmd.getCatalogName(i));

	    child.setAttributeNS(null, "isAutoIncrement", Boolean.toString(rmd.isAutoIncrement(i)));
	    child.setAttributeNS(null, "isCaseSensitive", Boolean.toString(rmd.isCaseSensitive(i)));
	    child.setAttributeNS(null, "isCurrency", Boolean.toString(rmd.isCurrency(i)));
	    child.setAttributeNS(null, "isDefinitelyWritable", Boolean.toString(rmd.isDefinitelyWritable(i)));
	    child.setAttributeNS(null, "isNullable", nullable);
	    child.setAttributeNS(null, "isReadOnly", Boolean.toString(rmd.isReadOnly(i)));
	    child.setAttributeNS(null, "isSearchable", Boolean.toString(rmd.isSearchable(i)));
	    child.setAttributeNS(null, "isSigned", Boolean.toString(rmd.isSigned(i)));
	    child.setAttributeNS(null, "isWritable", Boolean.toString(rmd.isWritable(i)));

	    child.setAttributeNS(null, "precision", Integer.toString(rmd.getPrecision(i)));
	    child.setAttributeNS(null, "scale", Integer.toString(rmd.getScale(i)));
	    child.setAttributeNS(null, "displaySize", Integer.toString(rmd.getColumnDisplaySize(i)));

	    meta.appendChild(child);
	  }
	}

	if (meta != null) {
	  root.appendChild(meta);
	}

	while (rs.next()) {
	  Element row = doc.createElementNS(null, entryElem);

	  for (int i = 1; i < labels.length; ++i) {
	    String value  = rs.getString(i);
	    Element child = XML.addChild(row, labels[i], value);

	    if (value == null) {
	      child.setAttributeNS(null, "isNull", "true");
	    }
	  }

	  root.appendChild(row);
	}
      }
      
      if (queryResult == null) {
	queryResult = cx.newObject(jsThis, "XMLList", Context.emptyArgs);
      }

      queryResult = ((XMLObject) queryResult).addValues(cx, true, ESXX.domToE4X(doc, cx, jsThis));
    }

    private static String addPart(String part_name, String name) {
      if (part_name != null && !part_name.isEmpty()) {
	return part_name + "." + name;
      }
      else {
	return name;
      }
    }

    private Context cx;
    private Scriptable jsThis;

    private Object[] args;
    private int batches;

    private String resultElem;
    private String entryElem;
    private String metaElem;

    private Object queryResult;
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
