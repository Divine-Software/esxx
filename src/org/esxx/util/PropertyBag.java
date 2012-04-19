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
import org.mozilla.javascript.Wrapper;
import org.mozilla.javascript.xml.XMLObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public abstract class PropertyBag
  implements Wrapper {

  @SuppressWarnings({"unchecked"})
  public static PropertyBag create(Context cx, Scriptable scope, Object original) {
    Object obj = org.esxx.util.JS.toJavaObject(original, false);

    if (obj instanceof Scriptable) {
      // Also XMLList -- but not XML, which is now a Node object
      return new PropertyBag.JS(cx, scope, original, (Scriptable) obj);
    }
    else if (obj instanceof Node) {
      if (obj instanceof Element) {
	PropertyBag pb = new PropertyBag.XML(cx, scope, original, (Element) obj);

	if (pb.getKeys().size() > 0) {
	  return pb;
	}
      }

      // If not an element or no child elements, fall back to a scalar string bag
      Scriptable e4x = ESXX.domToE4X((Node) obj, cx, scope);
      return new PropertyBag.Scalar(cx, scope, original, ScriptRuntime.toString(e4x));
    }
    else if (obj instanceof java.util.Map) {
      return new PropertyBag.Map(cx, scope, original, (java.util.Map<Object, Object>) obj);
    }
    else if (obj instanceof Iterable) {
      return new PropertyBag.Array(cx, scope, original, (Iterable) obj);
    }
    else {
      return new PropertyBag.Scalar(cx, scope, original, obj);
    }
  }

  protected PropertyBag(Context cx, Scriptable scope, Object original) {
    this.cx       = cx;
    this.scope    = scope;
    wrappedObject = original;
  }

  @Override /* Wrapper */ public Object unwrap() {
    return wrappedObject;
  }

  public Object getValue(Object key) {
    Object result = getRawValue(key);

    if (result instanceof XMLObject &&
	"XMLList".equals(((XMLObject) result).getClassName()) &&
	!((XMLObject) result).has(0, ((XMLObject) result))) {
      // Empty XMLList -> undefined
      result = Context.getUndefinedValue();
    }

    return result;
  }

  public Object getValue(Object key, Object default_value) {
    Object value = getValue(key);

    if (value == Context.getUndefinedValue()) {
      value = default_value;
    }

    return value;
  }

  public Object getDefinedValue(Object key) {
    Object result = getValue(key);

    if (result == Context.getUndefinedValue()) {
      throw ScriptRuntime.undefReadError(ScriptRuntime.toObjectOrNull(cx, unwrap()), key);
    }

    return result;
  }

  public abstract Collection<Object> getKeys();
  public abstract Collection<Object> getValues();
  public abstract Object getRawValue(Object key);

  protected Context cx;
  protected Scriptable scope;
  private Object wrappedObject;

  protected Collection<Object> keys;
  protected Collection<Object> values;


  // JavaScript facade

  private static class JS
    extends PropertyBag {
    public JS(Context cx, Scriptable scope, Object original, Scriptable o) {
      super(cx, scope, original);

      object = o;
    }

    @Override public Collection<Object> getKeys() {
      if (keys == null) {
	keys = Arrays.asList(object.getIds());
      }

      return keys;
    }

    @Override public Collection<Object> getValues() {
      if (values == null) {
	values = new ArrayList<Object>(getKeys().size());

	for (Object key : getKeys()) {
	  values.add(ScriptRuntime.getObjectElem(object, key, cx));
	}
      }

      return values;
    }

    @Override public Object getRawValue(Object key) {
      return ScriptRuntime.getObjectElem(object, key, cx);
    }

    private Scriptable object;
  }

  // XML facade

  private static class XML
    extends PropertyBag {
    public XML(Context cx, Scriptable scope, Object original, Element e) {
      super(cx, scope, original);

      this.elem  = e;
    }

    @Override public Collection<Object> getKeys() {
      if (keys == null) {
	keys = new LinkedHashSet<Object>();

	// For XML bags, child element names are the keys
	for (Node child = elem.getFirstChild(); child != null; child = child.getNextSibling()) {
	  if (child.getNodeType() == Node.ELEMENT_NODE) {
	    keys.add(child.getNodeName());
	  }
	}
      }

      return keys;
    }

    @Override public Collection<Object> getValues() {
      if (values == null) {
	values = new ArrayList<Object>(getKeys().size());

	for (Object key : getKeys()) {
	  values.add(getValue(key));
	}
      }

      return values;
    }

    @Override public Object getRawValue(Object key) {
      if (key instanceof String && ((String) key).substring(0, 1).equals("$")) {
	// Secondary keys are attributes for XML bags
	key = "@" + ((String) key).substring(1);
      }

      Scriptable e4x = ESXX.domToE4X(elem, cx, scope);
      return ScriptRuntime.getObjectElem(e4x, key, cx);
    }

    private Element elem;
  }

  // Map facade

  private static class Map
    extends PropertyBag {
    public Map(Context cx, Scriptable scope, Object original, java.util.Map<Object, Object> m) {
      super(cx, scope, original);

      map = m;
    }

    @Override public Collection<Object> getKeys() {
      if (keys == null) {
	keys = map.keySet();
      }

      return keys;
    }

    @Override public Collection<Object> getValues() {
      if (values == null) {
	values = map.values();
      }

      return values;
    }

    @Override public Object getRawValue(Object key) {
      if (map.containsKey(key)) {
	return map.get(key);
      }
      else {
	return Context.getUndefinedValue();
      }
    }

    private java.util.Map<Object, Object> map;
  }

  // Array/Iterable facade

  private static class Array
    extends PropertyBag {
    public Array(Context cx, Scriptable scope, Object original, Iterable iter) {
      super(cx, scope, original);

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

    @Override public Collection<Object> getKeys() {
      if (keys == null) {
	keys  = new ArrayList<Object>(values.size());

	for (int i = 0; i < values.size(); ++i) {
	  keys.add(new Integer(i));
	}
      }

      return keys;
    }

    @Override public Collection<Object> getValues() {
      return values;
    }

    @Override public Object getRawValue(Object key) {
      try {
	if (!(key instanceof Number)) {
	  key = Integer.parseInt(Context.toString(key));
	}

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
    public Scalar(Context cx, Scriptable scope, Object original, Object obj) {
      super(cx, scope, original);

      keys   = Arrays.asList((Object)new Integer(0));
      values = Arrays.asList(obj);
    }

    @Override public Collection<Object> getKeys() {
      return keys;
    }

    @Override public Collection<Object> getValues() {
      return values;
    }

    @Override public Object getRawValue(Object key) {
      try {
	if (!(key instanceof Number)) {
	  key = Integer.parseInt(Context.toString(key));
	}

	return ((List) values).get(((Number) key).intValue());
      }
      catch (Exception ignored) {
	return Context.getUndefinedValue();
      }
    }
  }
}
