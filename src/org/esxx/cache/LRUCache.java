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

package org.esxx.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class LRUCache<K, V> {

  public interface ValueFactory<K, V> {
    public V create(K key, long age) 
      throws Exception;
  }

  public interface EntryFilter<K, V> {
    public boolean isStale(K key, V value, long created);
  }

  public interface LRUListener<K, V> {
    public void entryAdded(K key, V value);
    public void entryRemoved(K key, V value);
  }

  public LRUCache(int max_entries, long max_age) {
    map = new LRUMap();

    maxEntries = max_entries;
    maxAge     = max_age;
  }


  public V get(K key) {
    LRUEntry entry;

    synchronized (map) {
      // (map.get() will put the entry last in the linked list!)
      entry = map.get(key);
    }

    if (entry != null) {
      synchronized (entry) {
	// Update expire time and return value
	long now = System.currentTimeMillis();
	entry.expires = entry.maxAge == 0 ? Long.MAX_VALUE : now + entry.maxAge;
	return entry.value;
      }
    }
    else {
      return null;
    }
  }


  /** Adds a value if and only if there was no previous value in the
   *  cache.
   *
   *  @param key    The key
   *  @param value  The value
   *  @param age    The maximum number of milliseconds to keep the value
   *                in the cache. If 0, the cache's global maximum age
   *                is used.
   *
   *  @result The value in the cache after this call (existing or new).
   */

  public V add(K key, final V value, long age) {
    try {
      return add(key, new ValueFactory<K, V>() {
	  public V create(K key, long age) {
	    return value;
	  }
	}, age);
    }
    catch (Exception ex) {
      throw new IllegalStateException("add() should not have thrown!", ex);
    }
  }


  /** Creates and adds a value if and only if there was no previous
   *  value in the cache.
   *
   *  @param key      The key
   *  @param factory  A ValueFactory
   *  @param age      The maximum number of milliseconds to keep the value
   *                  in the cache. If 0, the cache's global maximum age
   *                  is used.
   *
   *  @result The value in the cache after this call (existing or new).
   */

  public V add(K key, ValueFactory<K, V> factory, long age) 
    throws Exception {
    if (age == 0) {
      age = maxAge;
    }

    while (true) { // Repeat until successful
      LRUEntry entry = getEntry(key);

      synchronized (entry) {
	if (!entry.isDeleted()) {
	  if (entry.value ==  null) {
	    long now = System.currentTimeMillis();

	    entry.maxAge  = age;
	    entry.expires = entry.maxAge == 0 ? Long.MAX_VALUE : now + entry.maxAge;
	    entry.created = now;
	    entry.value   = factory.create(key, entry.expires);
	    fireAddedEvent(key, entry.value);
	  }
	
	  return entry.value;
	}
      }

      Thread.yield();
    }
  }



  /** Unconditionally inserts a value into the cache.
   *
   *  @param key    The key
   *  @param value  The value
   *  @param age    The maximum number of milliseconds to keep the value
   *                in the cache. If 0, the cache's global maximum age
   *                is used.
   *
   *  @result The value that was replaced, or null if there were no
   *  previous value in the cache.
   */

  public V set(K key, final V value, long age) {
    try {
      return set(key, new ValueFactory<K, V>() {
	  public V create(K key, long age) {
	    return value;
	  }
	}, age);
    }
    catch (Exception ex) {
      throw new IllegalStateException("set() should not have thrown!", ex);
    }
  }


  /** Unconditionally inserts a value into the cache.
   *
   *  @param key    The key
   *  @param value  A ValueFactory
   *  @param age    The maximum number of milliseconds to keep the value
   *                in the cache. If 0, the cache's global maximum age
   *                is used.
   *
   *  @result The value that was replaced, or null if there were no
   *  previous value in the cache.
   */

  public V set(K key, ValueFactory<K, V> factory, long age) 
    throws Exception {
    if (age == 0) {
      age = maxAge;
    }

    while (true) { // Repeat until successful
      LRUEntry entry = getEntry(key);

      synchronized (entry) {
	if (!entry.isDeleted()) {
	  V old_value = entry.value;

	  if (old_value != null) {
	    fireRemovedEvent(key, old_value);
	  }

	  long now = System.currentTimeMillis();

	  entry.maxAge  = age;
	  entry.expires = entry.maxAge == 0 ? Long.MAX_VALUE : now + entry.maxAge;
	  entry.created = now;
	  entry.value   = factory.create(key, entry.expires);
	  fireAddedEvent(key, entry.value);

	  return old_value;
	}
      }

      Thread.yield();
    }
  }


  /** Replaces a value if and only if it already exists.
   *
   *  @param key    The key
   *  @param value  The value
   *  @param age    The maximum number of milliseconds to keep the value
   *                in the cache. If 0, the cache's global maximum age
   *                is used.
   *
   *  @result The value that was replaced, or null if there were no
   *  previous value in the cache.
   */

  public V replace(K key, final V value, long age) {
    try {
      return replace(key, new ValueFactory<K, V>() {
	  public V create(K key, long age) {
	    return value;
	  }
	}, age);
    }
    catch (Exception ex) {
      throw new IllegalStateException("add() should not have thrown!", ex);
    }
  }


  /** Creates and replaces a value if and only if it already exists.
   *
   *  @param key      The key
   *  @param factory  A ValueFactory
   *  @param age      The maximum number of milliseconds to keep the value
   *                  in the cache. If 0, the cache's global maximum age
   *                  is used.
   *
   *  @result The value that was replaced, or null if there were no
   *  previous value in the cache.
   */

  public V replace(K key, ValueFactory<K, V> factory, long age)
    throws Exception {
    if (age == 0) {
      age = maxAge;
    }
    
    while (true) { // Repeat until successful
      LRUEntry entry = getEntry(key);

      synchronized (entry) {
	if (!entry.isDeleted()) {
	  V old_value = entry.value;

	  if (old_value != null) {
	    fireRemovedEvent(key, old_value);

	    long now = System.currentTimeMillis();

	    entry.maxAge  = age;
	    entry.expires = entry.maxAge == 0 ? Long.MAX_VALUE : now + entry.maxAge;
	    entry.created = now;
	    entry.value   = factory.create(key, entry.expires);
	    fireAddedEvent(key, entry.value);
	  }

	  return old_value;
	}
      }
      
      Thread.yield();
    }
  }


  /** Removes and returns a value from the cache.
   *
   *  @param key  The key
   *
   *  @result The old value, or null.
   */

  public V remove(K key) {
    LRUEntry entry = getEntry(key);
    V old_value;

    synchronized (entry) {
      old_value = entry.value;

      if (old_value != null) {
	fireRemovedEvent(key, old_value);

	// Mark entry as deleted
	entry.markAsDeleted();
      }
    }

    synchronized (map) {
      synchronized (entry) {
	// NOTE: Lock order: first map, then entry
	if (entry.isDeleted()) {
	  // Still marked for deletion
	  map.remove(key);
	}
      }
    }

    return old_value;
  }


  /** Removes all entries from the cache.
   * 
   *  This operation locks the whole map!
   */

  public void clear() {
    synchronized (map) {
      for (Map.Entry<K, LRUEntry> e : map.entrySet()) {
	LRUEntry entry = e.getValue();

	synchronized (entry) {
	  // NOTE: Lock order: first map, then entry

	  if (entry.value != null) {
	    fireRemovedEvent(e.getKey(), entry.value);
	  }

	  entry.markAsDeleted();
	}
      }

      map.clear();
    }
  }


  /** Iterates all entries and removes all for which
   *  EntryFilter.isStale() returns true, or has already expired.
   *
   *  @param filter  An EntryFilter. May be null.
   */

  public void filterEntries(EntryFilter<K, V> filter) {
    LinkedList<Map.Entry<K, LRUEntry>> entries;

    synchronized (map) {
      entries = new LinkedList<Map.Entry<K, LRUEntry>>(map.entrySet());
    }

    long now = System.currentTimeMillis();

    for (Map.Entry<K, LRUEntry> e : entries) {
      LRUEntry entry = e.getValue();
      boolean remove = false;

      synchronized (entry) {
	if (!entry.isDeleted() && entry.value != null && 
	    (entry.expires < now || 
	     filter != null && filter.isStale(e.getKey(), entry.value, entry.created))) {
	  fireRemovedEvent(e.getKey(), entry.value);

	  entry.markAsDeleted();
	  remove = true;
	}
      }
      
      if (remove) {
	synchronized (map) {
	  synchronized (entry) {
	    // NOTE: Lock order: first map, then entry
	    if (entry.isDeleted()) {
	      // Still marked for deletion
	      map.remove(e.getKey());
	    }
	  }
	}
      }
    }
  }

  public void addListener(LRUListener<K, V> l) {
    synchronized (entryListeners) {
      entryListeners.add(l);
    }
  }

  public void removeListener(LRUListener<K, V> l) {
    synchronized (entryListeners) {
      entryListeners.remove(l);
    }
  }

  public void fireAddedEvent(K key, V value) {
    synchronized (entryListeners) {
      for (LRUListener<K, V> l : entryListeners) {
	l.entryAdded(key, value);
      }
    }
  }

  public void fireRemovedEvent(K key, V value) {
    synchronized (entryListeners) {
      for (LRUListener<K, V> l : entryListeners) {
	l.entryRemoved(key, value);
      }
    }
  }


  /** Return an LRUEntry, creating a newly inserted one if not already present.
   *
   *  @param key  The key.
   *
   *  @return An LRUEntry
   */

  private LRUEntry getEntry(K key) {
    synchronized (map) {
      LRUEntry entry = map.get(key);

      if (entry == null) {
	entry = new LRUEntry();
	map.put(key, entry);
      }

      return entry;
    }
  }


  private class LRUMap
    extends LinkedHashMap<K, LRUEntry> {
    private static final long serialVersionUID = 8027701661709058455L;

    public LRUMap() {
      super (128, 0.75f, true);
    }

    private boolean isFull(LRUEntry eldest, long now) {
      return (size() > maxEntries || (eldest.expires != 0 && eldest.expires < now));
    }

    @Override public boolean removeEldestEntry(Map.Entry<K, LRUEntry> eldest) {
      long now = System.currentTimeMillis();

      LRUEntry entry = eldest.getValue();

      synchronized (entry) {
	// NOTE: Lock order: first map, then entry (map locked by caller)

	if (!isFull(entry, now)) {
	  entry = null;
	}
      }
      
      if (entry != null) {
	Iterator<Map.Entry<K, LRUEntry>> i = entrySet().iterator();

	while (i.hasNext()) {
	  Map.Entry<K, LRUEntry> e = i.next();

	  entry = e.getValue();

	  synchronized (entry) {
	    // NOTE: Lock order: first map, then entry (map locked by caller)

	    if (!isFull(entry, now)) {
	      break;
	    }

	    if (entry.value != null) {
	      fireRemovedEvent(e.getKey(), entry.value);
	    }

	    entry.markAsDeleted();
	    i.remove();
	  }
	}
      }

      // Tell implementation not to auto-modify the hash table
      return false;
    }
  }

  class LRUEntry {
    public void markAsDeleted() {
      maxAge  = Long.MIN_VALUE;
      expires = Long.MIN_VALUE;
      created = Long.MIN_VALUE;
      value   = null;
    }

    public boolean isDeleted() {
      return expires == Long.MIN_VALUE;
    }

    long expires;
    long created;
    long maxAge;
    V value;
  }

  private LRUMap map;

  private int maxEntries;
  private long maxAge;

  private LinkedList<LRUListener<K, V>> entryListeners = new LinkedList<LRUListener<K, V>>();

  static final long serialVersionUID = 8565024717836226408L;
}
