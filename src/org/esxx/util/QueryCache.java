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
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.esxx.ESXX;
import org.esxx.ESXXException;
import org.esxx.cache.LRUCache;
import org.h2.value.DataType;
import org.h2.value.Value;

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

	  for (int b = 0; b < qh.getBatches(); ++b) {
	    q.bindParams(b, params, total_param_length, qh);
	  }

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
      ptc.pooledConnection = cp.getConnection();
    }

    ++ptc.refCounter;

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

    public String toString() {
      return "[" + getClass() + " " + refCounter + ", " + pooledConnection + "]";
    }
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
	    ESXX.getInstance().mxRegister("JDBC Query", q.toString(), q.getJMXBean());
	  }

	  public void entryRemoved(String key, Query q) {
	    // Close statment
	    q.close();
	    ESXX.getInstance().mxUnregister("JDBC Query", q.toString());
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

    public String toString() {
      return "[" + getClass() + ": " + expires + ", " + connection + ", " + queryCache + "]";
    }

    private long expires;
    private Connection connection;
    private LRUCache<String, Query> queryCache;
  }

  private static class Query {
    public Query(String parsed_query, int total_param_length, Connection db)
      throws SQLException {

      generatedKeys = db.getMetaData().supportsGetGeneratedKeys();

      try {
	try {
	  sql = db.prepareStatement(parsed_query, (generatedKeys
						   ? Statement.RETURN_GENERATED_KEYS
						   : Statement.NO_GENERATED_KEYS));
	  pmd = sql.getParameterMetaData();
	}
	catch (SQLException ignored) {
	  // PostgreSQL (and probably others) only accepts
	  // RETURN_GENERATED_KEYS for INSERTs, so try again. Since
	  // we're caching, preparing the query twice is ... acceptable.

	  generatedKeys = false;

	  sql = db.prepareStatement(parsed_query, Statement.NO_GENERATED_KEYS);
	  pmd = sql.getParameterMetaData();
	}

      }
      catch (SQLException ex) {
	throw new SQLException("JDBC failed to prepare ESXX-parsed SQL statement: " +
			       parsed_query + ": " + ex.getMessage());
      }

      if (pmd.getParameterCount() != total_param_length) {
	throw new SQLException("JDBC and ESXX report different " +
			       "number of arguments in SQL query");
      }

      try {
	paramTypes = new int[total_param_length +1];

	for (int i = 1; i < paramTypes.length; ++i) {
	  paramTypes[i] = DataType.convertSQLTypeToValueType(pmd.getParameterType(i));

	  // FIXME: Workaround for invalid conversion from Double to
	  // String in H2 MERGE queries
	  if (paramTypes[i] == Value.STRING) {
	    paramTypes[i] = Value.UNKNOWN; // Don't bother with strings for now
	  }
	}
      }
      catch (Exception ex) {
	paramTypes = null;
      }
    }

    public void bindParams(int batch, List<Param> params, int total_params_length, QueryHandler qh)
      throws SQLException {
      ArrayList<Object> objects = new ArrayList<Object>(total_params_length);

      for (Param param : params) {
	qh.resolveParam(batch, param.name, param.length, objects);
      }

      int p = 1;

      for (Object o : objects) {
	if (paramTypes != null && paramTypes[p] != Value.UNKNOWN) {
	  try { // Why reinvent the wheel?
	    o = DataType.convertToValue(null /* session */, o, paramTypes[p])
	      .convertTo(paramTypes[p]).getObject();
	  }
	  catch (Exception ignored) {
	    paramTypes = null; // Don't try again
	  }
	}

	sql.setObject(p, o);
	++p;
      }

      if (qh.getBatches() > 1) {
	sql.addBatch();
	sql.clearParameters();
      }
    }

    public void execute(QueryHandler qh)
      throws SQLException {
      try {
	int set = 0;
	boolean has_result = false;
	int[] update_counts = null;

	if (qh.getBatches() > 1) {
	  update_counts = sql.executeBatch();

	  for (int i : update_counts) {
	    if (i < 0) {
	      // If the driver didn't do it, we do
	      throw new BatchUpdateException("JDBC driver didn't throw, but I do", update_counts);
	    }
	  }
	}
	else {
	  has_result = sql.execute();
	}

	// I have no idea what to do with update_counts here ...

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

	    result = generatedKeys ? sql.getGeneratedKeys() : null;
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
	    Param      param = new Param(name, qh.getParamLength(0 /* def. batch */, name));
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

	    if (sb == null) {
	      throw new ESXXException("Failed to resolve SQL parameter '" + name + "'.");
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

    public synchronized JMXBean getJMXBean() {
      if (jmxBean == null) {
	jmxBean = new JMXBean();
      }

      return jmxBean;
    }

    private class JMXBean 
      extends javax.management.StandardEmitterMBean
      implements org.esxx.jmx.QueryMXBean {

      public JMXBean() {
	super(org.esxx.jmx.QueryMXBean.class, true,
	      new javax.management.NotificationBroadcasterSupport());
      }

      @Override public String getQuery() {
	return sql.toString();
      }

      @Override public int getParameterCount() {
	try {
	  return pmd.getParameterCount();
	}
	catch (SQLException ignored) {
	  return -1;
	}
      }

      @Override public String getConnection() {
	try {
	  return sql.getConnection().toString(); 
	} 
	catch (SQLException ignored) {
	  return "<Unknown>";
	}
      }

      @Override public boolean isGeneratedKeys() {
	return generatedKeys;
      }
    }

    private PreparedStatement sql;
    private ParameterMetaData pmd;
    private boolean generatedKeys;
    private int[] paramTypes;
    private JMXBean jmxBean;
  }
}
