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
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;

public class QueryBuilder {
  public static void main(String[] args) 
    throws Exception {

    for (String a: args) {
      System.out.println("Processing dbref " + a);
      QueryBuilder qb = new QueryBuilder(new URI("#" + a));
      try { 
	System.out.println(qb.getSelectQuery(new ArrayList<String>())); 
      }
      catch (Exception ex) {
	System.out.println(ex);
      }

      try { 
	System.out.println(qb.getInsertQuery(java.util.Arrays.asList(new String[] { 
		"c1", "c2", "c3" })));
      }
      catch (Exception ex) {
	System.out.println(ex);
      }

      try { 
	System.out.println(qb.getUpdateQuery(java.util.Arrays.asList(new String[] { 
		"c1", "c2", "c3" }), new ArrayList<String>()));
      }
      catch (Exception ex) {
	System.out.println(ex);
      }

      try { 
	System.out.println(qb.getDeleteQuery(new ArrayList<String>()));
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

  public String getSelectQuery(List<String> params) 
    throws URISyntaxException {

    if (dbref.getScope() == DBReference.Scope.SCALAR && 
	dbref.getColumns().size() != 1) {
      throw new URISyntaxException(uri.toString(), 
				   "Scalar scope only works with one single column");
    }

    StringBuilder sb = new StringBuilder();

    sb.append("SELECT ");

    if (dbref.getScope() == DBReference.Scope.DISTINCT) {
      sb.append("DISTINCT ");
    }

    if (dbref.getColumns().isEmpty()) {
      sb.append("*");
    }
    else {
      append(dbref.getColumns(), ", ", sb);
    }

    sb.append(" FROM ").append(dbref.getTable());

    if (dbref.getFilter() != null) {
      sb.append(" WHERE ");
      where(dbref.getFilter(), sb, params);
    }

    return sb.toString();
  }

  public String getInsertQuery(Iterable<String> columns) 
    throws URISyntaxException {
    if (dbref.getScope() != DBReference.Scope.ALL) {
      throw new URISyntaxException(uri.toString(), dbref.getScope().toString().toLowerCase() +
				   " is not a valid scope when inserting");
    }

    if (dbref.getFilter() != null) {
      throw new URISyntaxException(uri.toString(), "Filters may not be used when inserting");
    }

    StringBuilder sb = new StringBuilder();

    sb.append("INSERT INTO ").append(dbref.getTable()).append("(");
    
    if (dbref.getColumns().isEmpty()) {
      for (String c : columns) {
	ensureValidColumnName(c);
      }

      append(columns, ", ", sb);
    }
    else {
      append(dbref.getColumns(), ", ", sb);
    }

    sb.append(") VALUES (");

    boolean first = true;
    for (String s : (dbref.getColumns().isEmpty() ? columns : dbref.getColumns())) {
      if (first) {
	first = false;
      }
      else {
	sb.append(", ");
      }

      sb.append("{").append(s).append("}");
    }

    sb.append(")");

    return sb.toString();
  }

  public String getUpdateQuery(Iterable<String> columns, List<String> params) 
    throws URISyntaxException {
    if (dbref.getScope() == DBReference.Scope.DISTINCT) {
      throw new URISyntaxException(uri.toString(), dbref.getScope().toString().toLowerCase() +
				   " is not a valid scope when updating");
    }

    if (dbref.getColumns().isEmpty() && !columns.iterator().hasNext()) {
      throw new URISyntaxException(uri.toString(), "Must have columns to update");
    }

    StringBuilder sb = new StringBuilder();

    sb.append("UPDATE ").append(dbref.getTable()).append(" SET ");

    boolean first = true;
    for (String s : (dbref.getColumns().isEmpty() ? columns : dbref.getColumns())) {
      if (first) {
	first = false;
      }
      else {
	sb.append(", ");
      }

      sb.append(s).append(" = ").append("{").append(s).append("}");
    }

    if (dbref.getFilter() != null) {
      sb.append(" WHERE ");
      where(dbref.getFilter(), sb, params);
    }

    return sb.toString();
  }


  public String getDeleteQuery(List<String> params) 
    throws URISyntaxException {
    switch (dbref.getScope()) {
      case SCALAR:
      case DISTINCT:
	throw new URISyntaxException(uri.toString(), dbref.getScope().toString().toLowerCase() +
				     " is not a valid scope when deleting");
      case ROW:
      case ALL:
	break;
    }

    if (!dbref.getColumns().isEmpty()) {
      throw new URISyntaxException(uri.toString(), "Columns may not be specified when deleting");
    }

    StringBuilder sb = new StringBuilder();

    sb.append("DELETE FROM ").append(dbref.getTable());
    
    if (dbref.getFilter() != null) {
      sb.append(" WHERE ");
      where(dbref.getFilter(), sb, params);
    }

    return sb.toString();
  }

  private void append(Iterable<String> iter, String delim, StringBuilder sb) {
    boolean first = true;

    for (String s : iter) {
      if (first) {
	first = false;
      }
      else {
	sb.append(delim);
      }

      sb.append(s);
    }
  }

  private void where(DBReference.Filter filter, StringBuilder sb, List<String> params) 
    throws URISyntaxException {
    DBReference.Filter.Op op = filter.getOp();

    sb.append("(");

    switch (op) {
      case AND:
      case OR: {
	boolean first = true;

	for (DBReference.Filter f : filter.getChildren()) {
	  if (first) {
	    first = false;
	  }
	  else {
	    sb.append(" ").append(op.toString()).append(" ");
	  }

	  where(f, sb, params);
	}

	break;
      }

      case NOT:
	if (filter.getChildren().size() != 1) {
	  throw new IllegalStateException("Filter.Op." + op + " must have exactly one child");
	}

	sb.append("NOT ");
	where(filter.getChildren().get(0), sb, params);
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
	sb.append(column);

	switch (op) {
	  case LT: sb.append(" < ");  break;
	  case LE: sb.append(" <= "); break;
	  case EQ: sb.append(" = ");  break;
	  case NE: sb.append(" != "); break;
	  case GT: sb.append(" > ");  break;
	  case GE: sb.append(" >= "); break;
	  default:
	    throw new IllegalStateException("This can't happen");
	}

	sb.append("{").append(params.size()).append("}");

	if (filter.getChildren().get(1).getOp() != DBReference.Filter.Op.VAL) {
	  throw new IllegalStateException("Filter.Op." + op + "'s second child must be VAL");
	}

	params.add(filter.getChildren().get(1).getValue());
	break;
      }

      case VAL:
	throw new IllegalStateException("Filter.Op." + op + " should have been handled already");
    }

    sb.append(")");
  }

  private void ensureValidColumnName(String name) 
    throws URISyntaxException {
    if (!strictColumnName.matcher(name).matches()) {
      throw new URISyntaxException(uri.toString(), "'" + name + "' is not a valid column name");
    }
  }

  private URI uri;
  private DBReference dbref;

  private static Pattern strictColumnName = Pattern.compile("[_A-Za-z][_A-Za-z0-9]*");
  private static Pattern strictTableName  = Pattern.compile("[_A-Za-z][_A-Za-z0-9]*" +
							    "(\\.[_A-Za-z][_A-Za-z0-9]*)*");
}
