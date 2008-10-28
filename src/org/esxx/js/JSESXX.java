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

package org.esxx.js;

import org.esxx.ESXX;
import org.esxx.ESXXException;
import org.esxx.Application;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mozilla.javascript.*;

public class JSESXX
  extends ScriptableObject {
    private static final long serialVersionUID = -7401965682230734380L;

    public JSESXX() {
      super();
    }

    public JSESXX(Context cx, Scriptable scope, Application app) {
      this();

      this.app      = app;
      this.wd       = (JSURI) cx.newObject(scope, "URI", new Object[] { app.getWD() });
      this.location = null;
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


    @Override
    public String getClassName() {
      return "ESXX";
    }

    public static Object jsConstructor(Context cx,
				       java.lang.Object[] args,
				       Function ctorObj,
				       boolean inNewExpr) {
      return new JSESXX(cx, ctorObj, (Application) args[0]);
    }

    public static void finishInit(Scriptable scope, 
				  FunctionObject constructor,
				  Scriptable prototype) {
      // Define classes in constructor object
      try {
	ScriptableObject.defineClass(constructor, JSLogger.class);
	ScriptableObject.defineClass(constructor, JSLRUCache.class);
	ScriptableObject.defineClass(constructor, JSRequest.class);
	ScriptableObject.defineClass(constructor, JSResponse.class);
      }
      catch (Exception ex) {
	throw new ESXXException("Failed to define Logger, Request and Response classes");
      }
    }

    public static Scriptable newObject(Context cx, Scriptable scope, String name, Object[] args) {
	Scriptable global = getTopLevelScope(scope);
	Scriptable esxx   = (Scriptable) global.get("ESXX", global);
	Function   ctor   = (Function) esxx.get(name, esxx);
	
	return ctor.construct(cx, scope, args);
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

    public static boolean jsFunction_wait(Context cx, Scriptable thisObj,
					  Object[] args, Function funcObj)
      throws InterruptedException {
      Object   object = null;
      Function   func = null;
      long timeout_ms = 0;

      if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
	throw Context.reportRuntimeError("Required argument missing.");
      }

      object = args[0];

      if (args.length >= 2) {
	if (!(args[1] instanceof Number)) {
	  throw Context.reportRuntimeError("Third argument must be a number.");
	}

	timeout_ms = (long) Context.toNumber(args[1]);
      }

      if (args.length >= 3) {
	if (!(args[2] instanceof Function)) {
	  throw Context.reportRuntimeError("Third argument must be a function.");
	}

	func = (Function) args[2];
      }

      long       now = System.currentTimeMillis();
      long   expires = timeout_ms != 0 ? now + timeout_ms : Long.MAX_VALUE;
      Object[] fargs = new Object[] { object };
      boolean lastrc = false;

      synchronized (object) {
	if (func == null) {
	  // If not specified in JS, timeout_ms == 0 which is the same as o.wait().
	  object.wait(timeout_ms);
	}
	else {
	  Scriptable thiz = func.getParentScope();

	  while (now < expires && 
		 (lastrc = Context.toBoolean(func.call(cx, thiz, thiz, fargs))) == false) {
	    object.wait(expires - now);
	    now = System.currentTimeMillis();
	  }
	}
      }

      return lastrc;
    }

    public static void jsFunction_include(Context cx, Scriptable thisObj,
					  Object[] args, Function funcObj)
      throws java.net.MalformedURLException, IOException {
      ESXX        esxx = ESXX.getInstance();
      JSESXX   js_esxx = (JSESXX) thisObj;
      Scriptable scope = thisObj.getParentScope();
      Application  app = js_esxx.app;

      URI          uri = null;
      InputStream   is = null;

      if (args[0] instanceof JSURI) {
	uri = ((JSURI) args[0]).getURI();
	is  = esxx.openCachedURL(uri.toURL());
      }
      else {
	String file = Context.toString(args[0]);

	try {
	  if (js_esxx.location != null) {
	    uri = js_esxx.location.getURI().resolve(file);
	    is  = esxx.openCachedURL(uri.toURL());
	  }
	}
	catch (IOException ex) {
	  
	}

	if (is == null) {
	  // Failed to resolve URL relative the current file's
	  // location -- try the include path

	  Object[] paths_to_try = cx.getElements(js_esxx.jsGet_paths());

	  for (Object path : paths_to_try) {
	    try {
	      uri = ((JSURI) path).getURI().resolve(file);
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

      try {
	app.importAndExecute(cx, scope, js_esxx, uri.toURL(), is);
      }
      finally {
	is.close();
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

    public static Scriptable jsFunction_parallel(final Context cx, Scriptable thisObj,
						 Object[] args, Function funcObj) {
      Scriptable scope = funcObj.getParentScope();

      Object[] fargs = Context.emptyArgs;
      int    timeout = Integer.MAX_VALUE;
      int  max_tasks = Integer.MAX_VALUE;

      // The first argument is the workload array
      if (args.length < 1 || !(args[0] instanceof NativeArray)) {
	throw Context.reportRuntimeError("First argument must be an Array");
      }

      final Object tasks[] = cx.getElements((NativeArray) args[0]);

      // The second (optional) argument is the function arguments
      if (args.length > 1 && args[1] != Context.getUndefinedValue()) {
	fargs = cx.getElements((Scriptable) args[1]);
      }

      // The third (optional) argument is the timeout in ms
      if (args.length > 2 && args[2] != Context.getUndefinedValue()) {
	timeout = (int) Context.toNumber(args[2]);
      }

      // The fourth (optional) argument is the parallel limit
      if (args.length > 3 && args[3] != Context.getUndefinedValue()) {
	max_tasks = (int) Context.toNumber(args[3]);
      }

      if (max_tasks <= 0) {
	throw Context.reportRuntimeError("Workload limit must be greater than 0.");
      }

      ESXX.Workload[] workloads   = new ESXX.Workload[tasks.length];
      final Object[]  final_fargs = fargs;

      fork(cx, workloads, new ForkedFunction() {
	  public Object call(Context cx, int idx) {
	    if (tasks[idx] instanceof Function) {
	      Function   func = (Function) tasks[idx];
	      Scriptable thiz = func.getParentScope();
	      return func.call(cx, thiz, thiz, final_fargs);
	    }
	    else {
	      return Context.getUndefinedValue();
	    }
	  }
      }, timeout, max_tasks);

      return join(cx, scope, workloads);
    }


    public static Scriptable jsFunction_map(final Context cx, Scriptable thisObj,
					    final Object[] args, Function funcObj) {
      // The first argument is the data array
      if (args.length < 1 || !(args[0] instanceof NativeArray)) {
	throw Context.reportRuntimeError("First argument must be an Array");
      }

      // The second argument is the function
      if (args.length < 2 || !(args[1] instanceof Function)) {
	throw Context.reportRuntimeError("Second argument must be a Function");
      }

      final Object   data[] = cx.getElements((NativeArray) args[0]);
      final Function   func = (Function) args[1];
      final Scriptable thiz = func.getParentScope();
      int           timeout = Integer.MAX_VALUE;
      int         max_tasks = Integer.MAX_VALUE;

      // The third (optional) argument is the timeout in ms
      if (args.length > 2 && args[2] != Context.getUndefinedValue()) {
	timeout = (int) Context.toNumber(args[2]);
      }

      // The fourth (optional) argument is the parallel limit
      if (args.length > 3 && args[3] != Context.getUndefinedValue()) {
	max_tasks = (int) Context.toNumber(args[3]);
      }

      if (max_tasks <= 0) {
	throw Context.reportRuntimeError("Workload limit must be greater than 0.");
      }

      ESXX.Workload[] workloads = new ESXX.Workload[data.length];
      final Object undefined    = Context.getUndefinedValue();

      fork(cx, workloads, new ForkedFunction() {
	  public Object call(Context cx, int idx) {
	    if (data[idx] != undefined) {
	      Object fargs[] = { data[idx], idx, args[0] };
	      return func.call(cx, thiz, thiz, fargs);
	    }
	    else {
	      return undefined;
	    }
	  }
      }, timeout, max_tasks);

      return join(cx, thisObj, workloads);
    }

    public synchronized JSLogger jsGet_log() {
      if (logger == null) {
	Context cx = Context.getCurrentContext();
	logger = (JSLogger) newObject(cx, this, "Logger", new Object[] { app, app.getAppName() });
      }

      return logger;
    }

    public JSLRUCache jsGet_pls() {
      return app.getPLS(Context.getCurrentContext());
    }

    public JSLRUCache jsGet_tls() {
      return app.getTLS(Context.getCurrentContext());
    }

    public Scriptable jsGet_document() {
      return app.getMainDocument();
    }

    public void jsSet_document(Scriptable doc) {
      app.setMainDocument(doc);
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



    private interface ForkedFunction {
      public Object call(Context cx, int idx);
    }


    private static void fork(Context cx,
			     ESXX.Workload[] workloads,
			     final ForkedFunction ff,
			     int timeout, int max_tasks) {
      ESXX esxx = ESXX.getInstance();

      // Submit workloads, limit if asked to
      final Semaphore limit = new Semaphore(max_tasks, false);
      final AtomicBoolean abort = new AtomicBoolean(false);

      for (int i = 0; i < workloads.length && !abort.get(); ++i) {
	try {
	  limit.acquire();
	}
	catch (InterruptedException ex) {
	  checkTimeout(cx);
	  break;
	}

	final int idx = i;

	workloads[i] = esxx.addContextAction(cx, new ContextAction() {
	    public Object run(Context cx) {
	      boolean fine = false;

	      try {
		Object res = ff.call(cx, idx);
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


    private static Scriptable join(Context cx, Scriptable scope, 
				   ESXX.Workload[] workloads) {
      Object undefined = Context.getUndefinedValue();
      Object[] result  = new Object[workloads.length];
      Object[] errors  = new Object[workloads.length];
      boolean  failed  = false;

      for (int i = 0; i < workloads.length; ++i) {
	if (workloads[i] != null) {
	  try {
	    result[i] = workloads[i].future.get();
	  }
	  catch (ExecutionException ex) {
	    result[i] = undefined;
	    errors[i] = ex.getCause();
	    failed   = true;

	    if (!(errors[i] instanceof RhinoException)) {
	      ex.printStackTrace();
	    }
	  }
	  catch (CancellationException ex) {
	    result[i] = undefined;
	    errors[i] = new WrappedException(new ESXXException.TimeOut());
	    failed   = true;
	  }
	  catch (InterruptedException ex) {
	    checkTimeout(cx);
	  }
	}
	else {
	  result[i] = undefined;
	}
      }

      if (failed) {
	throw new JavaScriptException(cx.newArray(scope, errors), null, 0);
      }

      return cx.newArray(scope, result);
    }

    private Application app;
    private JSLogger logger;
    private JSLRUCache cache;
    private JSURI wd;
    private JSURI location;
}
