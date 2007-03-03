
package org.blom.martin.esxx;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import org.blom.martin.esxx.js.JSESXX;
import org.mozilla.javascript.*;


public class ESXX {
    public static final String NAMESPACE = "http://martin.blom.org/esxx/1.0/";


    public ESXX(Properties p) {
      settings = p;

      workerThreads = new ThreadGroup("ESXX worker threads");
      workloadQueue = new LinkedBlockingQueue<Workload>(MAX_WORKLOADS);

      // Create worker threads

      int threads = Integer.parseInt(
	settings.getProperty("esxx.worker_threads", 
			     "" + Runtime.getRuntime().availableProcessors() * 2));

      for (int i = 0; i < threads; ++i) {
	Thread t = new Thread(
	  workerThreads, 
	  new Runnable() {
	      public void run() {
		// Create the JavaScript thread context and invoke workerThread()
		Context.call(new ContextAction() {
		      public Object run(Context cx) {
			workerThread(cx);
			return null;
		      }
		  });
	      }
	  },
	  "ESXX worker thread " + i);

	t.start();
      }
    }

    public void addWorkload(Workload w) {
      while (true) {
	try {
	  workloadQueue.put(w);
	  break;
	}
	catch (InterruptedException ex) {
	  // Retry
	}
      }
    }

    private void workerThread(Context cx) {
      // Provide a better mapping for primitive types on this context
      cx.getWrapFactory().setJavaPrimitiveWrap(false);

//       cx.setWrapFactory(new WrapFactory() {
// 	    public Object wrap(Context cx, Scriptable scope, Object obj, Class staticType) {
// 	      if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
// 		return obj;
// 	      } 
// 	      else if (obj instanceof Character) {
// 		char[] a = { ((Character) obj).charValue() };
// 		return new String(a);
// 	      }
// 	      else {
// 		return super.wrap(cx, scope, obj, staticType);
// 	      }
// 	    }	    
// 	});


      // Now wait for workloads and execute them

      while (true) {
	try {
	  Workload workload = workloadQueue.take();

	  try {
	    ESXXParser parser  = new ESXXParser(workload.getInputStream(), workload.getURL());
	    Scriptable scope   = new ImporterTopLevel(cx, true);
	    JSESXX     js_esxx = new JSESXX(cx, scope, workload, 
					    parser.getXML(), parser.getStylesheet().toString());
	    Object     esxx    = Context.javaToJS(js_esxx, scope);
	    ScriptableObject.putProperty(scope, "esxx", esxx);

	    for (ESXXParser.Code c : parser.getCodeList()) {
	      cx.evaluateString(scope, c.code, c.url.toString(), c.line, null);
	    }

// 	    Object result = cx.evaluateReader(scope, 
// 					      new InputStreamReader(workload.getCodeStream()),
// 					      workload.getURL().toString(), 1, null);
// 	    System.err.println(cx.toString(result));	
	  }
	  catch (Exception ex) {
	    ex.printStackTrace();
	  }

	  Properties p = new Properties();
	  p.setProperty("Status", "200 OK");
	  p.setProperty("Content-Type",  "text/plain");
	  workload.finished(0, p);
	}
	catch (InterruptedException ex) {
	  // Don't know what to do here ... die?
	  ex.printStackTrace();
	  return;
	}
      }
    }
    


    private static final int MAX_WORKLOADS = 16;

    private Properties settings;
    private ThreadGroup workerThreads;
    private LinkedBlockingQueue<Workload> workloadQueue;
};
