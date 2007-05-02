
package org.blom.martin.esxx.js;

import org.mozilla.javascript.*;

public class JSGlobal 
  extends ImporterTopLevel {

    public JSGlobal(Context cx) {
      super(cx, false);
    }

    public Object get(String name, Scriptable start) {
      if (name.equals("esxx")) {
	Context cx = Context.getCurrentContext();

	return (JSESXX) cx.getThreadLocal(JSESXX.class);
      }
      else {
	return super.get(name, start);
      }
    }
}
