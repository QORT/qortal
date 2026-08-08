package org.qortal.test.common.transaction;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.transaction.GroupApprovalTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Random;

public class GroupApprovalTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		byte[] pendingSignature = new byte[64];
		random.nextBytes(pendingSignature);
		final boolean approval = true;

		return new GroupApprovalTransactionData(generateBase(account), pendingSignature, approval);
	}

}
