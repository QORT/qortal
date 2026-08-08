package org.qortal.test.minting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.account.RewardShareData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AccountUtils;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.utils.Base58;

import static org.junit.Assert.*;

public class RewardShareTests extends Common {

	private static final int CANCEL_SHARE_PERCENT = -1;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testCreateRewardShare() throws DataException {
		final int sharePercent = 12_80; // 12.80%

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceAccount = Common.getTestAccount(repository, "alice");
			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");

			// Create reward-share
			byte[] rewardSharePrivateKey = AccountUtils.rewardShare(repository, "alice", "bob", sharePercent);
			PrivateKeyAccount rewardShareAccount = new PrivateKeyAccount(repository, rewardSharePrivateKey);

			// Confirm reward-share info set correctly

			// Fetch using reward-share public key
			RewardShareData rewardShareData = repository.getAccountRepository().getRewardShare(rewardShareAccount.getPublicKey());
			assertEquals("Incorrect minter public key", Base58.encode(aliceAccount.getPublicKey()), Base58.encode(rewardShareData.getMinterPublicKey()));
			assertEquals("Incorrect recipient", bobAccount.getAddress(), rewardShareData.getRecipient());
			assertEquals("Incorrect share percentage", sharePercent, rewardShareData.getSharePercent());

			// Fetch using minter public key and recipient address combination
			rewardShareData = repository.getAccountRepository().getRewardShare(aliceAccount.getPublicKey(), bobAccount.getAddress());
			assertEquals("Incorrect minter public key", Base58.encode(aliceAccount.getPublicKey()), Base58.encode(rewardShareData.getMinterPublicKey()));
			assertEquals("Incorrect recipient", bobAccount.getAddress(), rewardShareData.getRecipient());
			assertEquals("Incorrect share percentage", sharePercent, rewardShareData.getSharePercent());

			// Delete reward-share
			byte[] newRewardSharePrivateKey = AccountUtils.rewardShare(repository, "alice", "bob", CANCEL_SHARE_PERCENT);
			PrivateKeyAccount newRewardShareAccount = new PrivateKeyAccount(repository, newRewardSharePrivateKey);

			// Confirm reward-share keys match
			assertEquals("Reward-share private keys differ", Base58.encode(rewardSharePrivateKey), Base58.encode(newRewardSharePrivateKey));
			assertEquals("Reward-share public keys differ", Base58.encode(rewardShareAccount.getPublicKey()), Base58.encode(newRewardShareAccount.getPublicKey()));

			// Confirm reward-share no longer exists in repository

			// Fetch using reward-share public key
			RewardShareData newRewardShareData = repository.getAccountRepository().getRewardShare(rewardShareAccount.getPublicKey());
			assertNull("Reward-share shouldn't exist", newRewardShareData);

			// Fetch using minter public key and recipient address combination
			newRewardShareData = repository.getAccountRepository().getRewardShare(aliceAccount.getPublicKey(), bobAccount.getAddress());
			assertNull("Reward-share shouldn't exist", newRewardShareData);

			// Orphan last block to restore prior reward-share
			BlockUtils.orphanLastBlock(repository);

			// Confirm reward-share restored correctly

			// Fetch using reward-share public key
			newRewardShareData = repository.getAccountRepository().getRewardShare(rewardShareAccount.getPublicKey());
			assertNotNull("Reward-share should have been restored", newRewardShareData);
			assertEquals("Incorrect minter public key", Base58.encode(aliceAccount.getPublicKey()), Base58.encode(newRewardShareData.getMinterPublicKey()));
			assertEquals("Incorrect recipient", bobAccount.getAddress(), newRewardShareData.getRecipient());
			assertEquals("Incorrect share percentage", sharePercent, newRewardShareData.getSharePercent());

			// Fetch using minter public key and recipient address combination
			newRewardShareData = repository.getAccountRepository().getRewardShare(aliceAccount.getPublicKey(), bobAccount.getAddress());
			assertNotNull("Reward-share should have been restored", newRewardShareData);
			assertEquals("Incorrect minter public key", Base58.encode(aliceAccount.getPublicKey()), Base58.encode(newRewardShareData.getMinterPublicKey()));
			assertEquals("Incorrect recipient", bobAccount.getAddress(), newRewardShareData.getRecipient());
			assertEquals("Incorrect share percentage", sharePercent, newRewardShareData.getSharePercent());

			// Orphan another block to remove initial reward-share
			BlockUtils.orphanLastBlock(repository);

			// Confirm reward-share no longer exists

			// Fetch using reward-share public key
			newRewardShareData = repository.getAccountRepository().getRewardShare(rewardShareAccount.getPublicKey());
			assertNull("Reward-share shouldn't exist", newRewardShareData);

			// Fetch using minter public key and recipient address combination
			newRewardShareData = repository.getAccountRepository().getRewardShare(aliceAccount.getPublicKey(), bobAccount.getAddress());
			assertNull("Reward-share shouldn't exist", newRewardShareData);
		}
	}

	@Test
	public void testNegativeInitialShareInvalid() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Create invalid REWARD_SHARE transaction with initial negative reward share
			TransactionData transactionData = AccountUtils.createRewardShare(repository, "alice", "bob", CANCEL_SHARE_PERCENT);

			// Confirm transaction is invalid
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult validationResult = transaction.isValidUnconfirmed();
			assertNotSame("Creating reward-share with 'cancel' share-percent should be invalid", ValidationResult.OK, validationResult);
		}
	}

	@Test
	public void testSelfShare() throws DataException {
		final String testAccountName = "dilbert";

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount signingAccount = Common.getTestAccount(repository, testAccountName);
			// byte[] rewardSharePrivateKey = aliceAccount.getRewardSharePrivateKey(aliceAccount.getPublicKey());
			// PrivateKeyAccount rewardShareAccount = new PrivateKeyAccount(repository, rewardSharePrivateKey);

			// Create self-reward-share
			TransactionData transactionData = AccountUtils.createRewardShare(repository, testAccountName, testAccountName, 100_00);
			Transaction transaction = Transaction.fromData(repository, transactionData);

			// Confirm self-share is valid
			ValidationResult validationResult = transaction.isValidUnconfirmed();
			assertEquals("Initial self-share should be valid", ValidationResult.OK, validationResult);

			// Check zero fee is valid
			transactionData.setFee(0L);
			validationResult = transaction.isValidUnconfirmed();
			assertEquals("Zero-fee self-share should be valid", ValidationResult.OK, validationResult);

			TransactionUtils.signAndMint(repository, transactionData, signingAccount);

			// Subsequent non-terminating (0% share) self-reward-share should be invalid
			TransactionData newTransactionData = AccountUtils.createRewardShare(repository, testAccountName, testAccountName, 99_00);
			Transaction newTransaction = Transaction.fromData(repository, newTransactionData);

			// Confirm subsequent self-reward-share is actually invalid
			validationResult = newTransaction.isValidUnconfirmed();
			assertNotSame("Subsequent self-share should be invalid", ValidationResult.OK, validationResult);

			// Recheck with zero fee
			newTransactionData.setFee(0L);
			validationResult = newTransaction.isValidUnconfirmed();
			assertNotSame("Subsequent zero-fee self-share should be invalid", ValidationResult.OK, validationResult);

			// Subsequent terminating (negative share) self-reward-share should be OK
			newTransactionData = AccountUtils.createRewardShare(repository, testAccountName, testAccountName, CANCEL_SHARE_PERCENT);
			newTransaction = Transaction.fromData(repository, newTransactionData);

			// Confirm terminating reward-share with fee is valid
			validationResult = newTransaction.isValidUnconfirmed();
			assertEquals("Subsequent self-share cancel should be valid", ValidationResult.OK, validationResult);

			// Confirm terminating reward-share with zero fee is invalid
			newTransactionData.setFee(0L);
			validationResult = newTransaction.isValidUnconfirmed();
			assertNotSame("Subsequent zero-fee self-share cancel should be invalid", ValidationResult.OK, validationResult);
		}
	}

	@Test
	public void testCreateRewardSharesBeforeReduction() throws DataException {
		final int sharePercent = 0;

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount dilbertAccount = Common.getTestAccount(repository, "dilbert");

			// Create 6 reward shares
			for (int i=0; i<6; i++) {
				AccountUtils.rewardShare(repository, dilbertAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			}

			// 7th reward share should fail because we've reached the limit (and we're not yet requiring a self share)
			AssertionError assertionError = null;
			try {
				AccountUtils.rewardShare(repository, dilbertAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			} catch (AssertionError e) {
				assertionError = e;
			}
			assertNotNull("Transaction should be invalid", assertionError);
			assertTrue("Transaction should be invalid due to reaching maximum reward shares", assertionError.getMessage().contains("MAXIMUM_REWARD_SHARES"));
		}
	}

	@Test
	public void testCreateRewardSharesAfterReduction() throws DataException {
		Common.useSettings("test-settings-v2-reward-shares.json");

		final int sharePercent = 0;

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount dilbertAccount = Common.getTestAccount(repository, "dilbert");

			// Create 2 reward shares
			for (int i=0; i<2; i++) {
				AccountUtils.rewardShare(repository, dilbertAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			}

			// 3rd reward share should fail because we've reached the limit (and we haven't got a self share)
			AssertionError assertionError = null;
			try {
				AccountUtils.rewardShare(repository, dilbertAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			} catch (AssertionError e) {
				assertionError = e;
			}
			assertNotNull("Transaction should be invalid", assertionError);
			assertTrue("Transaction should be invalid due to reaching maximum reward shares", assertionError.getMessage().contains("MAXIMUM_REWARD_SHARES"));
		}
	}

	@Test
	public void testCreateSelfAndRewardSharesAfterReduction() throws DataException {
		Common.useSettings("test-settings-v2-reward-shares.json");

		final int sharePercent = 0;

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount dilbertAccount = Common.getTestAccount(repository, "dilbert");

			// Create 2 reward shares
			for (int i=0; i<2; i++) {
				AccountUtils.rewardShare(repository, dilbertAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			}

			// 3rd reward share should fail because we've reached the limit (and we haven't got a self share)
			AssertionError assertionError = null;
			try {
				AccountUtils.rewardShare(repository, dilbertAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			} catch (AssertionError e) {
				assertionError = e;
			}
			assertNotNull("Transaction should be invalid", assertionError);
			assertTrue("Transaction should be invalid due to reaching maximum reward shares", assertionError.getMessage().contains("MAXIMUM_REWARD_SHARES"));

			// Now create a self share, which should succeed as we have space for it
			AccountUtils.rewardShare(repository, dilbertAccount, dilbertAccount, sharePercent);

			// 4th reward share should fail because we've reached the limit (including the self share)
			assertionError = null;
			try {
				AccountUtils.rewardShare(repository, dilbertAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			} catch (AssertionError e) {
				assertionError = e;
			}
			assertNotNull("Transaction should be invalid", assertionError);
			assertTrue("Transaction should be invalid due to reaching maximum reward shares", assertionError.getMessage().contains("MAXIMUM_REWARD_SHARES"));
		}
	}

	@Test
	public void testCreateFounderRewardSharesBeforeReduction() throws DataException {
		final int sharePercent = 0;

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceFounderAccount = Common.getTestAccount(repository, "alice");

			// Create 5 reward shares (not 6, because alice already starts with a self reward share in the genesis block)
			for (int i=0; i<5; i++) {
				AccountUtils.rewardShare(repository, aliceFounderAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			}

			// 6th reward share should fail
			AssertionError assertionError = null;
			try {
				AccountUtils.rewardShare(repository, aliceFounderAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			} catch (AssertionError e) {
				assertionError = e;
			}
			assertNotNull("Transaction should be invalid", assertionError);
			assertTrue("Transaction should be invalid due to reaching maximum reward shares", assertionError.getMessage().contains("MAXIMUM_REWARD_SHARES"));
		}
	}

	@Test
	public void testCreateFounderRewardSharesAfterReduction() throws DataException {
		Common.useSettings("test-settings-v2-reward-shares.json");

		final int sharePercent = 0;

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceFounderAccount = Common.getTestAccount(repository, "alice");

			// Create 5 reward shares (not 6, because alice already starts with a self reward share in the genesis block)
			for (int i=0; i<5; i++) {
				AccountUtils.rewardShare(repository, aliceFounderAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			}

			// 6th reward share should fail
			AssertionError assertionError = null;
			try {
				AccountUtils.rewardShare(repository, aliceFounderAccount, Common.generateRandomSeedAccount(repository), sharePercent);
			} catch (AssertionError e) {
				assertionError = e;
			}
			assertNotNull("Transaction should be invalid", assertionError);
			assertTrue("Transaction should be invalid due to reaching maximum reward shares", assertionError.getMessage().contains("MAXIMUM_REWARD_SHARES"));
		}
	}

}
