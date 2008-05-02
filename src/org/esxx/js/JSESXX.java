/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007 Martin Blom <martin@blom.org>
     
     This program is free software; you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation; either version 2
     of the License, or (at your option) any later version.
     
     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.
     
     You should have received a copy of the GNU General Public License
     along with this program; if not, write to the Free Software
     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.esxx.js;

import org.esxx.ESXX;
import org.esxx.ESXXException;
import org.esxx.Request;
import org.esxx.Application;

import java.io.PrintWriter;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mozilla.javascript.*;

public class JSESXX
  extends ScriptableObject {
    public JSESXX() {
      super();
    }

    public JSESXX(Context cx, Scriptable scope,
		  Request request, Application app) {
      this();

      ESXX esxx     = ESXX.getInstance();

      this.debug    = new PrintWriter(request.getDebugWriter());
      this.error    = new PrintWriter(request.getErrorWriter());
      this.wd       = (JSURI) cx.newObject(scope, "URI", new Object[] { request.getWD() });
      this.location = (JSURI) cx.newObject(scope, "URI", new Object[] { request.getURL() });
      this.app      = app;
    }

    public JSURI setLocation(Context cx, Scriptable scope, URL url) {
      JSURI old_location = location;
      location = (JSURI) cx.newObject(scope, "URI", new Object[] { url });
      return old_location;
    }

    public JSURI setLocation(JSURI loc) {
      JSURI old_location = location;
      location = loc;
      return old_location;
    }

    public void setRequest(JSRequest req) {
      request = req;
    }

    public String getClassName() {
      return "ESXX";
    }

    static public Object jsConstructor(Context cx, 
				       java.lang.Object[] args, 
				       Function ctorObj, 
				       boolean inNewExpr) {
      return new JSESXX(cx, ctorObj, (Request) args[0], (Application) args[1]);
    }

    public Synchronizer jsFunction_sync(Function f) {
      return new Synchronizer(f);
    }

    public void jsFunction_notify(Object o) {
      synchronized (o) {
	o.notify();
      }
    }

    public void jsFunction_notifyAll(Object o) {
      synchronized (o) {
	o.notifyAll();
      }
    }

    public void jsFunction_wait(Object o)
      throws InterruptedException {
      synchronized (o) {
	o.wait();
      }
    }

    public void jsFunction_wait(Object o, int timeout_ms) 
      throws InterruptedException {
      synchronized (o) {
	o.wait(timeout_ms);
      }
    }

    public static void jsFunction_include(Context cx, Scriptable thisObj, 
					  Object[] args, Function funcObj) 
      throws java.net.MalformedURLException, IOException {
      ESXX        esxx = ESXX.getInstance();
      JSESXX   js_esxx = (JSESXX) thisObj;
      Scriptable scope = funcObj.getParentScope();
      Application  app = js_esxx.app;

      URI          uri = null;
      InputStream   is = null;

      if (args[0] instanceof JSURI) {
	uri = ((JSURI) args[0]).uri;
	is  = esxx.openCachedURL(uri.toURL());
      }
      else {
	String file = Context.toString(args[0]);

	try {
	  uri = js_esxx.location.uri.resolve(file);
	  is  = esxx.openCachedURL(uri.toURL());
	}
	catch (IOException ex) {
	  // Failed to resolve URL relative the current file's
	  // location -- try the include path

	  Object[] paths_to_try = cx.getElements(js_esxx.jsGet_paths());

	  for (Object path : paths_to_try) {
	    try {
	      uri = ((JSURI) path).uri.resolve(file);
	      is  = esxx.openCachedURL(uri.toURL());
	      // On success, break
	      break;
	    }
	    catch (IOException ex2) {
	      // Try next
	    }
	  }

	  if (is == null) {
	    throw Context.reportRuntimeError("File '" + file + "' not found.");
	  }
	}
      }

      synchronized (app) { // In case this method is called from a handler or main()
	app.importAndExecute(cx, scope, js_esxx, uri.toURL(), is);
      }
    }

    public static boolean jsFunction_checkTimeout(Context cx, Scriptable thisObj, 
						  Object[] args, Function funcObj) {
      if (Thread.currentThread().isInterrupted()) {
	checkTimeout(cx);
      }
      return true;
    }

    private static void checkTimeout(Context cx) {
      ESXX.Workload workload = (ESXX.Workload) cx.getThreadLocal(ESXX.Workload.class);

      if (workload.future.isCancelled()) {
	throw new ESXXException.TimeOut();
      }
    }

    public static Object[] jsFunction_parallel(Context cx, Scriptable thisObj, 
					       Object[] args, Function funcObj) {
      ESXX esxx = ESXX.getInstance();
      final Scriptable scope = funcObj.getParentScope();

      Object[] tasks = null;
      Object[] fargs = Context.emptyArgs;
      int    timeout = Integer.MAX_VALUE;
      int  max_tasks = Integer.MAX_VALUE;

      // The first argument in the workload array
      if (args.length < 1 || !(args[0] instanceof NativeArray)) {
	throw Context.reportRuntimeError("First argument must be an Array");
      }
      else {
	tasks = cx.getElements((NativeArray) args[0]);
      }

      // The second (optional) argument is the function arguments
      if (args.length > 1 && args[1] != Context.getUndefinedValue()) {
	cx.getElements((Scriptable) args[1]);
      }

      // The third (optional) argument is the timeout in ms
      if (args.length > 2 && args[2] != Context.getUndefinedValue()) {
	timeout = ((Number) args[2]).intValue();
      }

      // The fourth (optional) argument is the parallel limit
      if (args.length > 3 && args[3] != Context.getUndefinedValue()) {
	max_tasks = ((Number) args[3]).intValue();
      }

      if (max_tasks <= 0) {
	throw Context.reportRuntimeError("Workload limit must be greater than 0.");
      }

      ESXX.Workload[] workloads = new ESXX.Workload[tasks.length];
      
      // Submit workloads, limit if asked to
      final Semaphore limit = new Semaphore(max_tasks, false);
      final AtomicBoolean abort = new AtomicBoolean(false);

      for (int i = 0; i < tasks.length && !abort.get(); ++i) {
	if (tasks[i] instanceof Function) {
	  try {
	    limit.acquire();
	  }
	  catch (InterruptedException ex) {
	    checkTimeout(cx);
	    break;
	  }
	  
	  final Function func = (Function) tasks[i];
	  final Object[] jarg = fargs;
 
	  workloads[i] = esxx.addContextAction(cx, new ContextAction() {
	      public Object run(Context cx) {
		boolean fine = false;

		try {
		  Object res = func.call(cx, scope, scope, jarg);
		  fine = true;
		  return res;
		}
		finally {
		  if (!fine) {
		    abort.set(true);
		  }

		  limit.release();
		}
	      }
	    }, timeout);
	}
      }

      Object[] result = new Object[tasks.length];
      Object[] errors = new Object[tasks.length];
      boolean  failed = false;

      for (int i = 0; i < tasks.length; ++i) {
	if (workloads[i] != null) {
	  try {
	    result[i] = workloads[i].future.get();
	  }
	  catch (ExecutionException ex) {
	    result[i] = Context.getUndefinedValue();
	    errors[i] = new WrappedException(ex);
	    failed   = true;
	  }
	  catch (CancellationException ex) {
	    result[i] = Context.getUndefinedValue();
	    errors[i] = new WrappedException(new ESXXException.TimeOut());
	    failed   = true;
	  }
	  catch (InterruptedException ex) {
	    checkTimeout(cx);
	  }
	}
	else {
	  result[i] = tasks[i];
	}
      }

      if (failed) {
	throw new JavaScriptException(cx.newArray(scope, errors), null, 0);
      }

      return result;
    }

    public PrintWriter jsGet_error() {
      return error;
    }

    public PrintWriter jsGet_debug() {
      return debug;
    }

    public Scriptable jsGet_document() {
      return app.getMainDocument();
    }

    public JSURI jsGet_uri() {
      return app.getMainURI();
    }

    public Scriptable jsGet_paths() {
      return app.getIncludePath();
    }

    public void jsSet_paths(Scriptable paths) {
      app.setIncludePath(paths);
    }

    public JSURI jsGet_wd() {
      return wd;
    }

    public void jsSet_wd(JSURI wd) {
      this.wd = wd;
    }

    public JSURI jsGet_location() {
      return location;
    }

    public JSRequest jsGet_request() {
      return request;
    }

    private Application app;
    private PrintWriter error;
    private PrintWriter debug;
    private JSURI wd;
    private JSURI location;
    private JSRequest request;

}
