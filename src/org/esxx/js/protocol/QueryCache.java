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
import java.sql.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.esxx.*;
import org.esxx.js.*;
import org.esxx.cache.LRUCache;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class QueryCache {

  public interface Callback {
    Object execute(Query q) 
      throws SQLException;
  }

  public QueryCache(int max_connections, long connection_timeout,
		    int max_queries, long query_timeout) {
    maxConnections = max_connections;
    connectionTimeout = connection_timeout;
    maxQueries = max_queries;
    queryTimeout = query_timeout;

    connectionPools = new HashMap<ConnectionKey, ConnectionPool>();
  }
  
  public void purgeConnections() {
    synchronized (connectionPools) {
      for (ConnectionPool cp : connectionPools.values()) {
	cp.purgeConnections();
      }
    }
  }
  

  Object executeQuery(URI uri, Properties props, String query, Callback cb)
    throws SQLException {
    ConnectionKey key = new ConnectionKey(uri, props);
    ConnectionPool cp;

    synchronized (connectionPools) {
      cp = connectionPools.get(key);

      if (cp == null) {
	cp = new ConnectionPool(uri, props);
	connectionPools.put(key, cp);
      }
    }

    PooledConnection pc = cp.getConnection();

    try {
      return cb.execute(pc.getQuery(query));
    }
    finally {
      cp.releaseConnection(pc);
    }
  }

  private int maxConnections;
  private long connectionTimeout;
  private int maxQueries;
  private long queryTimeout;
  private HashMap<ConnectionKey, ConnectionPool> connectionPools;

  private class ConnectionKey {
    public ConnectionKey(URI uri, Properties props) {
      this.uri = uri;
      this.props = props;
    }

    @Override public boolean equals(Object o) {
      try {
	ConnectionKey ck = (ConnectionKey) o;

	return uri.equals(ck.uri) && props.equals(ck.props);
      }
      catch (ClassCastException ex) {
	return false;
      }
    }

    @Override public int hashCode() {
      return uri.hashCode() + props.hashCode();
    }

    private URI uri;
    private Properties props;
  }
  

  private class ConnectionPool {
    public ConnectionPool(URI uri, Properties props) {
      this.uri = uri;
      this.props = props;
      connections = new ArrayDeque<PooledConnection>();
      numConnections = 0;
    }

    public PooledConnection getConnection() 
      throws SQLException {
      PooledConnection res = null;

      synchronized (connections) {
	do {
	  res = connections.pollFirst();

	  if (res == null) {
	    if (numConnections < maxConnections) {
	      // Break out and create a new connection
	      ++numConnections;
	      break;
	    }
	    else {
	      try {
		res.wait();
	      }
	      catch (InterruptedException ex) {
		ex.printStackTrace();
		Thread.currentThread().interrupt();
		throw new ESXXException("Failed to get a pooled JDBC connection.", ex);
	      }
	    }
	  }

	} while (res == null);
      }

      if (res == null) {
	res = new PooledConnection(uri, props);
      }

      return res;
    }

    public void releaseConnection(PooledConnection pc) {
      synchronized (connections) {
	connections.addFirst(pc);
	connections.notify();
      }
    }

    public void purgeConnections() {
      long now = System.currentTimeMillis();

      synchronized (connections) {
	PooledConnection pc;

	// Purge exipres connections
	while ((pc = connections.peekLast()) != null && pc.getExpires() < now) {
	  pc.close();
	  --numConnections;
	  connections.removeLast();
	}

	// Purge expired queries from all remaining connections
	for (PooledConnection pc2 : connections) {
	  pc2.purgeQueries();
	}
      }
    }

    private URI uri;
    private Properties props;
    private ArrayDeque<PooledConnection> connections;
    private int numConnections;
  }


  private class PooledConnection {
    public PooledConnection(URI uri, Properties props) 
      throws SQLException {
      connection = DriverManager.getConnection(uri.toString(), props);
      queryCache = new LRUCache<String, Query>(maxQueries, queryTimeout);

      queryCache.addListener(new LRUCache.LRUListener<String, Query>() {
	  public void entryAdded(String key, Query q) {
	    // Do nothing
	  }

	  public void entryRemoved(String key, Query q) {
	    // Close statment
	    q.close();
	  }
	});

      // "Touch" connection
      expires = System.currentTimeMillis() + connectionTimeout;
    }

    public long getExpires() {
      return expires;
    }

    public Query getQuery(final String query) 
      throws SQLException {
      // "Touch" connection
      expires = System.currentTimeMillis() + connectionTimeout;

      try {
	return queryCache.add(query, new LRUCache.ValueFactory<String, Query>() {
	    public Query create(String key, long age)
	      throws SQLException {
	      return new Query(query, connection);
	    }
	  }, 0);
      }
      catch (SQLException ex) {
	throw ex;
      }
      catch (Exception ex) {
	throw new ESXXException("Unexpected exception in getQuery: " + ex.getMessage(), ex);
      }
    }

    public void purgeQueries() {
      // Purge expired Query objects for this connection
      queryCache.filterEntries(null);
    }

    public void close() {
      // Close all statements
      queryCache.filterEntries(new LRUCache.EntryFilter<String, Query>() {
	  public boolean isStale(String key, Query app, long created) {
	    return true;
	  }
	});
      
      // Close connection
      try {
	connection.close();
      }
      catch (SQLException ex) {
	// Log and ignore
	ESXX.getInstance().getLogger().log(java.util.logging.Level.WARNING, 
					   "Failed to close pooled connection: " + ex.getMessage(),
					   ex);
      }
    }

    private long expires;
    private Connection connection;
    private LRUCache<String, Query> queryCache;
  }


  public static class Query {
    public Query(String unparsed_query, Connection db)
      throws SQLException {

      params = new ArrayList<String>();
      String query = parseQuery(unparsed_query, params);

      try {
	sql = db.prepareCall(query);
	pmd = sql.getParameterMetaData();
      }
      catch (SQLException ex) {
	throw Context.reportRuntimeError("JDBC failed to prepare parsed SQL statement: " +
					 query + ": " + ex.getMessage());
      }

      if (pmd.getParameterCount() != params.size()) {
	throw Context.reportRuntimeError("JDBC and ESXX report different " +
					 "number of arguments in SQL query");
      }
    }

    public boolean needParams()
      throws SQLException {
      return pmd.getParameterCount() != 0;
    }

    public void bindParams(Context cx, Scriptable object)
      throws SQLException {

      int p = 1;
      for (String name : params) {
	String value = Context.toString(ProtocolHandler.evalProperty(cx, object, name));

	switch (pmd.getParameterType(p)) {
	default:
	  sql.setObject(p, value);
	  break;
	}

	++p;
      }
    }

    public Object execute()
      throws SQLException {
      if (sql.execute()) {
	sql.clearParameters();
	return sql.getResultSet();
      }
      else {
	sql.clearParameters();
	return new Integer(sql.getUpdateCount());
      }
    }

    public void close() {
      try {
	sql.close();
      }
      catch (SQLException ex) {
	// Log and ignore
	ESXX.getInstance().getLogger().log(java.util.logging.Level.WARNING, 
					   "Failed to close statement: " + ex.getMessage(),
					   ex);
      }
    }

    private static String parseQuery(String unparsed_query, List<String> parsed_params) {
      StringBuffer s = new StringBuffer();
      Matcher      m = paramPattern.matcher(unparsed_query);

      while (m.find()) {
	String g = m.group();

	if (m.start(1) != -1) {
	  // Match on group 1, which is our parameter pattern; append a single '?'
	  m.appendReplacement(s, "?");
	  parsed_params.add(g.substring(1, g.length() - 1));
	}
	else {
	  // Match on quoted strings, which we just copy as-is
	  m.appendReplacement(s, g);
	}
      }

      m.appendTail(s);

      return s.toString();
    }

    private ArrayList<String> params;

    private CallableStatement sql;
    private ParameterMetaData pmd;

    private static final String quotePattern1 = "('((\\\\')|[^'])+')";
    private static final String quotePattern2 = "(`((\\\\`)|[^`])+`)";
    private static final String quotePattern3 = "(\"((\\\\\")|[^\"])+\")";

    private static final Pattern paramPattern = Pattern.compile(
	"(\\{[^\\}]+\\})" +    // Group 1: Matches {identifier}
	"|" + quotePattern1 + "|" + quotePattern2 + "|" + quotePattern3);
  }
}
