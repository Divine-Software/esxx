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

package org.esxx.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;;
import java.util.List;
import java.util.Map;
import org.esxx.ESXX;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.xml.XMLObject;
import org.w3c.dom.Node;

public abstract class PropertyBag {

  @SuppressWarnings({"unchecked"})
  public static PropertyBag get(Context cx, Object obj) {
    // Unwrap wrapped Java objects
    obj = org.esxx.util.JS.unwrap(obj);

    if (obj instanceof Scriptable) {
      return new PropertyBag.JS(cx, (Scriptable) obj);
    }
    else if (obj instanceof java.util.Map) {
      return new PropertyBag.Map((java.util.Map<Object, Object>) obj);
    }
    else if (obj instanceof Iterable) {
      return new PropertyBag.Array((Iterable) obj);
    }
    else {
      return new PropertyBag.Scalar(obj);
    }
  }

  public abstract Collection<Object> getKeys(Context cx);
  public abstract Collection<Object> getValues(Context cx);
  public abstract Object getValue(Context cx, Object key);

  protected Collection<Object> keys;
  protected Collection<Object> values;

  // JavaScript facade

  private static class JS
    extends PropertyBag {
    public JS(Context cx, Scriptable o) {
      object = o;
    }

    @Override public Collection<Object> getKeys(Context cx) {
      if (keys == null) {
	if (object instanceof XMLObject && !"XMLList".equals(object.getClassName())) {
	  // For XML element nodes, use child element names as keys
	  Node node = ESXX.e4xToDOM(object);

	  if (node.getNodeType() == Node.ELEMENT_NODE) {
	    keys = new LinkedHashSet<Object>();

	    for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
	      if (child.getNodeType() == Node.ELEMENT_NODE) {
		keys.add(child.getNodeName());
	      }
	    }
	  }
	}
      }

      if (keys == null) {
	keys = Arrays.asList(object.getIds());
      }

      return keys;
    }

    @Override public Collection<Object> getValues(Context cx) {
      if (values == null) {
	values = new ArrayList<Object>(getKeys(cx).size());

	for (Object key : getKeys(cx)) {
	  values.add(ScriptRuntime.getObjectElem(object, key, cx));
	}
      }

      return values;
    }

    @Override public Object getValue(Context cx, Object key) {
      return ScriptRuntime.getObjectElem(object, key, cx);
    }

    private Scriptable object;
  }

  // Map facade

  private static class Map
    extends PropertyBag {
    public Map(java.util.Map<Object, Object> m) {
      map  = m;
    }

    @Override public Collection<Object> getKeys(Context cx) {
      if (keys == null) {
	keys = map.keySet();
      }

      return keys;
    }

    @Override public Collection<Object> getValues(Context cx) {
      if (values == null) {
	values = map.values();
      }

      return values;
    }

    @Override public Object getValue(Context cx, Object key) {
      if (map.containsKey(key)) {
	return map.get(key);
      }
      else {
	return Context.getUndefinedValue();
      }
    }

    private java.util.Map<Object, Object> map;
  }

  // Array facade

  private static class Array
    extends PropertyBag {
    public Array(Iterable iter) {
      if (iter instanceof Collection) {
	values = new ArrayList<Object>((Collection<?>) iter);
      }
      else {
	values = new ArrayList<Object>();

	for (Object o : iter) {
	  values.add(o);
	}
      }
    }

    @Override public Collection<Object> getKeys(Context cx) {
      if (keys == null) {
	keys  = new ArrayList<Object>(values.size());

	for (int i = 0; i < values.size(); ++i) {
	  keys.add(new Integer(i));
	}
      }

      return keys;
    }

    @Override public Collection<Object> getValues(Context cx) {
      return values;
    }

    @Override public Object getValue(Context cx, Object key) {
      try {
	return ((List) values).get(((Number) key).intValue());
      }
      catch (Exception ignored) {
	return Context.getUndefinedValue();
      }
    }
  }

  // Scalar facade

  private static class Scalar
    extends PropertyBag {
    public Scalar(Object obj) {
      keys   = Arrays.asList((Object)new Integer(0));
      values = Arrays.asList(obj);
    }

    @Override public Collection<Object> getKeys(Context cx) {
      return keys;
    }

    @Override public Collection<Object> getValues(Context cx) {
      return values;
    }

    @Override public Object getValue(Context cx, Object key) {
      try {
	return ((List) values).get(((Number) key).intValue());
      }
      catch (Exception ignored) {
	return Context.getUndefinedValue();
      }
    }
  }
}
