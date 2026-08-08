package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.UpdateAssetTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBUpdateAssetTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBUpdateAssetTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT asset_id, new_owner, new_description, new_data, orphan_reference FROM UpdateAssetTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			long assetId = resultSet.getLong(1);
			String newOwner = resultSet.getString(2);
			String newDescription = resultSet.getString(3);
			String newData = resultSet.getString(4);
			byte[] orphanReference = resultSet.getBytes(5);

			return new UpdateAssetTransactionData(baseTransactionData, assetId, newOwner, newDescription, newData, orphanReference);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch update asset transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		UpdateAssetTransactionData updateAssetTransactionData = (UpdateAssetTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("UpdateAssetTransactions");

		saveHelper.bind("signature", updateAssetTransactionData.getSignature())
				.bind("owner", updateAssetTransactionData.getOwnerPublicKey())
				.bind("asset_id", updateAssetTransactionData.getAssetId())
				.bind("new_owner", updateAssetTransactionData.getNewOwner())
				.bind("new_description", updateAssetTransactionData.getNewDescription())
				.bind("new_data", updateAssetTransactionData.getNewData())
				.bind("orphan_reference", updateAssetTransactionData.getOrphanReference());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save update asset transaction into repository", e);
		}
	}

}
