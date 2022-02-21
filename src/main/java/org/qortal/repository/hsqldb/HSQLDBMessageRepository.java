package org.qortal.repository.hsqldb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.MessageRepository;
import org.qortal.transaction.Transaction.TransactionType;

public class HSQLDBMessageRepository implements MessageRepository {

	protected HSQLDBRepository repository;

	public HSQLDBMessageRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<MessageTransactionData> getMessagesByParticipants(byte[] senderPublicKey,
			String recipient, Integer limit, Integer offset, Boolean reverse) throws DataException {
		if (senderPublicKey == null && recipient == null)
			throw new DataException("At least one of senderPublicKey or recipient required to fetch matching messages");

		StringBuilder sql = new StringBuilder(1024);
		sql.append("SELECT signature from MessageTransactions "
				+ "JOIN Transactions USING (signature) "
				+ "JOIN BlockTransactions ON transaction_signature = signature "
				+ "WHERE ");

		List<String> whereClauses = new ArrayList<>();
		List<Object> bindParams = new ArrayList<>();

		if (senderPublicKey != null) {
			whereClauses.add("sender = ?");
			bindParams.add(senderPublicKey);
		}

		if (recipient != null) {
			whereClauses.add("recipient = ?");
			bindParams.add(recipient);
		}

		sql.append(String.join(" AND ", whereClauses));

		sql.append("ORDER BY Transactions.created_when");
		sql.append((reverse == null || !reverse) ? " ASC" : " DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<MessageTransactionData> messageTransactionsData = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), bindParams.toArray())) {
			if (resultSet == null)
				return messageTransactionsData;

			do {
				byte[] signature = resultSet.getBytes(1);

				TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(signature);
				if (transactionData == null || transactionData.getType() != TransactionType.MESSAGE)
					throw new DataException("Inconsistent data from repository when fetching message");

				messageTransactionsData.add((MessageTransactionData) transactionData);
			} while (resultSet.next());

			return messageTransactionsData;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch matching messages from repository", e);
		}
	}

	@Override
	public boolean exists(byte[] senderPublicKey, String recipient, byte[] messageData) throws DataException {
		try {
			return this.repository.exists("MessageTransactions", "sender = ? AND recipient = ? AND data = ?", senderPublicKey, recipient, messageData);
		} catch (SQLException e) {
			throw new DataException("Unable to check for existing message in repository", e);
		}
	}

}
