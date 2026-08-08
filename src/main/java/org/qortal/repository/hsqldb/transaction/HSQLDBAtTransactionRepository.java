package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.ATTransactionData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBAtTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBAtTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT AT_address, recipient, amount, asset_id, message FROM ATTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String atAddress = resultSet.getString(1);
			String recipient = resultSet.getString(2);

			Long amount = resultSet.getLong(3);
			if (amount == 0 && resultSet.wasNull())
				amount = null;

			Long assetId = resultSet.getLong(4);
			if (assetId == 0 && resultSet.wasNull())
				assetId = null;

			byte[] message = resultSet.getBytes(5);

			return new ATTransactionData(baseTransactionData, atAddress, recipient, amount, assetId, message);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		ATTransactionData atTransactionData = (ATTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("ATTransactions");

		saveHelper.bind("signature", atTransactionData.getSignature()).bind("AT_address", atTransactionData.getATAddress())
				.bind("recipient", atTransactionData.getRecipient()).bind("amount", atTransactionData.getAmount())
				.bind("asset_id", atTransactionData.getAssetId()).bind("message", atTransactionData.getMessage());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save AT transaction into repository", e);
		}
	}

}
