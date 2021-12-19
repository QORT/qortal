package org.qortal.repository.hsqldb.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.PresenceTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;
import org.qortal.transaction.PresenceTransaction.PresenceType;

public class HSQLDBPresenceTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBPresenceTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT nonce, presence_type, timestamp_signature FROM PresenceTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			int nonce = resultSet.getInt(1);
			int presenceTypeValue = resultSet.getInt(2);
			PresenceType presenceType = PresenceType.valueOf(presenceTypeValue);

			byte[] timestampSignature = resultSet.getBytes(3);

			return new PresenceTransactionData(baseTransactionData, nonce, presenceType, timestampSignature);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch presence transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		PresenceTransactionData presenceTransactionData = (PresenceTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("PresenceTransactions");

		saveHelper.bind("signature", presenceTransactionData.getSignature())
				.bind("nonce", presenceTransactionData.getNonce())
				.bind("presence_type", presenceTransactionData.getPresenceType().value)
				.bind("timestamp_signature", presenceTransactionData.getTimestampSignature());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save chat transaction into repository", e);
		}
	}

}
