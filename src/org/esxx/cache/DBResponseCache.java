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

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.*;
import org.esxx.ESXX;
import org.h2.jdbcx.*;

public class DBResponseCache
  extends ResponseCache {

  public DBResponseCache(String path, int max_entries, long max_size, long max_age) {
    super();

    dbPath     = new File(path).getAbsoluteFile();
    maxEntries = max_entries;
    maxSize    = max_size;
    maxAge     = max_age;

    executor   = Executors.newCachedThreadPool();
  }

  @Override public CacheResponse get(URI uri, String method, Map<String,List<String>> headers) {
    if (!initDB()) {
      return null;
    }

    // Make sure the request allows an entry to be fetched from the cache
    if (!"GET".equals(method)) {
      return null;
    }

    long max_age   = Long.MAX_VALUE;
    long min_fresh = 0;

    List<String> cache_control = headers.get("Cache-Control");

    if (cache_control != null) {
      for (String cc : cache_control) {
	for (String f : COMMA_SEPARATED.split(cc)) {
	  if (NOCACHE_REQUEST.matcher(f).matches()) {
	    // Not cachable
	    return null;
	  }

	  if (f.startsWith("max-age=")) {
	    max_age = Long.parseLong(f.substring(8));
	  }
	  else if (f.startsWith("min-fresh=")) {
	    min_fresh = Long.parseLong(f.substring(10));
	  }
	}
      }
    }

    List<String> auth = headers.get("Authorization");
    boolean only_public = auth != null && !auth.isEmpty();

    System.err.println("Is " + method + " " + uri + " in cache?");

    return null;
  }

  @Override public CacheRequest put(URI uri, URLConnection conn) {
    long length = conn.getContentLength();

    if (length > maxSize * RELATIVE_MAX_SIZE) {
      return null;
    }

    if (!initDB()) {
      return null;
    }

    System.err.println("Put " + uri + " from " + conn + " in cache?");

    try {
      HttpURLConnection hc = (HttpURLConnection) conn;
      String method = hc.getRequestMethod();

      if (UPDATING_VERBS.matcher(method).matches()) {
	// Update cache
	return null;
      }

      if (DELETING_VERBS.matcher(method).matches()) {
	// Delete existing cached entry
	return null;
      }

      if (!CACHABLE_VERBS.matcher(hc.getRequestMethod()).matches()) {
	// Not cachable
	return null;
      }

      boolean is_public = false;
      long max_age = -1;

      List<String> cache_control = hc.getHeaderFields().get("Cache-Control");

      if (cache_control != null) {
	for (String cc : cache_control) {
	  for (String f : COMMA_SEPARATED.split(cc)) {
	    if (NOCACHE_CONTROL.matcher(f).matches()) {
	      // Not cachable
	      return null;
	    }

	    if (f.equals("public")) {
	      is_public = true;
	    }
	    else if (f.startsWith("max-age=")) {
	      if (max_age == -1) {
		max_age = 1000 * Long.parseLong(f.substring(8));
	      }
	    }
	    else if (f.startsWith("s-maxage=")) {
	      max_age = 1000 * Long.parseLong(f.substring(9));
	    }
	  }
	}
      }

      if (max_age == -1) {
	// No max-age, so try the Expires header
	long date    = hc.getDate();
	long expires = hc.getExpiration();

	if (date != 0 && expires != 0) {
	  max_age = expires - date;
	}
      }

      if (max_age <= 0) {
	return null;
      }

      // DELETE FROM foo foo
      // WHERE foo.id =
      //   (SELECT fooInner.id
      //    FROM      foo fooInner
      //    ORDER BY 1 DESC
      //    LIMIT 5);

      System.err.println("YES!");
      return null;
    }
    catch (ClassCastException ex) {
      // We only cache HTTP traffic ATM
      return null;
    }
  }

  private synchronized boolean initDB() {
    if (initialized) {
      return initStatus;
    }

    try {
      initialized = true;

      JdbcDataSource ds = new JdbcDataSource();
      ds.setURL("jdbc:h2:" + dbPath);
      ds.setUser("sa");
      ds.setPassword("");
      connectionPool = JdbcConnectionPool.create(ds);

      Connection connection = connectionPool.getConnection();
      Statement s = null;

      try {
	s = connection.createStatement();

	ResultSet r = s.executeQuery("select table_name from information_schema.tables " +
				     "where table_type = 'TABLE'");
	int total_rows = 0;
	int valid_rows = 0;

	while (r.next()) {
	  ++total_rows;

	  if (r.getString(1).matches("ESXX_WEBCACHE|URIS|HEADERS|ENTITIES")) {
	    ++valid_rows;
	  }
	}

	if (total_rows == 0) {
	  getLogger().info("Initializing WebCache " + dbPath);

	  s.execute("create table esxx_webcache (version int not null)");
	  s.execute("create table uris ("
		    + "id bigint auto_increment not null primary key ,"
		    + "uri varchar not null unique,"
		    + "last_modified timestamp not null,"
		    + "expires timestamp not null,"
		    + "accessed timestamp not null,"
		    + "public boolean not null)");
	  s.execute("create table headers ("
		    + "uri_id bigint not null references uris(id) on delete cascade,"
		    + "header varchar not null,"
		    + "value varchar not null)");
	  s.execute("create table entities ("
		    + "uri_id bigint not null unique references uris(id) on delete cascade,"
		    + "entity blob not null)");

	  s.execute("insert into esxx_webcache values(" + WEBCACHE_VERSION + ")");
	  initStatus = true;
	}
	else if (total_rows >= valid_rows && valid_rows == 4) {
	  r = s.executeQuery("select version from esxx_webcfache");

	  if (r.last() && r.getRow() == 1 && r.getInt(1) == WEBCACHE_VERSION) {
	    getLogger().config("WebCache " + dbPath + " is valid");
	    initStatus = true;
	  }
	  else {
	    getLogger().severe("Incorrect WebCache version -- please remove " + dbPath);
	  }
	}
	else {
	  getLogger().warning(dbPath + " is not a valid WebCache DB!");
	}
      }
      finally {
	if (s != null) try { s.close(); } catch (Exception ex) {}
	try { connection.close(); } catch (Exception ex) {}
      }
    }
    catch (SQLException ex) {
      getLogger().warning("Failed to open WebCache " + dbPath + ": " + ex.getMessage());
      initStatus = false;
    }

    return initStatus;
  }

  private static Logger getLogger() {
    return ESXX.getInstance().getLogger();
  }

  private class BodyInserter
    implements Runnable {
    public BodyInserter(long uri_id) 
      throws IOException {
      inputStream = new PipedInputStream();
      outputStream = new PipedOutputStream(inputStream);
      uriID = uri_id;
    }

    public OutputStream getOutputStream() {
      return outputStream;
    }

    public void run() {
      Connection connection = null;

      try {
	connection = connectionPool.getConnection();
	PreparedStatement s = connection.prepareStatement("merge into entities(uri_id, entity) " +
							  "key (uri_id) " +
							  "values(?, ?)");
	s.setLong(1, uriID);
	s.setBlob(2, inputStream);
	
	if (s.executeUpdate() != 1) {
	  getLogger().warning("WebCache BodyInserter did not affect one and only one row");
	}
      }
      catch (SQLException ex) {
	getLogger().warning("WebCache BodyInserter failed: " + ex.getMessage());
      }
      finally {
	if (connection != null) try { connection.close(); } catch (Exception ex) {}
      }
    }

    private PipedInputStream inputStream;
    private PipedOutputStream outputStream;
    private long uriID;
  }

  private File dbPath;
  private JdbcConnectionPool connectionPool;
  private int maxEntries;
  private long maxSize;
  private long maxAge;
  
  private Executor executor;

  private boolean initialized = false;
  private boolean initStatus = false;

  private static int WEBCACHE_VERSION = 1;
  private static float RELATIVE_MAX_SIZE = 0.25f;

  private static Pattern COMMA_SEPARATED = Pattern.compile("\\s*,\\s*");

  private static Pattern NOCACHE_REQUEST = Pattern.compile("no-cache|no-store");
  private static Pattern NOCACHE_CONTROL = Pattern.compile("no-cache|no-store|private");

  private static Pattern CACHABLE_VERBS = Pattern.compile("GET|POST");
  private static Pattern UPDATING_VERBS = Pattern.compile("HEAD");
  private static Pattern DELETING_VERBS = Pattern.compile("PUT|DELETE");
}
