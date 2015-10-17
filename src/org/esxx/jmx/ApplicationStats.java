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

package org.esxx.jmx;

import java.util.Date;

public class ApplicationStats {
  @java.beans.ConstructorProperties({"invocations", "executionTime", "started", "lastAccessed"})  
  public ApplicationStats(long invocations, long executionTime, 
			  Date started, Date lastAccessed) {
    this.invocations   = invocations;
    this.executionTime = executionTime;
    this.started       = started;
    this.lastAccessed  = lastAccessed;
  }

  @Units("requests")
  public long getInvocations() {
    return invocations;
  }

  @Units("wall clock milliseconds") public long getTotalExecutionTime() {
    return executionTime;
  }

  @Units("wall clock milliseconds") public long getMeanExecutionTime() {
    return invocations == 0 ? 0 : executionTime / invocations;
  }

  public Date getStarted() {
    return started;
  }

  public Date getLastAccessed() {
    return lastAccessed;
  }

  private long invocations;
  private long executionTime;
  private Date started;
  private Date lastAccessed;
}
