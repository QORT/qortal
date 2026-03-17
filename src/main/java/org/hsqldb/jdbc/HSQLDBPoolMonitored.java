package org.hsqldb.jdbc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hsqldb.jdbc.pool.JDBCPooledConnection;
import org.qortal.data.system.DbConnectionInfo;
import org.qortal.repository.hsqldb.HSQLDBRepositoryFactory;

import javax.sql.ConnectionEvent;
import javax.sql.PooledConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Class HSQLDBPoolMonitored
 *
 * This class uses the same logic as HSQLDBPool. The only difference is it monitors the state of every connection
 * to the database. This is used for debugging purposes only.
 */
public class HSQLDBPoolMonitored extends HSQLDBPool {

	private static final Logger LOGGER = LogManager.getLogger(HSQLDBRepositoryFactory.class);

	private static final String EMPTY = "Empty";
	private static final String AVAILABLE = "Available";
	private static final String ALLOCATED = "Allocated";

	private ConcurrentHashMap<Integer, DbConnectionInfo> infoByIndex;

	public HSQLDBPoolMonitored(int poolSize) {
		super(poolSize);

		this.infoByIndex = new ConcurrentHashMap<>(poolSize);
	}

	/**
	 * Tries to retrieve a new connection using the properties that have already been
	 * set.
	 *
	 * @return  a connection to the data source, or null if no spare connections in pool
	 * @exception SQLException if a database access error occurs
	 */
	public Connection tryConnection() throws SQLException {
		for (int i = 0; i < states.length(); i++) {
			if (states.compareAndSet(i, RefState.available, RefState.allocated)) {
				JDBCPooledConnection pooledConnection = connections[i];

				if (pooledConnection == null)
					// Probably shutdown situation
					return null;

				infoByIndex.put(i, new DbConnectionInfo(System.currentTimeMillis(), Thread.currentThread().getName(), ALLOCATED));

				return pooledConnection.getConnection();
			}

			if (states.compareAndSet(i, RefState.empty, RefState.allocated)) {
				try {
					JDBCPooledConnection pooledConnection = (JDBCPooledConnection) source.getPooledConnection();

					if (pooledConnection == null)
						// Probably shutdown situation
						return null;

					pooledConnection.addConnectionEventListener(this);
					pooledConnection.addStatementEventListener(this);
					connections[i] = pooledConnection;

					infoByIndex.put(i, new DbConnectionInfo(System.currentTimeMillis(), Thread.currentThread().getName(), ALLOCATED));

					return pooledConnection.getConnection();
				} catch (SQLException e) {
					states.set(i, RefState.empty);
					infoByIndex.put(i, new DbConnectionInfo(System.currentTimeMillis(), Thread.currentThread().getName(), EMPTY));
				}
			}
		}

		return null;
	}

	public Connection getConnection() throws SQLException {
		int var1 = 300;
		if (this.source.loginTimeout != 0) {
			var1 = this.source.loginTimeout * 10;
		}

		if (this.closed) {
			throw new SQLException("connection pool is closed");
		} else {
			for(int var2 = 0; var2 < var1; ++var2) {
				for(int var3 = 0; var3 < this.states.length(); ++var3) {
					if (this.states.compareAndSet(var3, 1, 2)) {
						infoByIndex.put(var3, new DbConnectionInfo(System.currentTimeMillis(), Thread.currentThread().getName(), ALLOCATED));
						return this.connections[var3].getConnection();
					}

					if (this.states.compareAndSet(var3, 0, 2)) {
						try {
							JDBCPooledConnection var4 = (JDBCPooledConnection)this.source.getPooledConnection();
							var4.addConnectionEventListener(this);
							var4.addStatementEventListener(this);
							this.connections[var3] = var4;

							infoByIndex.put(var3, new DbConnectionInfo(System.currentTimeMillis(), Thread.currentThread().getName(), ALLOCATED));

							return this.connections[var3].getConnection();
						} catch (SQLException var6) {
							this.states.set(var3, 0);
							infoByIndex.put(var3, new DbConnectionInfo(System.currentTimeMillis(), Thread.currentThread().getName(), EMPTY));
						}
					}
				}

				try {
					Thread.sleep(100L);
				} catch (InterruptedException var5) {
				}
			}

			throw JDBCUtil.invalidArgument();
		}
	}

	public void connectionClosed(ConnectionEvent event) {
		PooledConnection connection = (PooledConnection) event.getSource();

		for (int i = 0; i < connections.length; i++) {
			if (connections[i] == connection) {
				states.set(i, RefState.available);
				infoByIndex.put(i, new DbConnectionInfo(System.currentTimeMillis(), Thread.currentThread().getName(), AVAILABLE));
				break;
			}
		}
	}

	public void connectionErrorOccurred(ConnectionEvent event) {
		PooledConnection connection = (PooledConnection) event.getSource();

		for (int i = 0; i < connections.length; i++) {
			if (connections[i] == connection) {
				states.set(i, RefState.allocated);
				connections[i] = null;
				states.set(i, RefState.empty);
				infoByIndex.put(i, new DbConnectionInfo(System.currentTimeMillis(), Thread.currentThread().getName(), EMPTY));
				break;
			}
		}
	}

	public List<DbConnectionInfo> getDbConnectionsStates() {

		return infoByIndex.values().stream()
			.sorted(Comparator.comparingLong(DbConnectionInfo::getUpdated))
			.collect(Collectors.toList());
	}

	private int findConnectionIndex(ConnectionEvent connectionEvent) {
		PooledConnection pooledConnection = (PooledConnection) connectionEvent.getSource();

		for(int i = 0; i < this.connections.length; ++i) {
			if (this.connections[i] == pooledConnection) {
				return i;
			}
		}

		return -1;
	}
}