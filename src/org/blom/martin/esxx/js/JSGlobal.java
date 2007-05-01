
package org.blom.martin.esxx.js;

import org.mozilla.javascript.*;

public class JSGlobal 
  extends ScriptableObject {
    public JSGlobal() {
      super();
    }

    public String getClassName() {
      return "Global";
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
