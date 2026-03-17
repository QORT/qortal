package org.qortal.test.common.transaction;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.transaction.CancelAssetOrderTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Random;

public class CancelAssetOrderTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();
		byte[] orderId = new byte[64];
		random.nextBytes(orderId);

		return new CancelAssetOrderTransactionData(generateBase(account), orderId);
	}

}
