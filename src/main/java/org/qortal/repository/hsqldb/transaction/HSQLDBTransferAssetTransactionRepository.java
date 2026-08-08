package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.TransferAssetTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBTransferAssetTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBTransferAssetTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		// LEFT OUTER JOIN because asset might not exist (e.g. if ISSUE_ASSET & TRANSFER_ASSET are both unconfirmed)
		String sql = "SELECT recipient, asset_id, amount, asset_name FROM TransferAssetTransactions LEFT OUTER JOIN Assets USING (asset_id) WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String recipient = resultSet.getString(1);
			long assetId = resultSet.getLong(2);
			long amount = resultSet.getLong(3);
			String assetName = resultSet.getString(4);

			return new TransferAssetTransactionData(baseTransactionData, recipient, amount, assetId, assetName);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transfer asset transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("TransferAssetTransactions");

		saveHelper.bind("signature", transferAssetTransactionData.getSignature()).bind("sender", transferAssetTransactionData.getSenderPublicKey())
				.bind("recipient", transferAssetTransactionData.getRecipient()).bind("asset_id", transferAssetTransactionData.getAssetId())
				.bind("amount", transferAssetTransactionData.getAmount());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save transfer asset transaction into repository", e);
		}
	}

}
