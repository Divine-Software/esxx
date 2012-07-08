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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.mail.internet.ContentType;
import org.esxx.*;
import org.esxx.dbref.QueryBuilder;
import org.esxx.js.JSURI;
import org.esxx.util.*;
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

  @Override public Object load(Context cx, Scriptable thisObj,
			       ContentType recv_ct)
    throws URISyntaxException {
    recv_ct = ensureRecvTypeIsXML(recv_ct);

    QueryBuilder        builder = new QueryBuilder(jsuri.getURI());
    List<String>        args    = new ArrayList<String>();
    Map<String, String> params  = new HashMap<String, String>();
    String              query   = builder.getSelectQuery(args, params);
    DBReference.Scope   qscope  = builder.getSelectScope();

    Object[] query_params createQueryParams(cx, thisObj, DBReference.Scope.ROW, qscope, query, args, null, null, params);
    ensureRequiredParamsHandled(builder, params);

    return this.query(cx, thisObj, query_params);
  }

  @Override public Object save(Context cx, Scriptable thisObj,
			       Object data, ContentType send_ct, ContentType recv_ct)
    throws URISyntaxException {
    recv_ct = ensureRecvTypeIsXML(recv_ct);

    final PropertyBag qdata = PropertyBag.create(cx, thisObj, data);
    List<String> columns = getColumns(qdata);

    QueryBuilder.ColumnGetter cg = new QueryBuilder.ColumnGetter() {
	public Object get(String key) {
	  return qdata.getValue(key);
	}
      };

    QueryBuilder        builder = new QueryBuilder(jsuri.getURI());
    List<String>        args    = new ArrayList<String>();
    Map<String, String> params  = new HashMap<String, String>();
    String              query   = builder.getMergeQuery(columns, cg, args, params);
    DBReference.Scope   qscope  = builder.getMergeScope();

    Object[] query_params createQueryParams(cx, thisObj, qscope, DBReference.Scope.ALL, query, args, columns, qdata, params);
    ensureRequiredParamsHandled(builder, params);

    return this.query(cx, thisObj, query_params);
  }

  @Override public Object append(Context cx, Scriptable thisObj,
				 Object data, ContentType send_ct, ContentType recv_ct)
    throws URISyntaxException {
    recv_ct = ensureRecvTypeIsXML(recv_ct);

    PropertyBag qdata = PropertyBag.create(cx, thisObj, data);
    List<String> columns = getColumns(qdata);

    QueryBuilder        builder = new QueryBuilder(jsuri.getURI());
    Map<String, String> params  = new HashMap<String, String>();
    String              query   = builder.getInsertQuery(columns, params);
    DBReference.Scope   qscope  = builder.getInsertScope();

    Object[] query_params createQueryParams(cx, thisObj, qscope, DBReference.Scope.ALL, query, null, columns, qdata, params);
    ensureRequiredParamsHandled(builder, params);

    return this.query(cx, thisObj, query_params);
  }

  @Override public Object modify(Context cx, Scriptable thisObj,
				 Object data, ContentType send_ct, ContentType recv_ct)
    throws URISyntaxException {
    recv_ct = ensureRecvTypeIsXML(recv_ct);

    PropertyBag qdata = PropertyBag.create(cx, thisObj, data);
    List<String> columns = getColumns(qdata);

    QueryBuilder        builder = new QueryBuilder(jsuri.getURI());
    List<String>        args    = new ArrayList<String>();
    Map<String, String> params  = new HashMap<String, String>();
    String              query   = builder.getUpdateQuery(columns, args, params);
    DBReference.Scope   qscope  = builder.getUpdateScope();

    Object[] query_params createQueryParams(cx, thisObj, qscope, DBReference.Scope.ALL, query, args, columns, qdata, params);
    ensureRequiredParamsHandled(builder, params);

    return this.query(cx, thisObj, query_params);
  }

  @Override public Object remove(Context cx, Scriptable thisObj,
				 ContentType recv_ct)
    throws URISyntaxException {
    recv_ct = ensureRecvTypeIsXML(recv_ct);

    QueryBuilder        builder = new QueryBuilder(jsuri.getURI());
    List<String>        args    = new ArrayList<String>();
    Map<String, String> params  = new HashMap<String, String>();
    String              query   = builder.getDeleteQuery(args, params);
    DBReference.Scope   qscope  = builder.getDeleteScope();

    Object[] query_params createQueryParams(cx, thisObj, qscope, DBReference.Scope.ALL, query, args, null, null, params);
    ensureRequiredParamsHandled(builder, params);

    return this.query(cx, thisObj, query_params);
  }

  @Override public Object query(Context cx, Scriptable thisObj, Object[] args) {
    try {
      if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
	throw Context.reportRuntimeError("Missing query argument");
      }

      JDBCQueryHandler qh;

      if (args[0] instanceof Function) {
	qh = new JDBCQueryHandler(cx, thisObj, (Function) args[0]);
	queryCache.executeTransaction(getJDBCURI(), getJDBCProperties(cx), qh);
      }
      else {
	int    batches = 0;
	String result  = null;
	String entry   = null;
	String meta    = null;

	for (int i = args.length - 1; i >= 1; --i) {
	  ++batches;
	}

	if (args.length > 1) {
	  // (Only the first batch may change the element names)

	  PropertyBag p = PropertyBag.create(cx, thisObj, args[1]);

	  result = JS.toStringOrNull(p.getValue("$result"));
	  entry  = JS.toStringOrNull(p.getValue("$entry"));
	  meta   = JS.toStringOrNull(p.getValue("$meta"));
	}

	Object[] data = new Object[batches];
	System.arraycopy(args, 1, data, 0, batches);

	qh = new JDBCQueryHandler(cx, thisObj, data, result, entry, meta);

	queryCache.executeQuery(getJDBCURI(), getJDBCProperties(cx),
				Context.toString(args[0]), qh);
      }

      return qh.getResult();
    }
    catch (SQLException ex) {
      throw Context.reportRuntimeError("SQL query failed: " + ex.getMessage());
    }
  }

  private void ensureRequiredParamsHandled(QueryBuilder builder, Map<String, String> params) {
    String required = builder.findRequiredParam(params);

    if (required != null) {
      throw Context.reportRuntimeError("The required parameter '" + required + "' is unknown");
    }
  }

  private List<String> getColumns(PropertyBag qdata) {
    List<String> columns = new ArrayList<String>();

    for (Iterator<Object> k = qdata.getKeys().iterator(), v = qdata.getValues().iterator();
	 k.hasNext() && v.hasNext(); ) {
      Object key   = k.next();
      Object value = v.next();

      if (key instanceof String && value != Context.getUndefinedValue()) {
	columns.add((String) key);
      }
    }

    return columns;
  }

  private Object[] createQueryParams(Context cx, Scriptable scope,
				     DBReference.Scope iscope, DBReference.Scope oscope, String query,
				     List<String> args, List<String> columns, PropertyBag qdata,
				     Map<String, String> params) {
    switch (iscope) {
      case SCALAR: {
	if (columns.size() != 1) {
	  throw new IllegalArgumentException(iscope.toString().toLowerCase() + " requires one single column");
	}

	Map<String, Object> colValue = new TreeMap<String, Object>();
	colValue.put(columns.get(0), qdata.unwrap());
	qdata = PropertyBag.create(cx, scope, colValue);

	return new Object[] { query, createParamObject(args, columns, qdata, params, oscope) };
      }

      case COLUMN:
	throw new IllegalArgumentException(iscope.toString().toLowerCase() + " is not a valid scope when inserting data");

      case ROW:
	return new Object[] { query, createParamObject(args, columns, qdata, params, oscope) };

      case ALL: {
	get all values
	  create array
	  populate array
      }
    }

  }

  private Map<String, Object> createParamObject(List<String> args, List<String> columns, PropertyBag qdata,
						Map<String, String> params, DBReference.Scope oscope) {
    Map<String, Object> qparams = new HashMap<String, Object>();

    // Add QueryBuilder-generated arguments ("integer" keys)
    if (args != null) {
      for (int i = 0; i < args.size(); ++i) {
	qparams.put(Integer.toString(i), args.get(i));
      }
    }

    // Add user-provided arguments (string keys)
    if (columns != null) {
      for (String c : columns) {
	qparams.put(c, qdata.getValue(c));
      }
    }

    // Add params from the extensions part of the fragment
    String result = params.remove("result");
    String entry  = params.remove("entry");
    String meta   = params.remove("meta");

    if (result != null) qparams.put("$result", result);
    if (entry != null) qparams.put("$entry", entry);
    if (meta != null) qparams.put("$meta", meta);

    qparams.put("$scope", oscope);

    return qparams;
  }

  private URI getJDBCURI() {
    URI full = jsuri.getURI();

    try {
      return new URI(full.getScheme(), full.getSchemeSpecificPart(), null /* Remove fragment */);
    }
    catch (URISyntaxException ignored) {
      return full; // Should never happen
    }
  }

  private Properties getJDBCProperties(Context  cx) {
    Properties p = jsuri.getParams(cx, getJDBCURI());
    Scriptable a = jsuri.getAuth(cx, getJDBCURI(), null, null);

    if (a != null) {
      p.setProperty("user",     Context.toString(a.get("username", a)));
      p.setProperty("password", Context.toString(a.get("password", a)));
    }

    return p;
  }

  private static class JDBCQueryHandler
    implements QueryHandler {

    public JDBCQueryHandler(Context cx, Scriptable jsthis, Function f) {
      this.cx  = cx;
      jsThis   = jsthis;
      function = f;
    }

    public JDBCQueryHandler(Context cx, Scriptable jsthis, Object[] b,
			    String result, String entry, String meta) {
      this.cx    = cx;
      jsThis     = jsthis;
      batches    = b;
      resultElem = result == null ? "result" : result;
      entryElem  = entry == null ? "entry" : entry;
      metaElem   = meta == null ? null /* Default: no meta element */ : meta;
    }

    public Object getResult() {
      return queryResult;
    }

    @Override public void handleTransaction()
      throws SQLException {
      Scriptable thiz = function.getParentScope();
      queryResult = function.call(cx, thiz, thiz, Context.emptyArgs);
    }

    @Override public int getBatches() {
      return batches.length;
    }

    @Override public int getParamLength(int batch, String param) {
      if (batch >= batches.length) {
	throw Context.reportRuntimeError("No parameter object for batch " + batch + ", param " + param);
      }

      PropertyBag params = PropertyBag.create(cx, jsThis, batches[batch]);
      PropertyBag object = PropertyBag.create(cx, jsThis, params.getValue(param));
      int         length = object.getKeys().size();

      // System.out.println("getParamLength " + batch + " " + param
      // 			 + " from " + params + " => " + object + " length " + length);

      return length;
    }

    @Override public void resolveParam(int batch, String param, int length,
				       Collection<Object> result) {
      PropertyBag params = PropertyBag.create(cx, jsThis, batches[batch]);
      PropertyBag object = PropertyBag.create(cx, jsThis, params.getDefinedValue(param));

      // System.out.println("resolveParam " + batch + " " + param + " " + length
      //			 + " from " + params + " => " + object + " "
      //			 + (object != null ? object.getClass() : "null.class"));

      for (Object o : object.getValues()) {
	if (o instanceof XMLObject) {
	  o = o.toString();
	}
	else {
	  o = JS.toJavaObject(o, true);
	}

	result.add(o);
      }
    }

    @Override public void handleResult(int set, int uc, ResultSet rs)
      throws SQLException {
      QueryResult qr;

      if (rs == null) {
	qr = new QueryResult(set, uc, 0);
      }
      else {
	ResultSetMetaData rmd = rs.getMetaData();

	qr = new QueryResult(set, uc, rmd.getColumnCount());

	for (int i = 1; i < rmd.getColumnCount() + 1; ++i) {
	  qr.addMeta(rmd.getColumnLabel(i).toLowerCase(),
		     metaElem == null ? null : getMetaProps(rmd, i));
	}

	while (rs.next()) {
	  List<Object> row = new ArrayList<Object>(rmd.getColumnCount());

	  for (int i = 1; i < rmd.getColumnCount() + 1; ++i) {
	    String value = rs.getString(i);

	    if (rmd.getColumnType(i) == Types.BOOLEAN) {
	      value = value.toLowerCase();
	    }

	    row.add(value);
	  }

	  qr.addRow(row);
	}
      }

      if (queryResult == null) {
	queryResult = cx.newObject(jsThis, "XMLList", Context.emptyArgs);
      }

      queryResult = ((XMLObject) queryResult).addValues(cx, true,
							ESXX.domToE4X(qr.toXML(resultElem, entryElem, metaElem),
								      cx, jsThis));
    }

    private static KeyValueList<String, Object> getMetaProps(ResultSetMetaData rmd, int i)
      throws SQLException {
      KeyValueList<String, Object> props = new KeyValueList<String, Object>();

      String name = rmd.getColumnName(i);
      name = addPart(rmd.getTableName(i), name);
      name = addPart(rmd.getSchemaName(i), name);
      name = addPart(rmd.getCatalogName(i), name);

      Object nullable = Context.getUndefinedValue();

      switch (rmd.isNullable(i)) {
	case ResultSetMetaData.columnNullable:
	  nullable = true;
	  break;

	case ResultSetMetaData.columnNoNulls:
	  nullable = false;
	  break;
      }

      String label = rmd.getColumnLabel(i);

      props.add("nodeType", !label.isEmpty() && label.charAt(0) == '@' ? "attribute" : "element");
      props.add("name", name);
      props.add("label", label);
      props.add("type", typeToString.get(rmd.getColumnType(i)));
      props.add("className", rmd.getColumnClassName(i));

      props.add("columnName", rmd.getColumnName(i));
      props.add("tableName", rmd.getTableName(i));
      props.add("schemaName", rmd.getSchemaName(i));
      props.add("catalogName", rmd.getCatalogName(i));

      props.add("isAutoIncrement", rmd.isAutoIncrement(i));
      props.add("isCaseSensitive", rmd.isCaseSensitive(i));
      props.add("isCurrency", rmd.isCurrency(i));
      props.add("isDefinitelyWritable", rmd.isDefinitelyWritable(i));
      props.add("isNullable", nullable);
      props.add("isReadOnly", rmd.isReadOnly(i));
      props.add("isSearchable", rmd.isSearchable(i));
      props.add("isSigned", rmd.isSigned(i));
      props.add("isWritable", rmd.isWritable(i));

      props.add("precision", rmd.getPrecision(i));
      props.add("scale", rmd.getScale(i));
      props.add("displaySize", rmd.getColumnDisplaySize(i));

      return props;
    }

    private static String addPart(String part_name, String name) {
      if (part_name != null && !part_name.isEmpty()) {
	return part_name + "." + name;
      }
      else {
	return name;
      }
    }

    private static class QueryResult {
      public QueryResult(int set, int uc, int cc) {
	this.set    = set;
	updateCount = uc;

	columns = new ArrayList<String>(cc);
	rows    = new ArrayList<List<Object>>();
      }

      public void addMeta(String column, KeyValueList<String, Object> props) {
	columns.add(column);

	if (props != null) {
	  if (colProps == null) {
	    colProps = new ArrayList<KeyValueList<String, Object>>(columns.size());
	  }

	  colProps.add(props);
	}
      }

      public void addRow(List<Object> row) {
	rows.add(row);
      }

      public Element toXML(String resultElem, String entryElem, String metaElem) {
	Document doc    = ESXX.getInstance().createDocument(resultElem);
	Element  result = doc.getDocumentElement();

	// Add @updateCount to result element

	if (updateCount != -1) {
	  result.setAttributeNS(null, "updateCount", Integer.toString(updateCount));
	}

	// Add a <meta> element, if requested

	if (colProps != null) {
	  Element meta = XML.addChild(result, metaElem, null);

	  for (int i = 0; i < columns.size(); ++i) {
	    String  label   = columns.get(i);
	    String  colname = StringUtil.makeXMLName(label, "");
	    Element column  = XML.addChild(meta, colname, null);

	    for (Map.Entry<String, Object> e : colProps.get(i)) {
	      column.setAttributeNS(null, e.getKey(), e.getValue().toString());
	    }
	  }
	}

	// Add rows/entries

	for (List<Object> row : rows) {
	  Element entry  = XML.addChild(result, entryElem, null);
	  Element column = null;

	  for (int i = 0; i < columns.size(); ++i) {
	    String label   = columns.get(i);
	    String colname = StringUtil.makeXMLName(label, "");
	    Object ovalue  = row.get(i);
	    String value   = ovalue == null ? null : ovalue.toString();

	    if (!label.isEmpty() && label.charAt(0) == '@') {
	      if (column != null) {
		column.setAttributeNS(null, colname, value);
	      }
	      else {
		throw Context.reportRuntimeError("No previous element to attach attribute " + label + " to");
	      }
	    }
	    else {
	      column = XML.addChild(entry, colname, value);

	      if (value == null) {
		column.setAttributeNS(null, "isNull", "true");
	      }
	    }
	  }
	}

	return result;
      }

      private int set;
      private int updateCount;

      private List<String> columns;
      private List<List<Object>> rows;
      private List<KeyValueList<String, Object>> colProps;
    }

    private Context cx;
    private Scriptable jsThis;

    private Function function;

    private Object[] batches;
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
