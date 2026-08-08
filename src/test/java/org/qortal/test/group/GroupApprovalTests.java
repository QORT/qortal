package org.qortal.test.group;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.IssueAssetTransactionData;
import org.qortal.data.transaction.PaymentTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.group.Group.ApprovalThreshold;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.GroupUtils;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.ApprovalStatus;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.utils.Amounts;

import java.util.Arrays;

import static org.junit.Assert.*;

public class GroupApprovalTests extends Common {

	private static final long amount = 5000L * Amounts.MULTIPLIER;
	private static final long fee = 1L * Amounts.MULTIPLIER;
	private static final int minBlockDelay = 5;
	private static final int maxBlockDelay = 10;


	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	/** Check that a transaction type that doesn't need approval doesn't accept txGroupId apart from NO_GROUP */
	public void testNonApprovalTxGroupId() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = buildPaymentTransaction(repository, "alice", "bob", amount, Group.NO_GROUP);
			assertEquals(ValidationResult.OK, transaction.isValidUnconfirmed());

			int groupId = GroupUtils.createGroup(repository, "alice", "test", true, ApprovalThreshold.NONE, 0, 10);

			transaction = buildPaymentTransaction(repository, "alice", "bob", amount, groupId);
			assertEquals(ValidationResult.INVALID_TX_GROUP_ID, transaction.isValidUnconfirmed());
		}
	}

	@Test
	/** Check that a transaction type that does need approval, auto-approves if created by group admin */
	public void testAutoApprove() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceAccount = Common.getTestAccount(repository, "alice");

			int groupId = GroupUtils.createGroup(repository, "alice", "test", true, ApprovalThreshold.ONE, minBlockDelay, maxBlockDelay);

			Transaction transaction = buildIssueAssetTransaction(repository, "alice", groupId);
			TransactionUtils.signAndMint(repository, transaction.getTransactionData(), aliceAccount);

			// Confirm transaction doesn't need approval
			ApprovalStatus approvalStatus = GroupUtils.getApprovalStatus(repository, transaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.NOT_REQUIRED, approvalStatus);
		}
	}

	@Test
	/** Check that a transaction, that requires approval, updates references and fees properly. */
	public void testReferencesAndFees() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceAccount = Common.getTestAccount(repository, "alice");

			int groupId = GroupUtils.createGroup(repository, "alice", "test", true, ApprovalThreshold.ONE, minBlockDelay, maxBlockDelay);

			GroupUtils.joinGroup(repository, "bob", groupId);

			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");
			byte[] bobOriginalReference = bobAccount.getLastReference();

			long aliceOriginalBalance = aliceAccount.getConfirmedBalance(Asset.QORT);
			long bobOriginalBalance = bobAccount.getConfirmedBalance(Asset.QORT);

			Long blockReward = BlockUtils.getNextBlockReward(repository);
			Transaction bobAssetTransaction = buildIssueAssetTransaction(repository, "bob", groupId);
			TransactionUtils.signAndMint(repository, bobAssetTransaction.getTransactionData(), bobAccount);

			// Confirm transaction needs approval, and hasn't been approved
			ApprovalStatus approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Bob's last-reference should have changed, even though the transaction itself hasn't been approved yet
			byte[] bobPostAssetReference = bobAccount.getLastReference();
			assertFalse("reference should have changed", Arrays.equals(bobOriginalReference, bobPostAssetReference));

			// Bob's balance should have the fee removed, even though the transaction itself hasn't been approved yet
			long bobPostAssetBalance = bobAccount.getConfirmedBalance(Asset.QORT);
			assertEquals("approval-pending transaction creator's balance incorrect", bobOriginalBalance - fee, bobPostAssetBalance);

			// Transaction fee should have ended up in forging account
			long alicePostAssetBalance = aliceAccount.getConfirmedBalance(Asset.QORT);
			assertEquals("block minter's balance incorrect", aliceOriginalBalance + blockReward + fee, alicePostAssetBalance);

			// Have Bob do a non-approval transaction to change his last-reference
			Transaction bobPaymentTransaction = buildPaymentTransaction(repository, "bob", "chloe", amount, Group.NO_GROUP);
			TransactionUtils.signAndMint(repository, bobPaymentTransaction.getTransactionData(), bobAccount);

			byte[] bobPostPaymentReference = bobAccount.getLastReference();
			assertFalse("reference should have changed", Arrays.equals(bobPostAssetReference, bobPostPaymentReference));

			// Have Alice approve Bob's approval-needed transaction
			GroupUtils.approveTransaction(repository, "alice", bobAssetTransaction.getTransactionData().getSignature(), true);

			// Now mint a few blocks so transaction is approved
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockUtils.mintBlock(repository);

			// Confirm transaction now approved
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.APPROVED, approvalStatus);

			// Check Bob's last reference hasn't been changed by transaction approval
			byte[] bobPostApprovalReference = bobAccount.getLastReference();
			assertTrue("reference should be unchanged", Arrays.equals(bobPostPaymentReference, bobPostApprovalReference));

			// Ok, now unwind/orphan all the above to double-check

			// Orphan blocks that decided transaction approval
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockUtils.orphanLastBlock(repository);

			// Check Bob's last reference is still correct
			byte[] bobReference = bobAccount.getLastReference();
			assertTrue("reference should be unchanged", Arrays.equals(bobPostPaymentReference, bobReference));

			// Orphan block containing Alice's group-approval transaction
			BlockUtils.orphanLastBlock(repository);

			// Check Bob's last reference is still correct
			bobReference = bobAccount.getLastReference();
			assertTrue("reference should be unchanged", Arrays.equals(bobPostPaymentReference, bobReference));

			// Orphan block containing Bob's non-approval payment transaction
			BlockUtils.orphanLastBlock(repository);

			// Check Bob's last reference has reverted to pre-payment value
			bobReference = bobAccount.getLastReference();
			assertTrue("reference should be pre-payment", Arrays.equals(bobPostAssetReference, bobReference));

			// Orphan block containing Bob's issue-asset approval-needed transaction
			BlockUtils.orphanLastBlock(repository);

			// Check Bob's last reference has reverted to original value
			bobReference = bobAccount.getLastReference();
			assertTrue("reference should be pre-payment", Arrays.equals(bobOriginalReference, bobReference));

			// Also check Bob's balance is back to original value
			long bobBalance = bobAccount.getConfirmedBalance(Asset.QORT);
			assertEquals("reverted balance doesn't match original", bobOriginalBalance, bobBalance);
		}
	}

	@Test
	/** Test generic approval. */
	public void testApproval() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			int groupId = GroupUtils.createGroup(repository, "alice", "test", true, ApprovalThreshold.ONE, minBlockDelay, maxBlockDelay);

			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");
			GroupUtils.joinGroup(repository, "bob", groupId);

			// Bob's issue-asset transaction needs group-approval
			Transaction bobAssetTransaction = buildIssueAssetTransaction(repository, "bob", groupId);
			TransactionUtils.signAndMint(repository, bobAssetTransaction.getTransactionData(), bobAccount);

			// Confirm transaction needs approval, and hasn't been approved
			ApprovalStatus approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction has no group-approval decision height
			Integer approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);

			// Have Alice approve Bob's approval-needed transaction
			GroupUtils.approveTransaction(repository, "alice", bobAssetTransaction.getTransactionData().getSignature(), true);

			// Now mint a few blocks so transaction is approved
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockUtils.mintBlock(repository);

			// Confirm transaction now approved
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.APPROVED, approvalStatus);

			// Confirm transaction now has a group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNotNull("group-approval decision height should not be null", approvalHeight);

			// Orphan blocks that decided approval
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockUtils.orphanLastBlock(repository);

			// Confirm transaction no longer approved
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction no longer has group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);

			// Orphan block containing Alice's group-approval transaction
			BlockUtils.orphanLastBlock(repository);

			// Confirm transaction no longer approved
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction no longer has group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);
		}
	}

	@Test
	/** Test generic rejection. */
	public void testRejection() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			int groupId = GroupUtils.createGroup(repository, "alice", "test", true, ApprovalThreshold.ONE, minBlockDelay, maxBlockDelay);

			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");
			GroupUtils.joinGroup(repository, "bob", groupId);

			// Bob's issue-asset transaction needs group-approval
			Transaction bobAssetTransaction = buildIssueAssetTransaction(repository, "bob", groupId);
			TransactionUtils.signAndMint(repository, bobAssetTransaction.getTransactionData(), bobAccount);

			// Confirm transaction needs approval, and hasn't been approved
			ApprovalStatus approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction has no group-approval decision height
			Integer approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);

			// Have Alice reject Bob's approval-needed transaction
			GroupUtils.approveTransaction(repository, "alice", bobAssetTransaction.getTransactionData().getSignature(), false);

			// Now mint a few blocks so transaction is approved
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockUtils.mintBlock(repository);

			// Confirm transaction now rejected
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.REJECTED, approvalStatus);

			// Confirm transaction now has a group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNotNull("group-approval decision height should not be null", approvalHeight);

			// Orphan blocks that decided rejection
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockUtils.orphanLastBlock(repository);

			// Confirm transaction no longer rejected
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction no longer has group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);

			// Orphan block containing Alice's group-approval transaction
			BlockUtils.orphanLastBlock(repository);

			// Confirm transaction no longer rejected
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction no longer has group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);
		}
	}

	@Test
	/** Test generic expiry. */
	public void testExpiry() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			int groupId = GroupUtils.createGroup(repository, "alice", "test", true, ApprovalThreshold.ONE, minBlockDelay, maxBlockDelay);

			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");
			GroupUtils.joinGroup(repository, "bob", groupId);

			// Bob's issue-asset transaction needs group-approval
			Transaction bobAssetTransaction = buildIssueAssetTransaction(repository, "bob", groupId);
			TransactionUtils.signAndMint(repository, bobAssetTransaction.getTransactionData(), bobAccount);

			// Confirm transaction needs approval, and hasn't been approved
			ApprovalStatus approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction has no group-approval decision height
			Integer approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);

			// Now mint a few blocks so group-approval for transaction expires
			for (int blockCount = 0; blockCount <= maxBlockDelay; ++blockCount)
				BlockUtils.mintBlock(repository);

			// Confirm transaction now expired
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.EXPIRED, approvalStatus);

			// Confirm transaction now has a group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNotNull("group-approval decision height should not be null", approvalHeight);

			// Orphan blocks that decided expiry
			for (int blockCount = 0; blockCount <= maxBlockDelay; ++blockCount)
				BlockUtils.orphanLastBlock(repository);

			// Confirm transaction no longer expired
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction no longer has group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);
		}
	}

	@Test
	/** Test generic invalid. */
	public void testInvalid() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceAccount = Common.getTestAccount(repository, "alice");
			int groupId = GroupUtils.createGroup(repository, "alice", "test", true, ApprovalThreshold.ONE, minBlockDelay, maxBlockDelay);

			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");
			GroupUtils.joinGroup(repository, "bob", groupId);

			// Bob's issue-asset transaction needs group-approval
			Transaction bobAssetTransaction = buildIssueAssetTransaction(repository, "bob", groupId);
			TransactionUtils.signAndMint(repository, bobAssetTransaction.getTransactionData(), bobAccount);

			// Confirm transaction needs approval, and hasn't been approved
			ApprovalStatus approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction has no group-approval decision height
			Integer approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);

			// Have Alice approve Bob's approval-needed transaction
			GroupUtils.approveTransaction(repository, "alice", bobAssetTransaction.getTransactionData().getSignature(), true);

			// But wait! Alice issues an asset with the same name before Bob's asset is issued!
			// This transaction will be auto-approved as Alice is the group owner (and admin)
			Transaction aliceAssetTransaction = buildIssueAssetTransaction(repository, "alice", groupId);
			TransactionUtils.signAndMint(repository, aliceAssetTransaction.getTransactionData(), aliceAccount);

			// Confirm Alice's transaction auto-approved
			approvalStatus = GroupUtils.getApprovalStatus(repository, aliceAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.NOT_REQUIRED, approvalStatus);

			// Now mint a few blocks so transaction is approved
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockUtils.mintBlock(repository);

			// Confirm Bob's transaction now invalid
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.INVALID, approvalStatus);

			// Confirm transaction now has a group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNotNull("group-approval decision height should not be null", approvalHeight);

			// Orphan blocks that decided group-approval
			for (int blockCount = 0; blockCount < minBlockDelay; ++blockCount)
				BlockUtils.orphanLastBlock(repository);

			// Confirm transaction no longer invalid
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction no longer has group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);

			// Orphan block containing Alice's issue-asset transaction
			BlockUtils.orphanLastBlock(repository);

			// Orphan block containing Alice's group-approval transaction
			BlockUtils.orphanLastBlock(repository);

			// Confirm transaction no longer approved
			approvalStatus = GroupUtils.getApprovalStatus(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertEquals("incorrect transaction approval status", ApprovalStatus.PENDING, approvalStatus);

			// Confirm transaction no longer has group-approval decision height
			approvalHeight = GroupUtils.getApprovalHeight(repository, bobAssetTransaction.getTransactionData().getSignature());
			assertNull("group-approval decision height should be null", approvalHeight);
		}
	}

	private Transaction buildPaymentTransaction(Repository repository, String sender, String recipient, long amount, int txGroupId) throws DataException {
		PrivateKeyAccount sendingAccount = Common.getTestAccount(repository, sender);
		PrivateKeyAccount recipientAccount = Common.getTestAccount(repository, recipient);

		byte[] reference = sendingAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, sendingAccount.getPublicKey(), fee, null);
		PaymentTransactionData transactionData = new PaymentTransactionData(baseTransactionData, recipientAccount.getAddress(), amount);

		return Transaction.fromData(repository, transactionData);
	}

	private Transaction buildIssueAssetTransaction(Repository repository, String testAccountName, int txGroupId) throws DataException {
		PrivateKeyAccount account = Common.getTestAccount(repository, testAccountName);

		byte[] reference = account.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, account.getPublicKey(), fee, null);
		TransactionData transactionData = new IssueAssetTransactionData(baseTransactionData, "test asset", "test asset desc", 1000L, true, "{}", false);

		return Transaction.fromData(repository, transactionData);
	}

}
