/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2015 Martin Blom <martin@blom.org>

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

package org.esxx.util;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/** An QueryHandler implementation that uses arrays */

public class ArrayQueryHandler 
  implements QueryHandler {
    
  public ArrayQueryHandler(Object[] p) {
    this.params = p;
  }

  public ArrayList<Object[]> getResult() {
    return result;
  }

  @SuppressWarnings("unchecked")
  public <T> ArrayList<T> getColumn(int col) {
    ArrayList<T> res = new ArrayList<T>(result.size());

    for (int i = 0; i < result.size(); ++i) {
      res.add((T) result.get(i)[col]);
    }

    return res;
  }

  public void clear() {
    result = null;
  }

  public void handleTransaction() 
    throws SQLException {
    throw new SQLException("ArrayQueryHandler does not support transactions");
  }

  public int getBatches() {
    return 1;
  }

  public int getParamLength(int batch, String param) {
    if (batch != 0) {
      throw new IllegalArgumentException("ArrayQueryHandler does not support multiple batches");
    }

    Object obj = params[Integer.parseInt(param)];
    int    len = 1;

    if (obj instanceof Iterable) {
      Iterator<?> i = ((Iterable<?>) obj).iterator();

      for (len = 0; i.hasNext(); i.next()) {
	++len;
      }
    }

    return len;
  }

  @SuppressWarnings("unchecked")
  public void resolveParam(int batch, String param, int length, Collection<Object> result) {
    if (batch != 0) {
      throw new IllegalArgumentException("ArrayQueryHandler does not support multiple batches");
    }

    Object obj = params[Integer.parseInt(param)];

    if (obj instanceof Iterable) {
      for (Object o : ((Iterable) obj)) {
	result.add(o);
      }
    }
    else {
      result.add(obj);
    }
  }

  public void handleResult(int set, int update_count, ResultSet rs) 
    throws SQLException {

    if (set != 0) {
      throw new UnsupportedOperationException("ArrayQueryHandler does not support "
					      + "multiple result sets");
    }

    if (result == null) {
      result = new ArrayList<Object[]>();
    }

    while (rs != null && rs.next()) {
      Object[] row = new Object[rs.getMetaData().getColumnCount()];
	
      for (int i = 0; i < row.length; ++i) {
	row[i] = rs.getObject(i + 1);
      }

      result.add(row);
    }
  }

  private Object[] params;
  private ArrayList<Object[]> result;
}
