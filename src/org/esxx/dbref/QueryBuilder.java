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

package org.esxx.dbref;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class QueryBuilder {
  public static void main(String[] args)
    throws Exception {

    for (String a: args) {
      System.out.println("Processing dbref " + a);
      QueryBuilder qb = new QueryBuilder(new URI("#" + a));
      List<String>        result = new ArrayList<String>();
      Map<String, String> params = new HashMap<String, String>();

      try {
	System.out.println(qb.getSelectQuery(result, params));
      }
      catch (Exception ex) {
	System.out.println(ex);
      }

      try {
	System.out.println(qb.getInsertQuery(java.util.Arrays.asList(new String[] {
		"c1", "c2", "c3" }), params));
      }
      catch (Exception ex) {
	System.out.println(ex);
      }

      try {
	System.out.println(qb.getUpdateQuery(java.util.Arrays.asList(new String[] {
		"c1", "c2", "c3" }), result, params));
      }
      catch (Exception ex) {
	System.out.println(ex);
      }

      try {
	System.out.println(qb.getDeleteQuery(result, params));
      }
      catch (Exception ex) {
	System.out.println(ex);
      }
    }
  }

  public QueryBuilder(URI uri)
    throws URISyntaxException {

    this.uri = uri;
    dbref = new DBReference(uri.getRawFragment());

    String table = dbref.getTable();

    if (table == null) {
      throw new URISyntaxException(uri.toString(), "Table name missing from URI fragment part");
    }
    else if (!strictTableName.matcher(table).matches()) {
      throw new URISyntaxException(uri.toString(), "'" + table + "' is not a valid table name");
    }

    for (String c : dbref.getColumns()) {
      ensureValidColumnName(c);
    }
  }

  public DBReference getParsedReference() {
    return dbref;
  }

  public boolean isRequiredParam(String param) {
    return dbref.getRequiredParams().containsKey(param);
  }

  public String findRequiredParam(Map<String, String> params) {
    for (String key : params.keySet()) {
      if (isRequiredParam(key)) {
	return key;
      }
    }

    return null;
  }

  public String getSelectQuery(List<String> args, Map<String, String> unhandled_params)
    throws URISyntaxException {
    DBReference.Scope scope = dbref.getScope(DBReference.Scope.ALL);

    args.clear();
    unhandled_params.clear();
    unhandled_params.putAll(dbref.getOptionalParams());
    unhandled_params.putAll(dbref.getRequiredParams());

    String order   = unhandled_params.remove("order");
    String reverse = unhandled_params.remove("reverse");
    String offset  = unhandled_params.remove("offset");
    String count   = unhandled_params.remove("count");

    if ((scope == DBReference.Scope.SCALAR || scope == DBReference.Scope.COLUMN) &&
	dbref.getColumns().size() != 1) {
      throw new URISyntaxException(uri.toString(),
				   "Scalar and column scopes only works with one single column");
    }

    QueryBuffer qb = new QueryBuffer(uri.getSchemeSpecificPart());

    qb.append("SELECT ");

    if (dbref.getColumns().isEmpty()) {
      qb.append("*");
    }
    else {
      sequence(dbref.getColumns(), true, false, qb);
    }

    qb.append(" FROM ").appendTable(dbref.getTable());

    if (dbref.getFilter() != null) {
      qb.append(" WHERE ");
      where(dbref.getFilter(), qb, args);
    }

    orderBy(order, reverse, qb);
    offsetCount(offset, count, qb);

    return qb.toString();
  }

  public String getInsertQuery(Iterable<String> columns, Map<String, String> unhandled_params)
    throws URISyntaxException {
    DBReference.Scope scope = dbref.getScope(DBReference.Scope.ROW);

    unhandled_params.clear();
    unhandled_params.putAll(dbref.getOptionalParams());
    unhandled_params.putAll(dbref.getRequiredParams());

    if (dbref.getColumns().isEmpty()) {
      for (String c : columns) {
	ensureValidColumnName(c);
      }
    }
    else {
      columns = dbref.getColumns();
    }

    if (scope != DBReference.Scope.ROW && scope != DBReference.Scope.ALL) {
      throw new URISyntaxException(uri.toString(), scope.toString().toLowerCase() +
				   " is not a valid scope when inserting");
    }

    if (dbref.getFilter() != null) {
      throw new URISyntaxException(uri.toString(), "Filters may not be used when inserting");
    }

    QueryBuffer qb = new QueryBuffer(uri.getSchemeSpecificPart());

    qb.append("INSERT INTO ").appendTable(dbref.getTable()).append(" (");
    sequence(columns, true, false, qb);
    qb.append(") VALUES (");
    sequence(columns, false, true, qb);
    qb.append(")");

    return qb.toString();
  }

  public String getUpdateQuery(Iterable<String> columns,
			       List<String> args, Map<String, String> unhandled_params)
    throws URISyntaxException {
    DBReference.Scope scope = dbref.getScope(DBReference.Scope.ROW);

    args.clear();
    unhandled_params.clear();
    unhandled_params.putAll(dbref.getOptionalParams());
    unhandled_params.putAll(dbref.getRequiredParams());

    if (dbref.getColumns().isEmpty()) {
      for (String c : columns) {
	ensureValidColumnName(c);
      }
    }
    else {
      columns = dbref.getColumns();
    }

    if (scope != DBReference.Scope.SCALAR && scope != DBReference.Scope.ROW && scope != DBReference.Scope.ALL) {
      throw new URISyntaxException(uri.toString(), scope.toString().toLowerCase() +
				   " is not a valid scope when updating");
    }

    if (!columns.iterator().hasNext()) {
      throw new URISyntaxException(uri.toString(), "No columns to update");
    }

    QueryBuffer qb = new QueryBuffer(uri.getSchemeSpecificPart());

    qb.append("UPDATE ").appendTable(dbref.getTable()).append(" SET ");
    sequence(columns, true, true, qb);

    if (dbref.getFilter() != null) {
      qb.append(" WHERE ");
      where(dbref.getFilter(), qb, args);
    }

    return qb.toString();
  }


  public String getDeleteQuery(List<String> args, Map<String, String> unhandled_params)
    throws URISyntaxException {
    DBReference.Scope scope = dbref.getScope(DBReference.Scope.ALL);

    args.clear();
    unhandled_params.clear();
    unhandled_params.putAll(dbref.getOptionalParams());
    unhandled_params.putAll(dbref.getRequiredParams());

    if (scope != DBReference.Scope.ALL) {
      throw new URISyntaxException(uri.toString(), scope.toString().toLowerCase() +
				   " is not a valid scope when deleting");
    }

    if (!dbref.getColumns().isEmpty()) {
      throw new URISyntaxException(uri.toString(), "Columns may not be specified when deleting");
    }

    QueryBuffer qb = new QueryBuffer(uri.getSchemeSpecificPart());

    qb.append("DELETE FROM ").appendTable(dbref.getTable());

    if (dbref.getFilter() != null) {
      qb.append(" WHERE ");
      where(dbref.getFilter(), qb, args);
    }

    return qb.toString();
  }

  public interface ColumnGetter {
    public Object get(String key);
  }

  public String getMergeQuery(Iterable<String> columns, ColumnGetter cg,
			      List<String> args, Map<String, String> unhandled_params)
    throws URISyntaxException {
    DBReference.Scope scope = dbref.getScope(DBReference.Scope.ROW);

    unhandled_params.clear();
    unhandled_params.putAll(dbref.getOptionalParams());
    unhandled_params.putAll(dbref.getRequiredParams());

    String key = unhandled_params.remove("key");

    if (key == null) {
      throw new URISyntaxException(uri.toString(), "Missing 'key' parameter");
    }
    
    ensureValidColumnName(key);

    if (dbref.getColumns().isEmpty()) {
      for (String c : columns) {
	ensureValidColumnName(c);
      }
    }
    else {
      columns = dbref.getColumns();
    }

    if (scope != DBReference.Scope.ROW && scope != DBReference.Scope.ALL) {
      throw new URISyntaxException(uri.toString(), scope.toString().toLowerCase() +
				   " is not a valid scope when merging");
    }

    if (dbref.getFilter() != null) {
      throw new URISyntaxException(uri.toString(), "Filters may not be used when merging");
    }

    String     ssp = uri.getSchemeSpecificPart();
    QueryBuffer qb = new QueryBuffer(ssp);

    if (ssp.startsWith("h2:")) {
      qb.append("MERGE INTO ").appendTable(dbref.getTable()).append(" (");
      sequence(columns, true, false, qb);
      qb.append(") KEY (").appendColumn(key).append(") VALUES (");
      sequence(columns, false, true, qb);
      qb.append(")");
    }
    else if (ssp.startsWith("mysql:")) {
      qb.append("INSERT INTO ").appendTable(dbref.getTable()).append(" (");
      sequence(columns, true, false, qb);
      qb.append(") VALUES (");
      sequence(columns, false, true, qb);
      qb.append(") ON DUPLICATE KEY UPDATE ");
      sequence(columns, true, true, qb);
    }
    else {
      args.add(cg.get(key).toString());

      qb.append("MERGE INTO ").appendTable(dbref.getTable())
	.append(" USING ").appendTable(dbref.getTable())
	.append(" ON ").appendColumn(key).append(" = {0}")
	.append(" WHEN MATCHED THEN UPDATE SET ");
      sequence(columns, true, true, qb);
      qb.append(" WHEN NOT MATCHED THEN INSERT (");
      sequence(columns, true, false, qb);
      qb.append(") VALUES (");
      sequence(columns, false, true, qb);
      qb.append(")");
    }

    return qb.toString();
  }


  private void sequence(Iterable<String> iter, boolean col, boolean ref, QueryBuffer qb) {
    boolean first = true;

    for (String s : iter) {
      if (first) {
	first = false;
      }
      else {
	qb.append(", ");
      }

      if (col) {
	qb.appendColumn(s);
      }

      if (col && ref) {
	qb.append(" = ");
      }

      if (ref) {
        qb.append("{").append(s).append("}");
      }
    }
  }

  private void where(DBReference.Filter filter, QueryBuffer qb, List<String> args)
    throws URISyntaxException {
    DBReference.Filter.Op op = filter.getOp();

    qb.append("(");

    switch (op) {
    case AND:
    case OR: {
      boolean first = true;

      for (DBReference.Filter f : filter.getChildren()) {
	if (first) {
	  first = false;
	}
	else {
	  qb.append(" ").append(op.toString()).append(" ");
	}

	where(f, qb, args);
      }

      break;
    }

    case NOT:
      if (filter.getChildren().size() != 1) {
	throw new IllegalStateException("Filter.Op." + op + " must have exactly one child");
      }

      qb.append("NOT ");
      where(filter.getChildren().get(0), qb, args);
      break;

    case LT:
    case LE:
    case EQ:
    case NE:
    case GT:
    case GE: {
      if (filter.getChildren().size() != 2 ||
	  filter.getChildren().get(0).getOp() != DBReference.Filter.Op.VAL ||
	  filter.getChildren().get(1).getOp() != DBReference.Filter.Op.VAL) {
	throw new IllegalStateException("Filter.Op." + op + " must have exactly two VAL children");
      }

      String column = filter.getChildren().get(0).getValue();
      ensureValidColumnName(column);
      qb.appendColumn(column);

      switch (op) {
      case LT: qb.append(" < ");  break;
      case LE: qb.append(" <= "); break;
      case EQ: qb.append(" = ");  break;
      case NE: qb.append(" != "); break;
      case GT: qb.append(" > ");  break;
      case GE: qb.append(" >= "); break;
      default:
	throw new IllegalStateException("This can't happen");
      }

      qb.append("{").append(args.size()).append("}");

      if (filter.getChildren().get(1).getOp() != DBReference.Filter.Op.VAL) {
	throw new IllegalStateException("Filter.Op." + op + "'s second child must be VAL");
      }

      args.add(filter.getChildren().get(1).getValue());
      break;
    }

    case VAL:
      throw new IllegalStateException("Filter.Op." + op + " should have been handled already");
    }

    qb.append(")");
  }

  private void orderBy(String order, String reverse, QueryBuffer qb)
    throws URISyntaxException {
    if (order != null) {
      ensureValidColumnName(order);
      qb.append(" ORDER BY ").appendColumn(order);
    }

    if ("".equals(reverse) || Boolean.parseBoolean(reverse)) {
      qb.append(" DESC");
    }
  }

  private void offsetCount(String offset, String count, QueryBuffer qb) {
    boolean use_offset_limit = useLimitOffset.matcher(uri.getSchemeSpecificPart()).matches();

    if (use_offset_limit) {
      if (count != null) {
	qb.append(" LIMIT ").append(Integer.parseInt(count));
      }

      if (offset != null) {
	qb.append(" OFFSET ").append(Integer.parseInt(offset));
      }
    }
    else {
      if (offset != null) {
	qb.append(" OFFSET ").append(Integer.parseInt(offset)).append(" ROWS");
      }

      if (count != null) {
	qb.append(" FETCH FIRST ").append(Integer.parseInt(count)).append(" ROWS ONLY");
      }
    }
  }

  private void ensureValidColumnName(String name)
    throws URISyntaxException {
    if (!strictColumnName.matcher(name).matches()) {
      throw new URISyntaxException(uri.toString(), "'" + name + "' is not a valid column name");
    }
  }

  private static class QueryBuffer {
    public QueryBuffer(String ssp) {
      sb = new StringBuffer();
      sq = '`';
      eq = '`';
    }

    public QueryBuffer append(char c) {
      sb.append(c);
      return this;
    }

    public QueryBuffer append(int i) {
      sb.append(i);
      return this;
    }

    public QueryBuffer append(String s) {
      sb.append(s);
      return this;
    }

    public QueryBuffer appendColumn(String s) {
      sb.append(sq).append(s).append(eq);
      return this;
    }

    public QueryBuffer appendTable(String s) {
      sb.append(sq).append(s).append(eq);
      return this;
    }

    @Override public String toString() {
      return sb.toString();
    }

    private StringBuffer sb;
    private char sq, eq;
  }

  private URI uri;
  private DBReference dbref;

  private static Pattern useLimitOffset   = Pattern.compile("(h2|mysql|postgresql):.*");
  private static Pattern strictColumnName = Pattern.compile("[_A-Za-z][-_A-Za-z0-9]*");
  private static Pattern strictTableName  = Pattern.compile("[_A-Za-z][-_A-Za-z0-9]*" +
							    "(\\.[_A-Za-z][-_A-Za-z0-9]*)*");
}
