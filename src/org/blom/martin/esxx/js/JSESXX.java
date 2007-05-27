
package org.blom.martin.esxx.js;

import org.blom.martin.esxx.ESXX;
import org.blom.martin.esxx.Workload;

import java.io.PrintWriter;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;

public class JSESXX
  extends ScriptableObject {
    public JSESXX() {
      super();
    }

    public JSESXX(ESXX esxx, Workload workload, Document document,
		  Context cx, Scriptable scope) {
      this();

      this.esxx     = esxx;
      this.debug    = new PrintWriter(workload.getDebugWriter());
      this.error    = new PrintWriter(workload.getErrorWriter());
      this.document = esxx.domToE4X(document, cx, scope);
    }

    static public Object jsConstructor(Context cx, 
				       java.lang.Object[] args, 
				       Function ctorObj, 
				       boolean inNewExpr) {
      return new JSESXX((ESXX) args[0], (Workload) args[1], (Document) args[2],
			cx, ctorObj);
    }

    public String getClassName() {
      return "ESXX";
    }

//     public Synchronizer jsFunction_sync(Context cx, Scriptable scope, Scriptable thisObj, 
// 			     java.lang.Object[] args) {
     public Synchronizer jsFunction_sync(Function f) {
      return new Synchronizer(f);
    }

//     public synchronized void waitForEvent(long millisToWait) 
//       throws InterruptedException {
//       this.wait(millisToWait);
//     }

//     public synchronized void postEvent() {
//       this.notifyAll();
//     }

    public PrintWriter jsGet_error() {
      return error;
    }

    public PrintWriter jsGet_debug() {
      return debug;
    }

    public Scriptable jsGet_document() {
      return document;
    }

    private ESXX esxx;

    private PrintWriter error;
    private PrintWriter debug;

    private Scriptable document;
}
