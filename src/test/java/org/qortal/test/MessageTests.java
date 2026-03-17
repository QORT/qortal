package org.qortal.test;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.BlockChain;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.group.Group.ApprovalThreshold;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.*;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.MessageTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.MessageTransactionTransformer;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.NTP;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class MessageTests extends Common {

	private static final int version = 4;
	private static final String recipient = Common.getTestAccount(null, "bob").getAddress();


	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void validityTests() throws DataException {
		// with recipient, with amount
		assertTrue(isValid(Group.NO_GROUP, recipient, 123L, Asset.QORT));

		// with recipient, no amount
		assertTrue(isValid(Group.NO_GROUP, recipient, 0L, null));

		// no recipient (message to group), no amount
		assertTrue(isValid(Group.NO_GROUP, null, 0L, null));

		// can't have amount if no recipient!
		assertFalse(isValid(Group.NO_GROUP, null, 123L, Asset.QORT));

		// Alice is part of group 1
		assertTrue(isValid(1, null, 0L, null));

		int newGroupId;
		try (final Repository repository = RepositoryManager.getRepository()) {
			newGroupId = GroupUtils.createGroup(repository, "chloe", "non-alice-group", false, ApprovalThreshold.ONE, 10, 1440);
		}

		// Alice is not part of new group
		assertFalse(isValid(newGroupId, null, 0L, null));
	}

	@Test
	public void referenceTests() throws DataException {
		Random random = new Random();

		byte[] randomPrivateKey = new byte[32];
		random.nextBytes(randomPrivateKey);

		byte[] randomReference = new byte[64];
		random.nextBytes(randomReference);

		long minimumFee = BlockChain.getInstance().getUnitFeeAtTimestamp(System.currentTimeMillis());

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			PrivateKeyAccount newbie = new PrivateKeyAccount(repository, randomPrivateKey);

			byte[] aliceReference = alice.getLastReference();

			// real account, correct reference, real fee: OK
			assertTrue(hasValidReference(repository, alice, aliceReference, minimumFee));
			// real account, random reference, real fee: INVALID
			assertFalse(hasValidReference(repository, alice, randomReference, minimumFee));
			// real account, correct reference, zero fee: OK
			assertTrue(hasValidReference(repository, alice, aliceReference, 0));
			// real account, random reference, zero fee: OK
			assertTrue(hasValidReference(repository, alice, randomReference, 0));

			// new account, null reference, real fee: INVALID: new accounts don't have a reference!
			assertFalse(hasValidReference(repository, newbie, null, minimumFee));
			// new account, wrong reference, real fee: INVALID: new accounts don't have a reference!
			assertFalse(hasValidReference(repository, newbie, randomReference, minimumFee));
			// new account, null reference, zero fee: INVALID
			assertFalse(hasValidReference(repository, newbie, null, 0));
			// new account, random reference, zero fee: OK
			assertTrue(hasValidReference(repository, newbie, randomReference, 0));
		}
	}

	@Test
	public void noFeeNoNonce() throws DataException {
		testFeeNonce(false, false, false);
	}

	@Test
	public void withFeeNoNonce() throws DataException {
		testFeeNonce(true, false, true);
	}

	@Test
	public void noFeeWithNonce() throws DataException {
		testFeeNonce(false, true, true);
	}

	@Test
	public void withFeeWithNonce() throws DataException {
		testFeeNonce(true, true, true);
	}

	@Test
	public void withRecipentNoAmount() throws DataException {
		testMessage(Group.NO_GROUP, recipient, 0L, null);
	}

	@Test
	public void withRecipientWithAmount() throws DataException {
		testMessage(Group.NO_GROUP, recipient, 123L, Asset.QORT);
	}

	@Test
	public void noRecipentNoAmount() throws DataException {
		testMessage(Group.NO_GROUP, null, 0L, null);
	}

	@Test
	public void noRecipentNoAmountWithGroup() throws DataException {
		testMessage(1, null, 0L, null);
	}

	@Test
	public void atRecipientNoFeeWithNonce() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String atRecipient = deployAt();
			MessageTransaction transaction = testFeeNonce(repository, false, true, atRecipient, true);

			// Transaction should be confirmable because it's to an AT, and therefore should be present in a block
			assertTrue(transaction.isConfirmable());
			TransactionUtils.signAndMint(repository, transaction.getTransactionData(), alice);
			assertTrue(isTransactionConfirmed(repository, transaction));
			assertEquals(16, transaction.getPoWDifficulty());

			BlockUtils.orphanLastBlock(repository);
		}
	}

	@Test
	public void regularRecipientNoFeeWithNonce() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");

			// Transaction should not be present in db yet
			List<MessageTransactionData> messageTransactionsData = repository.getMessageRepository().getMessagesByParticipants(null, recipient, null, null, null);
			assertTrue(messageTransactionsData.isEmpty());

			MessageTransaction transaction = testFeeNonce(repository, false, true, recipient, true);

			// Transaction shouldn't be confirmable because it's not to an AT, and therefore shouldn't be present in a block
			assertFalse(transaction.isConfirmable());
			TransactionUtils.signAndMint(repository, transaction.getTransactionData(), alice);
			assertFalse(isTransactionConfirmed(repository, transaction));
			assertEquals(12, transaction.getPoWDifficulty());

			// Transaction should be found when trade bot searches for it
			messageTransactionsData = repository.getMessageRepository().getMessagesByParticipants(null, recipient, null, null, null);
			assertEquals(1, messageTransactionsData.size());

			BlockUtils.orphanLastBlock(repository);
		}
	}

	@Test
	public void noRecipientNoFeeWithNonce() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");

			MessageTransaction transaction = testFeeNonce(repository, false, true, null, true);

			// Transaction shouldn't be confirmable because it's not to an AT, and therefore shouldn't be present in a block
			assertFalse(transaction.isConfirmable());
			TransactionUtils.signAndMint(repository, transaction.getTransactionData(), alice);
			assertFalse(isTransactionConfirmed(repository, transaction));
			assertEquals(12, transaction.getPoWDifficulty());

			BlockUtils.orphanLastBlock(repository);
		}
	}

	@Test
	public void atRecipientWithFeeNoNonce() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String atRecipient = deployAt();
			MessageTransaction transaction = testFeeNonce(repository, true, false, atRecipient, true);

			// Transaction should be confirmable because it's to an AT, and therefore should be present in a block
			assertTrue(transaction.isConfirmable());
			TransactionUtils.signAndMint(repository, transaction.getTransactionData(), alice);
			assertTrue(isTransactionConfirmed(repository, transaction));
			assertEquals(16, transaction.getPoWDifficulty());

			BlockUtils.orphanLastBlock(repository);
		}
	}

	@Test
	public void regularRecipientWithFeeNoNonce() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");

			MessageTransaction transaction = testFeeNonce(repository, true, false, recipient, true);

			// Transaction shouldn't be confirmable because it's not to an AT, and therefore shouldn't be present in a block
			assertFalse(transaction.isConfirmable());
			TransactionUtils.signAndMint(repository, transaction.getTransactionData(), alice);
			assertFalse(isTransactionConfirmed(repository, transaction));
			assertEquals(12, transaction.getPoWDifficulty());

			BlockUtils.orphanLastBlock(repository);
		}
	}

	@Test
	public void atRecipientNoFeeWithNonceLegacyDifficulty() throws DataException, IllegalAccessException {
		try (final Repository repository = RepositoryManager.getRepository()) {

			// Set mempowTransactionUpdatesTimestamp to a high value, so that it hasn't activated key
			FieldUtils.writeField(BlockChain.getInstance(), "mempowTransactionUpdatesTimestamp", Long.MAX_VALUE, true);

			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String atRecipient = deployAt();
			MessageTransaction transaction = testFeeNonce(repository, false, true, atRecipient, true);

			// Transaction should be confirmable because all MESSAGE transactions confirmed prior to the feature trigger
			assertTrue(transaction.isConfirmable());
			TransactionUtils.signAndMint(repository, transaction.getTransactionData(), alice);
			assertTrue(isTransactionConfirmed(repository, transaction));
			assertEquals(14, transaction.getPoWDifficulty()); // Legacy difficulty was 14 in all cases

			BlockUtils.orphanLastBlock(repository);
		}
	}

	@Test
	public void regularRecipientNoFeeWithNonceLegacyDifficulty() throws DataException, IllegalAccessException {
		try (final Repository repository = RepositoryManager.getRepository()) {

			// Set mempowTransactionUpdatesTimestamp to a high value, so that it hasn't activated key
			FieldUtils.writeField(BlockChain.getInstance(), "mempowTransactionUpdatesTimestamp", Long.MAX_VALUE, true);

			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			MessageTransaction transaction = testFeeNonce(repository, false, true, recipient, true);

			// Transaction should be confirmable because all MESSAGE transactions confirmed prior to the feature trigger
			assertTrue(transaction.isConfirmable());
			TransactionUtils.signAndMint(repository, transaction.getTransactionData(), alice);
			assertTrue(isTransactionConfirmed(repository, transaction)); // All MESSAGE transactions would confirm before feature trigger
			assertEquals(14, transaction.getPoWDifficulty()); // Legacy difficulty was 14 in all cases

			BlockUtils.orphanLastBlock(repository);
		}
	}

	@Test
	public void serializationTests() throws DataException, TransformationException {
		// with recipient, with amount
		testSerialization(recipient, 123L, Asset.QORT);

		// with recipient, no amount
		testSerialization(recipient, 0L, null);

		// no recipient (message to group), no amount
		testSerialization(null, 0L, null);
	}

	private String deployAt() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");
			byte[] creationBytes = AtUtils.buildSimpleAT();
			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);

			String address = deployAtTransaction.getATAccount().getAddress();
			assertNotNull(address);
			return address;
		}
	}

	private boolean isTransactionConfirmed(Repository repository, MessageTransaction transaction) throws DataException {
		TransactionData queriedTransactionData = repository.getTransactionRepository().fromSignature(transaction.getTransactionData().getSignature());
		return queriedTransactionData.getBlockHeight() != null && queriedTransactionData.getBlockHeight() > 0;
	}

	private boolean isValid(int txGroupId, String recipient, long amount, Long assetId) throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			TestAccount alice = Common.getTestAccount(repository, "alice");

			int nonce = 0;
			byte[] data = new byte[1];
			boolean isText = false;
			boolean isEncrypted = false;

			MessageTransactionData transactionData = new MessageTransactionData(TestTransaction.generateBase(alice, txGroupId),
					version, nonce, recipient, amount, assetId, data, isText, isEncrypted);

			Transaction transaction = new MessageTransaction(repository, transactionData);

			return transaction.isValidUnconfirmed() == ValidationResult.OK;
		}
	}

	private boolean hasValidReference(Repository repository, PrivateKeyAccount sender, byte[] reference, long fee) throws DataException {
		long timestamp = NTP.getTime();
		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, reference, sender.getPublicKey(), fee, null);
		int version = 4;
		byte[] data = "test".getBytes();
		boolean isText = true;
		boolean isEncrypted = false;
		MessageTransactionData messageTransactionData = new MessageTransactionData(baseTransactionData, version, 0, recipient, 0, null, data, isText, isEncrypted);
		MessageTransaction messageTransaction = new MessageTransaction(repository, messageTransactionData);
		return messageTransaction.hasValidReference();
	}


	private MessageTransaction testFeeNonce(boolean withFee, boolean withNonce, boolean isValid) throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return testFeeNonce(repository, withFee, withNonce, recipient, isValid);
		}
	}

	private MessageTransaction testFeeNonce(Repository repository, boolean withFee, boolean withNonce, String recipient, boolean isValid) throws DataException {
		TestAccount alice = Common.getTestAccount(repository, "alice");

		int txGroupId = 0;
		int nonce = 0;
		long amount = 0;
		long assetId = Asset.QORT;
		byte[] data = new byte[1];
		boolean isText = false;
		boolean isEncrypted = false;

		MessageTransactionData transactionData = new MessageTransactionData(TestTransaction.generateBase(alice, txGroupId),
				version, nonce, recipient, amount, assetId, data, isText, isEncrypted);

		MessageTransaction transaction = new MessageTransaction(repository, transactionData);

		if (withFee)
			transactionData.setFee(transaction.calcRecommendedFee());
		else
			transactionData.setFee(0L);

		if (withNonce) {
			transaction.computeNonce();
		} else {
			transactionData.setNonce(-1);
		}

		transaction.sign(alice);

		assertEquals(isValid, transaction.isSignatureValid());

		return transaction;
	}

	private MessageTransaction testMessage(int txGroupId, String recipient, long amount, Long assetId) throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			TestAccount alice = Common.getTestAccount(repository, "alice");

			int nonce = 0;
			byte[] data = new byte[1];
			boolean isText = false;
			boolean isEncrypted = false;

			MessageTransactionData transactionData = new MessageTransactionData(TestTransaction.generateBase(alice, txGroupId),
					version, nonce, recipient, amount, assetId, data, isText, isEncrypted);

			TransactionUtils.signAndMint(repository, transactionData, alice);

			BlockUtils.orphanLastBlock(repository);

			return new MessageTransaction(repository, transactionData);
		}
	}

	private void testSerialization(String recipient, long amount, Long assetId) throws DataException, TransformationException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			TestAccount alice = Common.getTestAccount(repository, "alice");

			int nonce = 0;
			byte[] data = new byte[1];
			boolean isText = false;
			boolean isEncrypted = false;

			MessageTransactionData expectedTransactionData = new MessageTransactionData(TestTransaction.generateBase(alice),
					version, nonce, recipient, amount, assetId, data, isText, isEncrypted);

			Transaction transaction = new MessageTransaction(repository, expectedTransactionData);
			transaction.sign(alice);

			MessageTransactionTransformer.getDataLength(expectedTransactionData);
			byte[] transactionBytes = MessageTransactionTransformer.toBytes(expectedTransactionData);

			TransactionData transactionData = TransactionTransformer.fromBytes(transactionBytes);
			assertEquals(TransactionType.MESSAGE, transactionData.getType());

			MessageTransactionData actualTransactionData = (MessageTransactionData) transactionData;

			assertEquals(expectedTransactionData.getRecipient(), actualTransactionData.getRecipient());
			assertEquals(expectedTransactionData.getAmount(), actualTransactionData.getAmount());
			assertEquals(expectedTransactionData.getAssetId(), actualTransactionData.getAssetId());
		}
	}

}
