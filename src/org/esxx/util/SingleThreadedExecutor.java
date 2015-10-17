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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class SingleThreadedExecutor
  extends AbstractExecutorService
  implements ScheduledExecutorService {

  private class STEFuture<V>
    extends DelayedFutureTask<V> {

    public STEFuture(Runnable r, V v, long time, long period) {
      super(r, v, time, period);
    }

    public STEFuture(Callable<V> c, long time, long period) {
      super(c, time, period);
    }

    @Override protected void reschedule() {
      try { 
	executeOrQueue(this); 
      } 
      catch (RejectedExecutionException ex) {
	// Just ignore
      }
    }
  }


  // From interface Executor

  @Override public void execute(Runnable r) {
    // NOTE: It's important to use System.nanoTime() and not 0 here,
    // because otherwise this request will be executed before
    // scheduled and expired jobs.
    executeOrQueue(new STEFuture<Void>(r, null, System.nanoTime(), 0));
  }


  // From interface ExecutorService

  @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
    return true;
  }

  @Override public boolean isShutdown() {
    return killed;
  }

  @Override public boolean isTerminated() {
    return isShutdown();
  }

  @Override public void shutdown() {
    executeReadyFutures();
    shutdownNow();
  }

  @Override public List<Runnable> shutdownNow() {
    killed = true;
    List<Runnable> result = Arrays.asList(delayQueue.toArray(new Runnable[0]));
    delayQueue.clear();
    return result;
  }


  // From interface ScheduledExecutorService

  @Override public ScheduledFuture<?> schedule(Runnable r, long d, TimeUnit u) {
    d = Math.max(0, d);

    return executeOrQueue(new STEFuture<Void>(r, null, System.nanoTime() + u.toNanos(d), 0));
  }

  @Override public <V> ScheduledFuture<V> schedule(Callable<V> c, long d, TimeUnit u) {
    d = Math.max(0, d);

    return executeOrQueue(new STEFuture<V>(c, System.nanoTime() + u.toNanos(d), 0));
  }

  @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long d, long p, TimeUnit u) {
    d = Math.max(0, d);

    if (p <= 0) {
      throw new IllegalArgumentException();
    }

    return executeOrQueue(new STEFuture<Void>(r, null,
					      System.nanoTime() + u.toNanos(d),
					      u.toNanos(p)));
  }

  @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable r, long d, long p, TimeUnit u) {
    d = Math.max(0, d);

    if (p <= 0) {
      throw new IllegalArgumentException();
    }

    return executeOrQueue(new STEFuture<Void>(r, null,
					      System.nanoTime() + u.toNanos(d),
					      u.toNanos(-p))); // Use negative period to mark delay
  }


  private <V> STEFuture<V> executeOrQueue(STEFuture<V> f) {
    // Add task to run queue
    if (isShutdown() || !delayQueue.offer(f)) {
      throw new RejectedExecutionException();
    }

    // Execute all expired tasks
    executeReadyFutures();

    return f;
  }

  private void executeReadyFutures() {
    List<STEFuture<?>> ready = new ArrayList<STEFuture<?>>();
    delayQueue.drainTo(ready);

    for (STEFuture<?> r : ready) {
      //      System.out.println(r + ": delay: " + r.getDelay(TimeUnit.NANOSECONDS));
      r.run();
    }
  }

  private boolean killed;
  private DelayQueue<STEFuture<?>> delayQueue = new DelayQueue<STEFuture<?>>();
}
