
package org.blom.martin.esxx.js;

import org.mozilla.javascript.*;

public class JSGlobal 
  extends ImporterTopLevel {

    public JSGlobal(Context cx) 
      throws IllegalAccessException, InstantiationException, 
      java.lang.reflect.InvocationTargetException {
      super(cx, false);

      ScriptableObject.defineClass(this, JSESXX.class);
      ScriptableObject.defineClass(this, JSRequest.class);
      ScriptableObject.defineClass(this, JSResponse.class);
      ScriptableObject.defineClass(this, JSURI.class);
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
