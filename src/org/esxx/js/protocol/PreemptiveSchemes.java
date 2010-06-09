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

package org.esxx.js.protocol;

import java.net.URI;
import java.util.*;
import java.util.regex.*;
import org.apache.http.auth.*;
import org.esxx.ESXX;

class PreemptiveSchemes {
    public PreemptiveSchemes() {
      Properties p = ESXX.getInstance().getSettings();

      maxEntries = Integer.parseInt(p.getProperty("esxx.cache.preemptive-http.max_entries",
						  "32"));
      maxAge = (long) (Double.parseDouble(p.getProperty("esxx.cache.preemptive-http.max_age",
							"3600")) * 1000);
    }

    public synchronized void purgeEntries() {
      long now = System.currentTimeMillis();
      Iterator<PreemptiveScheme> i = schemes.iterator();

      while (i.hasNext()) {
	PreemptiveScheme ps = i.next();

	if (ps.getCreatedDate() + maxAge > now) {
	  i.remove();
	}
      }
    }

    public synchronized PreemptiveScheme find(String uri) {
      Iterator<PreemptiveScheme> i = schemes.iterator();

      while (i.hasNext()) {
	PreemptiveScheme ps = i.next();

	if (ps.matches(uri)) {
	  // Move ps to the front of the LRU list and return
	  i.remove();
	  schemes.addFirst(ps);
	  return ps;
	}
      }

      return null;
    }

    public synchronized void remember(String uri_prefix, AuthScheme scheme, AuthScope scope) {
      String[] prefices = { uri_prefix };
      String    domains = scheme.getParameter("domain");

      if (domains != null && !domains.isEmpty()) {
	prefices = wsPattern.split(domains);
      }

      for (String prefix : prefices) {
	// Add schemes to the front of the LRU list
	if (prefix.startsWith("/")) {
	  prefix = URI.create(uri_prefix).resolve(prefix).toString();
	}

	if (find(prefix) == null) {
	  schemes.addFirst(new PreemptiveScheme(prefix, scheme, scope));
	}
      }

      // Trim cache
      while (schemes.size() > maxEntries) {
	schemes.removeLast();
      }
    }

    public static class PreemptiveScheme {
	public PreemptiveScheme(String uri, AuthScheme auth_scheme, AuthScope auth_scope) {
	  created = System.currentTimeMillis();

	  if (uri.endsWith("/")) {
	    rule = Pattern.compile("^" + Pattern.quote(uri) + ".*");
	  }
	  else {
	    rule = Pattern.compile("^" + Pattern.quote(uri) + "($|/.*)");
	  }

	  scheme = auth_scheme;
	  scope  = auth_scope;
	}

	public long getCreatedDate() {
	  return created;
	}

	public boolean matches(String uri) {
	  // System.out.println(uri + " matches " + rule + ": " + rule.matcher(uri).matches());
	  return rule.matcher(uri).matches();
	}

	public AuthScheme getScheme() {
	  return scheme;
	}

	public AuthScope getScope() {
	  return scope;
	}

	public String toString() {
	  return "[PreemptiveScheme " + rule + ", " + scheme + ", " + scope + "]";
	}

	private long created;
	private Pattern rule;
	private AuthScheme scheme;
	private AuthScope scope;
    }

    // (We should probably switch to a LinkedHashMap instead.)
    private Deque<PreemptiveScheme> schemes = new LinkedList<PreemptiveScheme>();
    private int maxEntries;
    private long maxAge;

    private static Pattern wsPattern = Pattern.compile("\\s+");
}
