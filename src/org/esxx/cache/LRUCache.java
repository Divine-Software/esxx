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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;

public class LRUCache<E> {
  public LRUCache(int max_entries, long max_size, long max_age) {
    map = new LRUMap();

    maxEntries = max_entries;
    maxSize    = max_size;
    maxAge     = max_age;
  }


  public E get(String key) {
    LRUEntry entry;

    synchronized (map) {
      // (map.get() will put the entry last in the linked list!)
      entry = map.get(key);
    }

    if (entry != null) {
      synchronized (entry) {
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

  public E add(String key, E value, long age) {
    if (age == 0) {
      age = maxAge;
    }

    LRUEntry entry = getEntry(key);

    synchronized (entry) {
      if (entry.value ==  null) {
	entry.expires = age == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + age;
	entry.value   = value;
      }

      return entry.value;
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

  public E set(String key, E value, long age) {
    if (age == 0) {
      age = maxAge;
    }

    LRUEntry entry = getEntry(key);

    synchronized (entry) {
      E old_value = entry.value;

      if (old_value != null) {
	entryRemoved(key, old_value);
      }

      entry.expires = age == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + age;
      entry.value = value;

      return old_value;
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

  public E replace(String key, E value, long age) {
    if (age == 0) {
      age = maxAge;
    }
    
    LRUEntry entry = getEntry(key);

    synchronized (entry) {
      E old_value = entry.value;

      if (old_value != null) {
	entryRemoved(key, old_value);

	entry.expires = age == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + age;
	entry.value   = value;
      }

      return old_value;
    }
  }


  /** Removes and returns a value from the cache.
   *
   *  @param key  The key
   *
   *  @result The old value, or null.
   */

  public E remove(String key) {
    LRUEntry entry = getEntry(key);
    E old_value;

    synchronized (entry) {
      old_value = entry.value;

      if (old_value != null) {
	entryRemoved(key, old_value);

	// Mark entry as deleted
	entry.expires = Long.MIN_VALUE; // A looong time ago
	entry.value   = null;
      }
    }

    synchronized (map) {
      synchronized (entry) {
	// NOTE: Lock order: first map, then entry
	if (entry.expires == Long.MIN_VALUE) {
	  // Still marked for deletion
	  map.remove(key);
	}
      }
    }

    return old_value;
  }


  /** Removes all entries from the cache. */

  public void clear() {
    synchronized (map) {
      for (Map.Entry<String, LRUEntry> e : map.entrySet()) {
	entryRemoved(e.getKey(), e.getValue().value);
      }
      
      map.clear();
    }
  }

  public interface EntryFilter<E> {
    public boolean isStale(String key, E value);
  }


  /** Iterates all entries and removes all for which
   *  EntryFilter.isStale() returns true. 
   *
   *  @param filter  An EntryFilter.
   */

  public void filterEntries(EntryFilter filter) {
    
  }

  /** An overridable method that will be called whenever a value is
   *  removed from the cache.
   *
   *  @param key    The key
   *  @param value  The value
   */

  protected void entryRemoved(String key, E value) {
    // Override in subclass if you need to be informed when an entry
    // is flushed.
  }


  /** Return an LRUEntry, creating a newly inserted one if not already present.
   *
   *  @param key  The key.
   *
   *  @return An LRUEntry
   */

  private LRUEntry getEntry(String key) {
    synchronized (map) {
      LRUEntry entry = map.get(key);

      if (entry == null) {
	entry = new LRUEntry();
	map.put(key, entry);
      }

      synchronized (entry) {
	// NOTE: Lock order: first map, then entry

	if (entry.expires == Long.MIN_VALUE) {
	  // Entry is about to be removed from map: cancel it!
	  entry.expires = 0;
	}
	
	return entry;
      }
    }
  }


  private class LRUMap
    extends LinkedHashMap<String, LRUEntry> {

    public LRUMap() {
      super (128, 0.75f, true);
    }

    private boolean isFull(LRUEntry eldest, long now) {
      return (size() > maxEntries ||
	      currentSize > maxSize ||
	      eldest.expires < now);
    }

    @Override public boolean removeEldestEntry(Map.Entry<String, LRUEntry> eldest) {
      long now = System.currentTimeMillis();

      LRUEntry entry = eldest.getValue();

      synchronized (entry) {
	// NOTE: Lock order: first map, then entry (map locked by caller)

	if (!isFull(entry, now)) {
	  entry = null;
	}
      }
      
      if (entry != null) {
	Iterator<Map.Entry<String, LRUEntry>> i = entrySet().iterator();

	while (i.hasNext()) {
	  Map.Entry<String, LRUEntry> e = i.next();

	  entry = e.getValue();

	  synchronized (entry) {
	    // NOTE: Lock order: first map, then entry (map locked by caller)

	    if (!isFull(entry, now)) {
	      break;
	    }

	    entryRemoved(e.getKey(), entry.value);
	    i.remove();
	  }
	}
      }

      // Tell implementation not to auto-modify the hash table
      return false;
    }
  }

  class LRUEntry {
    long expires;
    E value;
  }

  private LRUMap map;

  private int maxEntries;
  private long maxSize;
  private long maxAge;

  private long currentSize;

  static final long serialVersionUID = 8565024717836226408L;
}
