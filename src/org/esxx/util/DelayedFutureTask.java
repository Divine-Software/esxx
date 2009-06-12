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

import java.util.concurrent.*;

public abstract class DelayedFutureTask<V>
  extends FutureTask<V>
  implements RunnableScheduledFuture<V> {

  public DelayedFutureTask(Runnable r, V v, long time, long period) {
    super(r, v);
    nsTime = time;
    nsPeriod = period;
  }

  public DelayedFutureTask(Callable<V> c, long time, long period) {
    super(c);
    nsTime = time;
    nsPeriod = period;
  }

  @Override public boolean isPeriodic() {
    return nsPeriod != 0;
  }

  @Override public long getDelay(TimeUnit u) {
    return u.convert(nsTime - System.nanoTime(), TimeUnit.NANOSECONDS);
  }

  @Override public int compareTo(Delayed o) {
    return Long.signum(getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS));
  }

  @Override public void run() {
    if (nsPeriod == 0) {
      super.run();
    }
    else if (runAndReset()) {
      if (nsPeriod > 0) {
	nsTime = Math.max(System.nanoTime(), nsTime + nsPeriod);
      }
      else {
	nsTime = System.nanoTime() - nsPeriod;
      }

      // Re-schedule
      reschedule();
    }
  }

  protected abstract void reschedule();

  private long nsTime;
  private long nsPeriod;
}
