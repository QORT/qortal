package org.qortal.test;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AccountUtils;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.transaction.Transaction.TransactionType;

public class TransactionSearchTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testFindingSpecificTransactionsWithinHeight() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");

			// Block 2
			BlockUtils.mintBlock(repository);

			// Block 3
			AccountUtils.pay(repository, alice, chloe.getAddress(), 1234L);

			// Block 4
			AccountUtils.pay(repository, chloe, alice.getAddress(), 5678L);

			// Block 5
			BlockUtils.mintBlock(repository);

			List<byte[]> signatures;

			// No transactions with this type
			signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(TransactionType.GROUP_KICK, null, null, null);
			assertEquals(0, signatures.size());

			// 2 payments
			signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(TransactionType.PAYMENT, null, null, null);
			assertEquals(2, signatures.size());

			// 1 payment by Alice
			signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(TransactionType.PAYMENT, alice.getPublicKey(), null, null);
			assertEquals(1, signatures.size());

			// 1 transaction by Chloe
			signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, chloe.getPublicKey(), null, null);
			assertEquals(1, signatures.size());

			// 1 transaction from blocks 4 onwards
			signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, null, 4, null);
			assertEquals(1, signatures.size());

			// 1 transaction from blocks 2 to 3
			signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, null, 2, 3);
			assertEquals(1, signatures.size());

			// No transaction of this type from blocks 2 to 5
			signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(TransactionType.ISSUE_ASSET, null, 2, 5);
			assertEquals(0, signatures.size());
		}

	}

}
