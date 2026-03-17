package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.AccountLevelTransactionData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBAccountLevelTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBAccountLevelTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT target, level FROM AccountLevelTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String target = resultSet.getString(1);
			int level = resultSet.getInt(2);

			return new AccountLevelTransactionData(baseTransactionData, target, level);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account level transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		AccountLevelTransactionData accountLevelTransactionData = (AccountLevelTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("AccountLevelTransactions");

		saveHelper.bind("signature", accountLevelTransactionData.getSignature()).bind("creator", accountLevelTransactionData.getCreatorPublicKey())
				.bind("target", accountLevelTransactionData.getTarget()).bind("level", accountLevelTransactionData.getLevel());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save account level transaction into repository", e);
		}
	}

}
