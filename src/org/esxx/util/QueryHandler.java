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

package org.esxx.util;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Callback interface used by executeQuery() and executeTransaction() */

public interface QueryHandler {
  /** Once a transaction is initiated, this method will be called.
   *
   *  Once this method has been called, the transaction will be
   *  committed (unless this method throws an exception, in which
   *  case the tranaction will be rolled back.
   *
   *  @throw SQLException Any exception thrown will trigger a rollback.
   */

  public void handleTransaction()
    throws SQLException;

  /** Resolve a named parameter.
   *
   *  This method will be called by executeQuery() to resolve a
   *  named {...} parameter in the SQL query.
   *
   *  @param param  The name of the parameter to be resolved.
   *
   *  @return  An Object suitable as value in PreparedStatement.setObject().
   *
   *  @throw SQLException May be thrown, and will be propagated back
   *  from executeQuery()
   */

  public Object resolveParam(String param)
    throws SQLException;

  /** Transform SQL result.
   *
   *  This method transforms the result of an SQL query. It will be
   *  called once for each result set.
   *
   *  @param update_count  The result of Statement.getUpdateCount();
   *  @para  result        The result of Statement.getResultSet() or getGeneratedKeys().
   *
   *  @throw SQLException May be thrown, and will be propagated back
   *  from executeQuery().
   */

  public void handleResult(int update_count, ResultSet result) 
    throws SQLException;
}
