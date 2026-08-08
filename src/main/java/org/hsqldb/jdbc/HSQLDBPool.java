package org.hsqldb.jdbc;

import org.hsqldb.jdbc.pool.JDBCPooledConnection;

import java.sql.Connection;
import java.sql.SQLException;

public class HSQLDBPool extends JDBCPool {

	public HSQLDBPool(int poolSize) {
		super(poolSize);
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

					return pooledConnection.getConnection();
				} catch (SQLException e) {
					states.set(i, RefState.empty);
				}
			}
		}

		return null;
	}

}
