package org.qortal.test.common.transaction;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.transaction.CreatePollTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CreatePollTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		String owner = account.getAddress();
		String pollName = "test poll " + random.nextInt(1_000_000);
		String description = "Not ready reading drive A";

		List<PollOptionData> pollOptions = new ArrayList<>();
		pollOptions.add(new PollOptionData("Abort"));
		pollOptions.add(new PollOptionData("Retry"));
		pollOptions.add(new PollOptionData("Fail"));

		return new CreatePollTransactionData(generateBase(account), owner, pollName, description, pollOptions);
	}

}
