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

import java.util.*;

public class KeyValueList<K, V>
  extends ArrayList<Map.Entry<K, V>> {

  public KeyValueList() {
    super();
  }

  public KeyValueList(Collection<? extends Map.Entry<K, V>> c) {
    super(c);
  }

  public KeyValueList(int initialCapacity) {
    super(initialCapacity);
  }

  public boolean add(K key, V value) {
    return add(new AbstractMap.SimpleImmutableEntry<K, V>(key, value));
  }

  public void add(int index, K key, V value) {
    add(index, new AbstractMap.SimpleImmutableEntry<K, V>(key, value));
  }

  public K getKey(int index) {
    Map.Entry<K, V> entry = get(index);

    return entry == null ? null : entry.getKey();
  }

  public V getValue(int index) {
    Map.Entry<K, V> entry = get(index);

    return entry == null ? null : entry.getValue();
  }

  public Map.Entry<K, V> set(int index, K key, V value) {
    return set(index, new AbstractMap.SimpleImmutableEntry<K, V>(key, value));
  }

  static final long serialVersionUID = -1038792336604624107L;
}
