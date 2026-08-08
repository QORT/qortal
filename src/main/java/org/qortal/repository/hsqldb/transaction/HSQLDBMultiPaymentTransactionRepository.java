package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.PaymentData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.MultiPaymentTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class HSQLDBMultiPaymentTransactionRepository extends HSQLDBTransactionRepository {

	public HSQLDBMultiPaymentTransactionRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
		String sql = "SELECT TRUE from MultiPaymentTransactions WHERE signature = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
			if (resultSet == null)
				return null;

			List<PaymentData> payments = this.getPaymentsFromSignature(baseTransactionData.getSignature());

			return new MultiPaymentTransactionData(baseTransactionData, payments);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch multi-payment transaction from repository", e);
		}
	}

	@Override
	public void save(TransactionData transactionData) throws DataException {
		MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

		HSQLDBSaver saveHelper = new HSQLDBSaver("MultiPaymentTransactions");

		saveHelper.bind("signature", multiPaymentTransactionData.getSignature()).bind("sender", multiPaymentTransactionData.getSenderPublicKey());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save multi-payment transaction into repository", e);
		}

		// Save payments. If this fails then it is the caller's responsibility to catch the DataException as the underlying transaction will have been lost.
		this.savePayments(transactionData.getSignature(), multiPaymentTransactionData.getPayments());
	}

}
