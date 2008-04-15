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
import org.blom.martin.esxx.util.ESXXParser;

import java.io.PrintWriter;
import java.util.concurrent.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;

public class JSESXX
  extends ScriptableObject {
    public JSESXX() {
      super();
    }

    public JSESXX(ESXX esxx, Request request, ESXXParser parser,
		  Context cx, Scriptable scope) {
      this();

      try {
	this.esxx     = esxx;
	this.debug    = new PrintWriter(request.getDebugWriter());
	this.error    = new PrintWriter(request.getErrorWriter());
	this.document = esxx.domToE4X(parser.getXML(), cx, scope);
	this.uri      = JSURI.createJSURI(esxx, request.getURL().toURI());
	this.parser   = parser;
	this.request  = request;
      }
      catch (java.net.URISyntaxException ex) {
	throw new ESXXException("Failed to create JS ESXX object: " + ex.getMessage(), ex);
      }
    }

    static public Object jsConstructor(Context cx, 
				       java.lang.Object[] args, 
				       Function ctorObj, 
				       boolean inNewExpr) {
      return new JSESXX((ESXX) args[0], (Request) args[1], (ESXXParser) args[2],
			cx, ctorObj);
    }

    public String getClassName() {
      return "ESXX";
    }

     public Synchronizer jsFunction_sync(Function f) {
      return new Synchronizer(f);
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
      ESXX esxx = (ESXX) cx.getThreadLocal(ESXX.class);
      Scriptable scope = funcObj.getParentScope();

      int timeout;
      int tasks;
      
      // If the last parameter is a number, it defines the timeout
      if (args.length > 1 && args[args.length - 1] instanceof Number) {
	timeout = ((Number) args[args.length - 1]).intValue();
	tasks   = args.length - 1;
      }
      else {
	timeout = Integer.MAX_VALUE;
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

//      public Forker jsFunction_slow(Function f) {
//       return new Forker(f);
//     }

    
//      public static Forker jsFunction_slow(Context cx, Scriptable thisObj, 
//  					    Object[] args, Function funcObj);

    public PrintWriter jsGet_error() {
      return error;
    }

    public PrintWriter jsGet_debug() {
      return debug;
    }

    public Scriptable jsGet_document() {
      return document;
    }

    public JSURI jsGet_uri() {
      return uri;
    }

    private ESXX esxx;
    private PrintWriter error;
    private PrintWriter debug;
    private Scriptable document;
    private JSURI uri;
    private ESXXParser parser;
    private Request request;

//     private static class Forker
//       extends Delegator {
	
// 	public Forker(Scriptable obj) {
// 	  super(obj);
// 	}

// 	public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
// 	  JSESXX js_esxx = (JSESXX) cx.getThreadLocal(JSESXX.class);

// 	  if (!js_esxx.markedForTermination) {
// 	    // Make this thread die on completion and create a new one
// 	    js_esxx.markedForTermination = true;
// 	    js_esxx.esxx.createWorker();
// 	  }

// 	  return ((Function) obj).call(cx,scope,thisObj,args);
// 	}
//     }
}
