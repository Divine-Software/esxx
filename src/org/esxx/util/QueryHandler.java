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

import java.util.Collection;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Callback interface used by executeQuery() and executeTransaction() */

public interface QueryHandler {
  /** Once a transaction is initiated, this method will be called.
   *
   *  Once this method has been called, the transaction will be
   *  committed (unless this method throws an exception, in which
   *  case the transaction will be rolled back.
   *
   *  @throw SQLException Any exception thrown will trigger a rollback.
   */

  public void handleTransaction()
    throws SQLException;

  /** Return the number of batches.
   *
   *  @return How many batches there are (1 means no batches)
   */

  public int getBatches();

  /** Return the length of a named parameter.
   *
   *  If the given parameter is an array or some other collection,
   *  this method should return the number of members. For plain
   *  objects, it should return 1.
   *
   *  @param batch  The batch ID (0 is the first batch or no batch)
   *  @param param  The name of the parameter.
   *
   *  @return The length of the parameter (1 for plain params)
   */

  public int getParamLength(int batch, String param);

  /** Resolve a named parameter.
   *
   *  This method will be called by executeQuery() to resolve a
   *  named {...} parameter in the SQL query.
   *
   *  @param batch   The batch ID (0 is the first batch or no batch)
   *  @param param   The name of the parameter to be resolved.
   *  @param length  The expected length (as it was reported in
   *                 #getParamLength().
   *  @param result  The params should be appended to this Collection,
   *                 as objects suitable as value in PreparedStatement.setObject().
   *
   */

  public void resolveParam(int batch, String param, int length, Collection<Object> result);

  /** Transform SQL result.
   *
   *  This method transforms the result of an SQL query. It will be
   *  called once for each result set.
   *
   *  @param set           The result set number, starting at 0.
   *  @param update_count  The result of Statement.getUpdateCount();
   *  @param result        The result of Statement.getResultSet() or getGeneratedKeys(). 
   *                       May be null.
   *
   *  @throw SQLException May be thrown, and will be propagated back
   *  from executeQuery().
   */

  public void handleResult(int set, int update_count, ResultSet result) 
    throws SQLException;
}
