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

import java.util.HashMap;
import org.esxx.cache.LRUCache;
import org.mozilla.javascript.*;

public class JSLRUCache
  extends ScriptableObject {

  private static final long serialVersionUID = -6799670003560008699L;
	
  public JSLRUCache() {
    super();
  }

  public JSLRUCache(int max_entries, long max_age_ms) {
    super();

    cache = new LRUCache<String, Object>(max_entries, max_age_ms);

    cache.addListener(new LRUCache.LRUListener<String, Object>() {
	public void entryAdded(String key, Object value) {
	  // Nothing to do
	}

	public void entryRemoved(String key, Object value) {
	  Function destructor;

	  synchronized (destructors) {
	    // The value stored, not the key, is associated with the
	    // destructor.
	    destructor = destructors.remove(value);
	  }

	  if (destructor != null) {
	    Context cx = Context.getCurrentContext();
	    Object[] a = new Object[] { key, value };
	    destructor.call(cx, JSLRUCache.this, JSLRUCache.this, a);
	  }
	}
      });
  }

  static public Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr) {
    int max_entries;
    long max_age_ms;

    if (args.length < 2) {
      throw Context.reportRuntimeError("Required argument missing.");
    }

    max_entries = (int) Context.toNumber(args[0]);
    max_age_ms  = (long) (1000 * Context.toNumber(args[1]));
    return new JSLRUCache(max_entries, max_age_ms);
  }


  @Override public String getClassName() {
    return "LRUCache";
  }

  public Object jsFunction_get(String key) {
    return cache.get(key);
  }

  public Object jsFunction_add(final String key, Double max_age, 
			       final Object value, Function destructor) 
    throws Exception {
    return cache.add(key, new VF(value, destructor), (long) (1000 * max_age));
  }

  public Object jsFunction_set(final String key, Double max_age, 
			       final Object value, Function destructor) 
    throws Exception {
    return cache.set(key, new VF(value, destructor), (long) (1000 * max_age));
  }

  public Object jsFunction_replace(final String key, Double max_age, 
				   final Object value, Function destructor) 
    throws Exception {
    return cache.replace(key, new VF(value, destructor), (long) (1000 * max_age));
  }

  public Object jsFunction_remove(final String key) 
    throws Exception {
    return cache.remove(key);
  }

  public void jsFunction_clear() {
    cache.clear();
  }
  
  public void jsFunction_filter(final Function f) {
    cache.filterEntries(new LRUCache.EntryFilter<String, Object>() {
	public boolean isStale(String key, Object value, long created) {
	  Context cx = Context.getCurrentContext();
	  Object[] a = new Object[] { key, value, new java.util.Date(created) };
	  // Negate return value to get Array.filter() semantics
	  return !Context.toBoolean(f.call(cx, JSLRUCache.this, JSLRUCache.this, a));
	}
      });
  }

  private class VF
    implements LRUCache.ValueFactory<String, Object> {

    public VF(Object value, Function destructor) {
      this.value = value;
      this.destructor = destructor;
    }
    
    public Object create(String key, long expires) {
      if (value instanceof Function) {
	Context cx = Context.getCurrentContext();
	Function f = (Function) value;
	Object[] a = new Object[] { key, new java.util.Date(expires) };
	value = f.call(cx, JSLRUCache.this, JSLRUCache.this, a);
      }

      if (destructor != null) {
	synchronized (destructors) {
	  destructors.put(value, destructor);
	}
      }

      return value;
    }

    private Object value;
    private Function destructor;
  }

  private LRUCache<String, Object> cache;
  private final HashMap<Object, Function> destructors = new HashMap<Object, Function>();
}
