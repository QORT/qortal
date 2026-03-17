package org.qortal.test.assets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AccountUtils;
import org.qortal.test.common.AssetUtils;
import org.qortal.test.common.Common;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.utils.Amounts;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CancellingTests extends Common {

	/*
	 * Commitments are always rounded up.
	 * Returns (amounts traded) are always rounded down.
	 * Thus expected post-cancel refunds should be rounded up too.
	 */

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testSimpleCancel() throws DataException {
		long amount = 1234_87654321L;
		long price = 1_35615263L;

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.testAssetId, AssetUtils.otherAssetId);

			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, AssetUtils.otherAssetId, amount, price);
			AssetUtils.cancelOrder(repository, "alice", aliceOrderId);

			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.testAssetId, amount, price);
			AssetUtils.cancelOrder(repository, "bob", bobOrderId);

			// Check asset balances match pre-ordering values
			long expectedBalance;

			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);
		}
	}

	@Test
	public void testRepeatCancel() throws DataException {
		long amount = 1234_87654321L;
		long price = 1_35615263L;

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.testAssetId, AssetUtils.otherAssetId);

			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, AssetUtils.otherAssetId, amount, price);
			AssetUtils.cancelOrder(repository, "alice", aliceOrderId);

			// Build 2nd cancel-order transaction to check it is invalid
			assertCannotCancelClosedOrder(repository, "alice", aliceOrderId);

			// Check asset balances match pre-ordering values
			long expectedBalance;

			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId);
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);
		}
	}

	@Test
	public void testPartialTargetMatchCancel() throws DataException {
		// TEST has a lower assetId than OTHER

		// Alice has TEST, wants OTHER
		long aliceAmount = 1234_00000000L; // OTHER is 'want' asset
		long alicePrice = 1_50000000L; // TEST/OTHER

		// Bob has OTHER, wants TEST
		long bobAmount = 500_00000000L; // OTHER is 'have' asset
		long bobPrice = 1_20000000L; // TEST/OTHER

		long aliceCommitment = Amounts.roundUpScaledMultiply(aliceAmount, alicePrice); // TEST
		long bobCommitment = bobAmount; // OTHER

		long matchedAmount = Math.min(aliceAmount, bobAmount); // 500 OTHER

		long aliceReturn = matchedAmount; // OTHER
		long bobReturn = Amounts.roundDownScaledMultiply(matchedAmount, alicePrice); // TEST

		long aliceRefund = Amounts.roundUpScaledMultiply(aliceAmount - matchedAmount,  alicePrice); // TEST
		long bobRefund = 0L; // because Bob's order is fully matched

		long bobSaving = 0L; // not in this direction

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.testAssetId, AssetUtils.otherAssetId);

			// Place 'target' order
			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice);
			// Place 'initiating' order: the order that initiates a trade
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.testAssetId, bobAmount, bobPrice);

			AssetUtils.cancelOrder(repository, "alice", aliceOrderId);
			assertCannotCancelClosedOrder(repository, "bob", bobOrderId); // because full matched

			// Check asset balances
			long expectedBalance;

			// Alice
			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId) - aliceCommitment + aliceRefund;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId) + aliceReturn;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId) - bobCommitment + bobSaving + bobRefund;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId) + bobReturn;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);
		}
	}

	@Test
	public void testPartialInitiatorMatchCancel() throws DataException {
		// TEST has a lower assetId than OTHER

		// Alice has TEST, wants OTHER
		long aliceAmount = 500_00000000L; // OTHER is 'want' asset
		long alicePrice = 1_50000000L; // TEST/OTHER

		// Bob has OTHER, wants TEST
		long bobAmount = 1234_00000000L; // OTHER is 'have' asset
		long bobPrice = 1_20000000L; // TEST/OTHER

		long aliceCommitment = Amounts.roundUpScaledMultiply(aliceAmount, alicePrice); // TEST
		long bobCommitment = bobAmount; // OTHER

		long matchedAmount = Math.min(aliceAmount, bobAmount); // 500 OTHER

		long aliceReturn = matchedAmount; // OTHER
		long bobReturn = Amounts.roundDownScaledMultiply(matchedAmount, alicePrice); // TEST

		long aliceRefund = 0L; // because Alice's order is fully matched
		long bobRefund = bobAmount - matchedAmount; // OTHER

		long bobSaving = 0L; // not in this direction

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.testAssetId, AssetUtils.otherAssetId);

			// Place 'target' order
			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice);
			// Place 'initiating' order: the order that initiates a trade
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.testAssetId, bobAmount, bobPrice);

			assertCannotCancelClosedOrder(repository, "alice", aliceOrderId); // because fully matched
			AssetUtils.cancelOrder(repository, "bob", bobOrderId);

			// Check asset balances
			long expectedBalance;

			// Alice
			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId) - aliceCommitment + aliceRefund;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId) + aliceReturn;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId) - bobCommitment + bobSaving + bobRefund;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId) + bobReturn;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);
		}
	}

	@Test
	public void testPartialTargetMatchCancelInverted() throws DataException {
		// GOLD has a higher assetId than OTHER, hence "inverted" viz-a-viz have/want assetIds

		// Alice has GOLD, wants OTHER
		long aliceAmount = 1234_00000000L; // GOLD is 'have' asset
		long alicePrice = 1_20000000L; // OTHER/GOLD

		// Bob has OTHER, wants GOLD
		long bobAmount = 500_00000000L; // GOLD is 'want' asset
		long bobPrice = 1_50000000L; // OTHER/GOLD

		long aliceCommitment = aliceAmount; // GOLD
		long bobCommitment = Amounts.roundUpScaledMultiply(bobAmount, bobPrice); // OTHER

		long matchedAmount = Math.min(aliceAmount, bobAmount); // 500 GOLD

		long aliceReturn = Amounts.roundDownScaledMultiply(matchedAmount, alicePrice); // OTHER
		long bobReturn = matchedAmount; // GOLD

		long aliceRefund = aliceAmount - matchedAmount; // GOLD
		long bobRefund = 0L; // because Bob's order is fully matched

		long bobSaving = 150_00000000L; // (1.5 - 1.2) * 500 = 150 OTHER

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.goldAssetId, AssetUtils.otherAssetId);

			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice);
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.goldAssetId, bobAmount, bobPrice);

			AssetUtils.cancelOrder(repository, "alice", aliceOrderId);
			assertCannotCancelClosedOrder(repository, "bob", bobOrderId);

			// Check asset balances
			long expectedBalance;

			// Alice
			expectedBalance = initialBalances.get("alice").get(AssetUtils.goldAssetId) - aliceCommitment + aliceRefund;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.goldAssetId, expectedBalance);

			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId) + aliceReturn;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId) - bobCommitment + bobSaving + bobRefund;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.goldAssetId) + bobReturn;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.goldAssetId, expectedBalance);
		}
	}

	@Test
	public void testPartialInitiatorMatchCancelInverted() throws DataException {
		// GOLD has a higher assetId than OTHER, hence "inverted" viz-a-viz have/want assetIds

		// Alice has GOLD, wants OTHER
		long aliceAmount = 500_00000000L; // GOLD is 'have' asset
		long alicePrice = 1_20000000L; // OTHER/GOLD

		// Bob has OTHER, wants GOLD
		long bobAmount = 1234_00000000L; // GOLD is 'want' asset
		long bobPrice = 1_50000000L; // OTHER/GOLD

		long aliceCommitment = aliceAmount; // GOLD
		long bobCommitment = Amounts.roundUpScaledMultiply(bobAmount, bobPrice); // OTHER

		long matchedAmount = Math.min(aliceAmount, bobAmount); // 500 GOLD

		long aliceReturn = Amounts.roundDownScaledMultiply(matchedAmount, alicePrice); // OTHER
		long bobReturn = matchedAmount; // GOLD

		long aliceRefund = 0L; // because Alice's order is fully matched
		long bobRefund = Amounts.roundUpScaledMultiply(bobAmount - matchedAmount, bobPrice); // OTHER

		long bobSaving = 150_00000000L; // (1.5 - 1.2) * 500 = 150 OTHER

		try (Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.goldAssetId, AssetUtils.otherAssetId);

			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice);
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.goldAssetId, bobAmount, bobPrice);

			assertCannotCancelClosedOrder(repository, "alice", aliceOrderId);
			AssetUtils.cancelOrder(repository, "bob", bobOrderId);

			// Check asset balances
			long expectedBalance;

			// Alice
			expectedBalance = initialBalances.get("alice").get(AssetUtils.goldAssetId) - aliceCommitment + aliceRefund;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.goldAssetId, expectedBalance);

			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId) + aliceReturn;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId) - bobCommitment + bobSaving + bobRefund;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			expectedBalance = initialBalances.get("bob").get(AssetUtils.goldAssetId) + bobReturn;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.goldAssetId, expectedBalance);
		}
	}

	public void assertCannotCancelClosedOrder(Repository repository, String accountName, byte[] orderId) throws DataException {
		Transaction transaction = AssetUtils.buildCancelOrder(repository, accountName, orderId);
		ValidationResult validationResult = transaction.isValidUnconfirmed();
		assertEquals("CANCEL_ASSET_ORDER should be invalid due to already closed order", ValidationResult.ORDER_ALREADY_CLOSED, validationResult);
	}

}
