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

import java.util.List;
import java.util.concurrent.*;

public class SingleThreadedExecutor 
  extends AbstractExecutorService {

  public void execute(Runnable r) {
    r.run();
  }

  public boolean awaitTermination(long timeout, TimeUnit unit) {
    return true;
  }

  public void shutdown() {
    killed = true;
  }
  
  public List<Runnable> shutdownNow() {
    killed = true;
    return null;
  }

  public boolean isShutdown() {
    return killed;
  }

  public boolean isTerminated() {
    return isShutdown();
  }

  private boolean killed;
}