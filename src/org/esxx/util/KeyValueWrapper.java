/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2015 Martin Blom <martin@blom.org>

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

package org.esxx.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptRuntime;

public class KeyValueWrapper {

  @SuppressWarnings({"unchecked"})
  public KeyValueWrapper(Object obj) {
    if (obj instanceof Scriptable) {
      js   = (Scriptable) obj;
      size = -1;
    }
    else if (obj instanceof Map) {
      map  = (Map<Object, Object>) obj;
      size = -1;
    }
    else if (obj instanceof Iterable) {
      Collection<Object> coll;

      if (obj instanceof Collection) {
	coll = (Collection<Object>) obj;
      }
      else {
	coll = new ArrayList<Object>();

	for (Object o : (Iterable) obj) {
	  coll.add(o);
	}
      }

      array = coll.toArray();
      size  = array.length;
    }
    else {
      throw new IllegalArgumentException("Unable to wrap class " + obj.getClass().getSimpleName());
    }
  }

  public int size() {
    return size != -1 ? size : getKeys().size();
  }

  public Collection<Object> getKeys() {
    if (keys == null) {
      if (js != null) {
	keys = Arrays.asList(js.getIds());
      }
      else if (map != null) {
	keys = map.keySet();
      }
      else if (array != null) {
	keys  = new ArrayList<Object>(size);

	for (int i = 0; i < size; ++i) {
	  keys.add(new Integer(i));
	}
      }
      else {
	throw new IllegalStateException("No object!");
      }
    }

    return keys;
  }

  public Collection<Object> getValues(Context cx) {
    if (values == null) {
      if (js != null) {
	values = new ArrayList<Object>(getKeys().size());

	for (Object key : getKeys()) {
	  values.add(ScriptRuntime.getObjectElem(js, key, cx));
	}
      }
      else if (map != null) {
	values = map.values();
      }
      else if (array != null) {
	values = Arrays.asList(array);
      }
      else {
	throw new IllegalStateException("No object!");
      }
    }

    return values;
  }

  public Object getValue(Context cx, Object key) {
    if (js != null) {
      return ScriptRuntime.getObjectElem(js, key, cx);
    }
    else if (map != null) {
      if (map.containsKey(key)) {
	return map.get(key);
      }
      else {
	return Context.getUndefinedValue();
      }
    }
    else if (array != null) {
      try {
	return array[((Number) key).intValue()];
      }
      catch (Exception ignored) {
	return Context.getUndefinedValue();
      }
    }
    else {
      throw new IllegalStateException("No object!");
    }
  }

  private int size;
  private Collection<Object> keys;
  private Collection<Object> values;

  private Scriptable js;
  private Map<Object, Object> map;
  private Object[] array;
}
