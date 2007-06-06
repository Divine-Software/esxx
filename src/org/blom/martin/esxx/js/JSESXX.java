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
