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

import java.net.URI;
import java.sql.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Properties;
import org.esxx.ESXX;
import org.esxx.ESXXException;
import org.esxx.cache.LRUCache;
import org.esxx.util.StringUtil;

import org.mozilla.javascript.Scriptable;

/** An easy-to-use SQL query cache and connection pool. */

public class QueryCache {
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

  public void executeQuery(URI uri, Properties props, final String query, final QueryHandler qh)
    throws SQLException {
    withConnection(uri, props, new ConnectionCallback() {
	public void execute(PooledConnection pc) 
	  throws SQLException {

	  List<Query.Param> params = new ArrayList<Query.Param>(32);
	  String parsed_query      = Query.parseQuery(query, params, qh);
	  int total_param_length   = 0;

	  for (Query.Param p : params) {
	    total_param_length += p.length;
	  }

	  Query q = pc.getQuery(parsed_query, total_param_length);
	  q.bindParams(params, total_param_length, qh);
	  q.execute(qh);
	}
      });
  }

  public void executeTransaction(URI uri, Properties props, final QueryHandler qh) 
    throws SQLException {
    withConnection(uri, props, new ConnectionCallback() {
	public void execute(PooledConnection pc) 
	  throws SQLException {

	  boolean committed = false;
	  Connection c = pc.getConnection();
	  c.setAutoCommit(false);

	  try {
	    try {
	      qh.handleTransaction();
	      committed = true;
	      c.commit();
	    }
	    finally {
	      if (!committed) {
		c.rollback();
	      }
	    }
	  }
	  finally {
	    c.setAutoCommit(true);
	  }
	}
      });
  }


  private interface ConnectionCallback {
    public void execute(PooledConnection pc)
      throws SQLException;
  }

  private static Properties nullProperties = new Properties();

  private void withConnection(URI uri, Properties props, ConnectionCallback cb) 
    throws SQLException {
    ConnectionPool cp;

    if (props == null) {
      props = nullProperties;
    }

    synchronized (connectionPools) {
      ConnectionKey key = new ConnectionKey(uri, props);

      cp = connectionPools.get(key);

      if (cp == null) {
	cp = new ConnectionPool(uri, props);
	connectionPools.put(key, cp);
      }
    }

    // If this thread is already inside withConnection(), re-use the
    // same PooledConnection as the outermost withConnection() call
    // returned.
    PerThreadConnection ptc = perThreadConnection.get();

    if (ptc.refCounter == 0) {
      ++ptc.refCounter;
      ptc.pooledConnection = cp.getConnection();
    }

    try {
      cb.execute(ptc.pooledConnection);
    }
    finally {
      --ptc.refCounter;

      if (ptc.refCounter == 0) {
	cp.releaseConnection(ptc.pooledConnection);
	ptc.pooledConnection = null;
      }
    }
  }
  
  private static class PerThreadConnection {
    long refCounter;
    PooledConnection pooledConnection;
  }

  private static final ThreadLocal<PerThreadConnection> perThreadConnection = 
    new ThreadLocal<PerThreadConnection>() {
    @Override protected PerThreadConnection initialValue() {
      return new PerThreadConnection();
    }
  };

  private int maxConnections;
  private long connectionTimeout;
  private int maxQueries;
  private long queryTimeout;
  private final HashMap<ConnectionKey, ConnectionPool> connectionPools;


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
      PooledConnection res;

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
		connections.wait();
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
      if (pc == null) {
	return;
      }

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
    private final ArrayDeque<PooledConnection> connections;
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

    public Connection getConnection() {
      return connection;
    }

    public Query getQuery(final String parsed_query, final int total_param_length)
      throws SQLException {
      // "Touch" connection
      expires = System.currentTimeMillis() + connectionTimeout;

      try {
	return queryCache.add(parsed_query, new LRUCache.ValueFactory<String, Query>() {
	    public Query create(String key, long expires)
	      throws SQLException {
	      return new Query(parsed_query, total_param_length, connection);
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

  private static class Query {
    public Query(String parsed_query, int total_param_length, Connection db)
      throws SQLException {

      try {
	sql = db.prepareCall(parsed_query);
	pmd = sql.getParameterMetaData();
      }
      catch (SQLException ex) {
	throw new SQLException("JDBC failed to prepare ESXX-parsed SQL statement: " +
			       parsed_query + ": " + ex.getMessage());
      }

      if (pmd.getParameterCount() != total_param_length) {
	throw new SQLException("JDBC and ESXX report different " +
			       "number of arguments in SQL query");
      }
    }

    public void bindParams(List<Param> params, int total_params_length, QueryHandler qh) 
      throws SQLException {
      ArrayList<Object> objects = new ArrayList<Object>(total_params_length);

      for (Param param : params) {
	qh.resolveParam(param.name, param.length, objects);
      }

      int p = 1;
      for (Object o : objects) {
	sql.setObject(p, o);
	++p;
      }
    }

    public void execute(QueryHandler qh)
      throws SQLException {
      try {
	int set = 1;
	boolean has_result = sql.execute();
	int update_count;
      
	while (true) {
	  ResultSet result;

	  if (has_result) {
	    update_count = -1;
	    result = sql.getResultSet();
	  }
	  else {
	    update_count = sql.getUpdateCount();

	    if (update_count == -1) {
	      break;
	    }

	    result = sql.getGeneratedKeys();
	  }

	  qh.handleResult(set, update_count, result);
	  ++set;

	  has_result = sql.getMoreResults();
	}
      }
      finally {
	sql.clearParameters();
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

    public static String parseQuery(String unparsed_query, 
				    final List<Param> query_params,
				    final QueryHandler qh) {
      query_params.clear();

      return StringUtil.format(unparsed_query, new StringUtil.ParamResolver() {
	  public String resolveParam(String name) {
	    Param      param = new Param(name, qh.getParamLength(name));
	    StringBuilder sb = null;

	    for (int i = 0; i < param.length; ++i) {
	      if (sb == null) {
		sb = new StringBuilder();
	      }
	      else {
		sb.append(',');
	      }

	      sb.append('?');
	    }
	  
	    query_params.add(param);
	    return sb.toString();
	  }
	});
    }

    public static class Param {
      Param(String n, int l) {
	name   = n;
	length = l;
      }

      String name;
      int length;
    };

    private CallableStatement sql;
    private ParameterMetaData pmd;
  }
}
