package org.qortal.test.common.transaction;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.VoteOnPollTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Random;

public class VoteOnPollTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		String pollName = "test poll " + random.nextInt(1_000_000);
		final int optionIndex = random.nextInt(3);

		return new VoteOnPollTransactionData(generateBase(account), pollName, optionIndex);
	}

}
