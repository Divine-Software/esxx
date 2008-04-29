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

package org.blom.martin.esxx.js;

import org.blom.martin.esxx.ESXX;
import org.blom.martin.esxx.ESXXException;
import org.blom.martin.esxx.Request;
import org.blom.martin.esxx.Application;

import java.io.PrintWriter;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.util.concurrent.*;
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
      Scriptable scope = funcObj.getParentScope();

      int timeout;
      int tasks;
      
      // If the last parameter is a number, it defines the timeout
      if (args.length > 1 && args[args.length - 1] instanceof Number) {
	timeout = ((Number) args[args.length - 1]).intValue();
	tasks   = args.length - 1;
      }
      else {
	timeout = -1;
	tasks   = args.length;
      }
      Object[] func_args;

      // If the second-last parameter (or last if no timeout was
      // specified) is not a Function, it's an array that will be used
      // as arguments to the functions.
      if (tasks > 1 && args[tasks - 1] instanceof Scriptable && 
	  !(args[tasks - 1] instanceof Function)) {
	func_args = cx.getElements((Scriptable) args[tasks - 1]);
	--tasks;
      }
      else {
	func_args = new Object[0];
      }

      if (tasks == 1 && args[0] instanceof NativeArray) {
	NativeArray array = (NativeArray) args[0];
	tasks = (int) array.getLength();
	args  = cx.getElements(array);
      }

      ESXX.Workload[] workloads = new ESXX.Workload[tasks];
      
      // Submit workloads
      for (int i = 0; i < tasks; ++i) {
	if (args[i] instanceof Function) {
	  workloads[i] = esxx.addJSFunction(cx, scope, (Function) args[i], func_args, timeout);
	}
      }

      Object[] result = new Object[tasks];

      for (int i = 0; i < tasks; ++i) {
	if (workloads[i] != null) {
	  try {
	    result[i] = workloads[i].future.get();
	  }
	  catch (ExecutionException ex) {
	    result[i] = new WrappedException(ex);
	  }
	  catch (CancellationException ex) {
	    result[i] = new WrappedException(new ESXXException.TimeOut());
	  }
	  catch (InterruptedException ex) {
	    //	    Thread.currentThread().interrupt();
	     	    checkTimeout(cx);
	  }
	}
	else {
	  result[i] = args[i];
	}
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
