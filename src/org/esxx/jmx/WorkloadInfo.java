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

package org.esxx.jmx;

import java.util.Date;

public class WorkloadInfo {
  @java.beans.ConstructorProperties({"name, expires, thread, timedout, done"})
  public WorkloadInfo(String name, Date expires, String thread, boolean timedout, boolean done) {
    this.name     = name;
    this.expires  = expires;
    this.thread   = thread != null ? thread : "<Queued>";
    this.timedout = timedout;
    this.done     = done;
  }

  public String getName() {
    return name;
  }

  public Date getExpires() {
    return expires;
  }

  public String getThread() {
    return thread;
  }

  public boolean isTimedOut() {
    return timedout;
  }

  public boolean isDone() {
    return done;
  }

  private String name;
  private Date expires;
  private String thread;
  private boolean timedout;
  private boolean done;
}
