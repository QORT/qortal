package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CreateAssetOrderTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBCreateAssetOrderTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBCreateAssetOrderTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		// LEFT OUTER JOIN because asset might not exist (e.g. if ISSUE_ASSET & CREATE_ASSET_ORDER are both unconfirmed)
		String sql = "SELECT have_asset_id, amount, want_asset_id, price, HaveAsset.asset_name, WantAsset.asset_name "
				+ "FROM CreateAssetOrderTransactions "
				+ "LEFT OUTER JOIN Assets AS HaveAsset ON HaveAsset.asset_id = have_asset_id "
				+ "LEFT OUTER JOIN Assets AS WantAsset ON WantAsset.asset_id = want_asset_id "
				+ "WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			long haveAssetId = resultSet.getLong(1);
			long amount = resultSet.getLong(2);
			long wantAssetId = resultSet.getLong(3);
			long price = resultSet.getLong(4);
			String haveAssetName = resultSet.getString(5);
			String wantAssetName = resultSet.getString(6);

			return new CreateAssetOrderTransactionData(baseTransactionData, haveAssetId, wantAssetId, amount, price, haveAssetName, wantAssetName);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch create order transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		CreateAssetOrderTransactionData createOrderTransactionData = (CreateAssetOrderTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("CreateAssetOrderTransactions");

		saveHelper.bind("signature", createOrderTransactionData.getSignature()).bind("creator", createOrderTransactionData.getCreatorPublicKey())
				.bind("have_asset_id", createOrderTransactionData.getHaveAssetId()).bind("amount", createOrderTransactionData.getAmount())
				.bind("want_asset_id", createOrderTransactionData.getWantAssetId()).bind("price", createOrderTransactionData.getPrice());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save create order transaction into repository", e);
		}
	}

}
