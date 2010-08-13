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
import org.esxx.Schema;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
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

      this.app = app;
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
	ScriptableObject.defineClass(constructor, JSSchema.class);
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
					  Object[] args, Function funcObj) {
      if (args.length < 1 || args[0] == Context.getUndefinedValue()) {
	throw Context.reportRuntimeError("Required argument missing.");
      }

      Object object = args[0];
      Function   func = null;
      long timeout_ms = 0;

      if (args.length >= 2) {
	if (!(args[1] instanceof Number)) {
	  throw Context.reportRuntimeError("Third argument must be a number.");
	}

	timeout_ms = (long) (1000 * Context.toNumber(args[1]));
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
	  try {
	    object.wait(timeout_ms);
	  }
	  catch (InterruptedException ex) {
	    checkTimeout(cx);
	  }
	}
	else {
	  Scriptable thiz = func.getParentScope();

	  while (now < expires && 
		 (lastrc = Context.toBoolean(func.call(cx, thiz, thiz, fargs))) == false) {
	    try {
	      object.wait(expires - now);
	    }
	    catch (InterruptedException ex) {
	      checkTimeout(cx);
	    }

	    now = System.currentTimeMillis();
	  }
	}
      }

      return lastrc;
    }

    public static void jsFunction_include(Context cx, Scriptable thisObj,
					  Object[] args, Function funcObj)
      throws IOException {
      ESXX        esxx = ESXX.getInstance();
      JSESXX   js_esxx = (JSESXX) thisObj;
      Scriptable scope = ScriptableObject.getTopLevelScope(thisObj);
      Application  app = js_esxx.app;
      URI          uri;

      if (args.length > 1 && args[1] != Context.getUndefinedValue()) {
	scope = (Scriptable) args[1];
      }

      try {
	uri = new URI(Context.toString(args[0]));
      }
      catch (URISyntaxException ex) {
	throw Context.reportRuntimeError("Invalid argument: " + args[0]);
      }

      Application.ESXXScript es = app.resolveScript(cx, uri, null);
      es.exec(cx, scope);
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

      if (workload.isCancelled()) {
	throw new ESXXException.TimeOut();
      }
    }

    public static Scriptable jsFunction_parallel(final Context cx, Scriptable thisObj,
						 Object[] args, Function funcObj) {
      JSESXX   js_esxx = (JSESXX) thisObj;
      Scriptable scope = funcObj.getParentScope();

      Object[] fargs = Context.emptyArgs;
      int timeout_ms = Integer.MAX_VALUE;
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

      // The third (optional) argument is the timeout in s
      if (args.length > 2 && args[2] != Context.getUndefinedValue()) {
	timeout_ms = (int) (1000 * Context.toNumber(args[2]));
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

      js_esxx.fork(cx, workloads, new ForkedFunction() {
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
      }, timeout_ms, max_tasks);

      return join(cx, scope, workloads);
    }


    public static Scriptable jsFunction_map(final Context cx, Scriptable thisObj,
					    final Object[] args, Function funcObj) {
      JSESXX js_esxx = (JSESXX) thisObj;

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
      int        timeout_ms = Integer.MAX_VALUE;
      int         max_tasks = Integer.MAX_VALUE;

      // The third (optional) argument is the timeout in s
      if (args.length > 2 && args[2] != Context.getUndefinedValue()) {
	timeout_ms = (int) (1000 * Context.toNumber(args[2]));
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

      js_esxx.fork(cx, workloads, new ForkedFunction() {
	  public Object call(Context cx, int idx) {
	    if (data[idx] != undefined) {
	      Object fargs[] = { data[idx], idx, args[0] };
	      return func.call(cx, thiz, thiz, fargs);
	    }
	    else {
	      return undefined;
	    }
	  }
      }, timeout_ms, max_tasks);

      return join(cx, thisObj, workloads);
    }

    public JSLogger jsGet_log() {
      return app.getJSAppLogger(Context.getCurrentContext());
    }

    public Object jsGet_host() {
      return ESXX.getInstance().getHostObject();
    }

    public Scriptable jsGet_global() {
      return getTopLevelScope(this);
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
      return app.getJSWorkingDirectory(Context.getCurrentContext());
    }

    public void jsSet_wd(JSURI wd) {
      app.setWorkingDirectory(wd.jsGet_javaURI());
    }

    public JSURI jsGet_location() {
      return app.getJSCurrentLocation(Context.getCurrentContext());
    }

    private interface ForkedFunction {
      public Object call(Context cx, int idx);
    }


    private void fork(Context cx,
		      ESXX.Workload[] workloads,
		      final ForkedFunction ff,
		      int timeout_ms, int max_tasks) {
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
	  }, app + "/" + Thread.currentThread() + " fork " + i, timeout_ms);
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
	    result[i] = workloads[i].getResult();
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
}
