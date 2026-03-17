package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.VoteOnPollTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBVoteOnPollTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBVoteOnPollTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT poll_name, option_index, previous_option_index FROM VoteOnPollTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String pollName = resultSet.getString(1);
			int optionIndex = resultSet.getInt(2);

			// Special null-checking for previous option index
			Integer previousOptionIndex = resultSet.getInt(3);
			if (previousOptionIndex == 0 && resultSet.wasNull())
				previousOptionIndex = null;

			return new VoteOnPollTransactionData(baseTransactionData, pollName, optionIndex, previousOptionIndex);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch vote on poll transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		VoteOnPollTransactionData voteOnPollTransactionData = (VoteOnPollTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("VoteOnPollTransactions");

		saveHelper.bind("signature", voteOnPollTransactionData.getSignature()).bind("poll_name", voteOnPollTransactionData.getPollName())
				.bind("voter", voteOnPollTransactionData.getVoterPublicKey()).bind("option_index", voteOnPollTransactionData.getOptionIndex())
				.bind("previous_option_index", voteOnPollTransactionData.getPreviousOptionIndex());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save vote on poll transaction into repository", e);
		}
	}

}
