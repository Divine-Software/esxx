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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class ThreadSafeExecutor
  extends ThreadPoolExecutor
  implements ScheduledExecutorService {

  private class TSFuture<V>
    extends DelayedFutureTask<V> {

    public TSFuture(Runnable r, V v, long time, long period) {
      super(r, v, time, period);
    }

    public TSFuture(Callable<V> c, long time, long period) {
      super(c, time, period);
    }

    @Override protected void reschedule() {
      if (!isShutdown()) {
	queueDelayed(this);
      }
    }
  }


  /** Create an unbounded thread pool */

  public ThreadSafeExecutor(ThreadFactory tf) {
    super(1, Integer.MAX_VALUE,
	  60L, TimeUnit.SECONDS,
	  new SynchronousQueue<Runnable>(),
	  tf);
    startDelayedWorker();
  }


  /** Create a fixed-size thread pool with CallerRunsPolicy */

  public ThreadSafeExecutor(int worker_threads, ThreadFactory tf) {
    // When using a bounded thread pool, SynchronousQueue and
    // CallerRunsPolicy must be used in order to avoid deadlock.  This
    // is why we're not simply using ScheduledThreadPoolExecutor. See
    // http://esxx.blogspot.com/2009/06/threadpoolexecutor-deadlocks.html

    super(1, worker_threads + 1,
	  60L, TimeUnit.SECONDS,
	  new SynchronousQueue<Runnable>(),
	  tf, new ThreadPoolExecutor.CallerRunsPolicy());
    startDelayedWorker();
  }

  // From interface ExecutorService

  @Override public List<Runnable> shutdownNow() {
    List<Runnable> result = super.shutdownNow();
    result.addAll(Arrays.asList(delayQueue.toArray(new Runnable[0])));
    delayQueue.clear();
    return result;
  }

  // From interface ScheduledExecutorService

  @Override public ScheduledFuture<?> schedule(Runnable r, long d, TimeUnit u) {
    d = Math.max(0, d);

    return queueDelayed(new TSFuture<Void>(r, null, System.nanoTime() + u.toNanos(d), 0));
  }

  @Override public <V> ScheduledFuture<V> schedule(Callable<V> c, long d, TimeUnit u) {
    d = Math.max(0, d);

    return queueDelayed(new TSFuture<V>(c, System.nanoTime() + u.toNanos(d), 0));
  }

  @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long d, long p, TimeUnit u) {
    d = Math.max(0, d);

    if (p <= 0) {
      throw new IllegalArgumentException();
    }

    return queueDelayed(new TSFuture<Void>(r, null,
					   System.nanoTime() + u.toNanos(d),
					   u.toNanos(p)));
  }

  @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable r, long d, long p, TimeUnit u) {
    d = Math.max(0, d);

    if (p <= 0) {
      throw new IllegalArgumentException();
    }

    return queueDelayed(new TSFuture<Void>(r, null,
					   System.nanoTime() + u.toNanos(d),
					   u.toNanos(-p))); // Use negative period to mark delay
  }


  private <V> TSFuture<V> queueDelayed(TSFuture<V> f) {
    if (isShutdown() || !delayQueue.offer(f)) {
      getRejectedExecutionHandler().rejectedExecution(f, this);
    }

    return f;
  }

  private void startDelayedWorker() {
    execute(new Runnable() {
	@Override public void run() {
	  try {
	    while (!isShutdown()) {
	      TSFuture delayed = delayQueue.poll(1, TimeUnit.SECONDS);

	      if (delayed != null) {
		execute(delayed);
	      }
	    }
	  }
	  catch (InterruptedException ex) {
	    Thread.currentThread().interrupt();
	  }
	  catch (RejectedExecutionException ex) {
	    // Just exit
	  }
	}
      });
  }

  private DelayQueue<TSFuture> delayQueue = new DelayQueue<TSFuture>();
}
