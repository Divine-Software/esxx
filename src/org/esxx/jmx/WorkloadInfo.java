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
  @java.beans.ConstructorProperties({"name, expires, thread, done, cancelled"})
  public WorkloadInfo(String name, Date expires, String thread, boolean done, boolean cancelled) {
    this.name      = name;
    this.expires   = expires;
    this.thread    = thread != null ? thread : "<Queued>";
    this.done      = done;
    this.cancelled = cancelled;
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

  public boolean isDone() {
    return done;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  private String name;
  private Date expires;
  private String thread;
  private boolean done;
  private boolean cancelled;
}
