package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.TransferPrivsTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBTransferPrivsTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBTransferPrivsTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT recipient, previous_sender_flags, previous_recipient_flags, previous_sender_blocks_minted_adjustment, previous_sender_blocks_minted FROM TransferPrivsTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			String recipient = resultSet.getString(1);

			Integer previousSenderFlags = resultSet.getInt(2);
			if (previousSenderFlags == 0 && resultSet.wasNull())
				previousSenderFlags = null;

			Integer previousRecipientFlags = resultSet.getInt(3);
			if (previousRecipientFlags == 0 && resultSet.wasNull())
				previousRecipientFlags = null;

			Integer previousSenderBlocksMintedAdjustment = resultSet.getInt(4);
			if (previousSenderBlocksMintedAdjustment == 0 && resultSet.wasNull())
				previousSenderBlocksMintedAdjustment = null;

			Integer previousSenderBlocksMinted = resultSet.getInt(5);
			if (previousSenderBlocksMinted == 0 && resultSet.wasNull())
				previousSenderBlocksMinted = null;

			return new TransferPrivsTransactionData(baseTransactionData, recipient, previousSenderFlags, previousRecipientFlags, previousSenderBlocksMintedAdjustment, previousSenderBlocksMinted);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch transfer privs transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		TransferPrivsTransactionData transferPrivsTransactionData = (TransferPrivsTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("TransferPrivsTransactions");
		saveHelper.bind("signature", transferPrivsTransactionData.getSignature()).bind("sender", transferPrivsTransactionData.getSenderPublicKey())
				.bind("recipient", transferPrivsTransactionData.getRecipient())
				.bind("previous_sender_flags", transferPrivsTransactionData.getPreviousSenderFlags())
				.bind("previous_recipient_flags", transferPrivsTransactionData.getPreviousRecipientFlags())
				.bind("previous_sender_blocks_minted_adjustment", transferPrivsTransactionData.getPreviousSenderBlocksMintedAdjustment())
				.bind("previous_sender_blocks_minted", transferPrivsTransactionData.getPreviousSenderBlocksMinted());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save transfer privs transaction into repository", e);
		}
	}

}
