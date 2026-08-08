package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.AddGroupAdminTransactionData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBAddGroupAdminTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBAddGroupAdminTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT group_id, address FROM AddGroupAdminTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			int groupId = resultSet.getInt(1);
			String member = resultSet.getString(2);

			return new AddGroupAdminTransactionData(baseTransactionData, groupId, member);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch add group admin transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		AddGroupAdminTransactionData addGroupAdminTransactionData = (AddGroupAdminTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("AddGroupAdminTransactions");

		saveHelper.bind("signature", addGroupAdminTransactionData.getSignature()).bind("owner", addGroupAdminTransactionData.getOwnerPublicKey())
				.bind("group_id", addGroupAdminTransactionData.getGroupId()).bind("address", addGroupAdminTransactionData.getMember());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save add group admin transaction into repository", e);
		}
	}

}
