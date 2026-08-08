package org.qortal.test.assets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.data.asset.OrderData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AccountUtils;
import org.qortal.test.common.AssetUtils;
import org.qortal.test.common.Common;
import org.qortal.utils.Amounts;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TradingTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testSimple() throws DataException {
		// GOLD has a higher assetId than OTHER
		// amounts are in GOLD
		// prices are in OTHER/GOLD
		long goldAmount = 24L * Amounts.MULTIPLIER;
		long price = 2L * Amounts.MULTIPLIER;

		long otherAmount = 48L * Amounts.MULTIPLIER;

		long aliceAmount = goldAmount;
		long alicePrice = price;

		long bobAmount = goldAmount;
		long bobPrice = price;

		// Alice has GOLD, wants OTHER so her commitment is in GOLD
		long aliceCommitment = goldAmount;
		// Bob has OTHER, wants GOLD so his commitment is in OTHER
		long bobCommitment = otherAmount;

		long aliceReturn = otherAmount;
		long bobReturn = goldAmount;

		// alice (target) order: have 'goldAmount' GOLD, want OTHER @ 'price' OTHER/GOLD (commits goldAmount GOLD)
		// bob (initiating) order: have OTHER, want 'goldAmount' GOLD @ 'price' OTHER/GOLD (commits goldAmount*price = otherAmount OTHER)
		// Alice should be -goldAmount, +otherAmount
		// Bob should be -otherAmount, +goldAmount

		long bobSaving = 0L; // no price improvement

		AssetUtils.genericTradeTest(AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	@Test
	public void testSimpleInverted() throws DataException {
		// TEST has a lower assetId than OTHER, so this is 'inverted' viz-a-viz have/want assetIds, compared with testSimple() above
		// amounts are in OTHER
		// prices are in TEST/OTHER
		long testAmount = 48L * Amounts.MULTIPLIER;
		long price = 2L * Amounts.MULTIPLIER;

		long otherAmount = 24L * Amounts.MULTIPLIER;

		long aliceAmount = otherAmount;
		long alicePrice = price;

		long bobAmount = otherAmount;
		long bobPrice = price;

		long aliceCommitment = testAmount;
		long bobCommitment = otherAmount;

		long aliceReturn = otherAmount;
		long bobReturn = testAmount;

		// alice (target) order: have TEST, want 'otherAmount' OTHER @ 'price' TEST/OTHER (commits otherAmount*price = testAmount TEST)
		// bob (initiating) order: have 'otherAmount' OTHER, want TEST @ 'price' TEST/OTHER (commits otherAmount OTHER)
		// Alice should be -testAmount, +otherAmount
		// Bob should be -otherAmount, +testAmount

		long bobSaving = 0L; // no price improvement

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check matching using divisible and indivisible assets.
	 */
	@Test
	public void testMixedDivisibility() throws DataException {
		// Issue indivisible asset, which will have higher assetId than anything else, so amounts will be in INDIV
		long indivAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			indivAssetId = AssetUtils.issueAsset(repository, "alice", "INDIV", 1000000_00000000L, false);
		}

		// amounts are in INDIV
		// prices are in OTHER/INDIV
		long indivAmount = 2L * Amounts.MULTIPLIER;
		long price = 12L * Amounts.MULTIPLIER;

		long otherAmount = 24L * Amounts.MULTIPLIER;

		long aliceAmount = indivAmount;
		long alicePrice = price;

		long bobAmount = indivAmount;
		long bobPrice = price;

		long aliceCommitment = indivAmount;
		long bobCommitment = otherAmount;

		long aliceReturn = otherAmount;
		long bobReturn = indivAmount;

		long bobSaving = 0L; // no price improvement

		AssetUtils.genericTradeTest(indivAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check matching using divisible and indivisible assets.
	 */
	@Test
	public void testMixedDivisibilityInverted() throws DataException {
		// Issue indivisible asset, which will have higher assetId than anything else, so amounts will be in INDIV
		long indivAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			indivAssetId = AssetUtils.issueAsset(repository, "bob", "INDIV", 1000000_00000000L, false);
		}

		// amounts are in INDIV
		// prices are in TEST/INDIV
		long indivAmount = 2L * Amounts.MULTIPLIER;
		long price = 12L * Amounts.MULTIPLIER;

		long testAmount = 24L * Amounts.MULTIPLIER;

		long aliceAmount = indivAmount;
		long alicePrice = price;

		long bobAmount = indivAmount;
		long bobPrice = price;

		long aliceCommitment = testAmount;
		long bobCommitment = indivAmount;

		long aliceReturn = indivAmount;
		long bobReturn = testAmount;

		long bobSaving = 0L; // no price improvement

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, indivAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check matching of indivisible amounts (new pricing).
	 * <p>
	 * Alice is selling twice as much as Bob wants,
	 * but at the same [calculated] unit price,
	 * so Bob's order should fully match.
	 * <p>
	 * However, in legacy/"old" mode, the granularity checks
	 * would prevent this trade.
	 */
	@Test
	public void testIndivisible() throws DataException {
		// Issue some indivisible assets
		long ragsAssetId;
		long richesAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			// Issue indivisible asset
			ragsAssetId = AssetUtils.issueAsset(repository, "alice", "rags", 1000000_00000000L, false);

			// Issue another indivisible asset
			richesAssetId = AssetUtils.issueAsset(repository, "bob", "riches", 1000000_00000000L, false);
		}

		// "amount" will be in riches, "price" will be in rags/riches

		long ragsAmount = 50307_00000000L;
		long richesAmount = 123_00000000L;

		long price = Amounts.scaledDivide(ragsAmount, richesAmount);

		long aliceAmount = richesAmount * 2;
		long alicePrice = price;
		long aliceCommitment = Amounts.roundUpScaledMultiply(aliceAmount, alicePrice); // rags

		long bobAmount = richesAmount;
		long bobPrice = price;
		long bobCommitment = bobAmount; // riches

		long matchedAmount = Math.min(aliceAmount, bobAmount);

		long aliceReturn = bobAmount; // riches
		long bobReturn = Amounts.roundDownScaledMultiply(matchedAmount, alicePrice); // rags

		long bobSaving = 0L; // no price improvement

		AssetUtils.genericTradeTest(ragsAssetId, richesAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check partial matching of indivisible amounts (new pricing).
	 * <p>
	 * Assume both "rags" and "riches" assets are indivisible.
	 * 
	 * Alice places an order:
	 * Have rags, want riches, amount 3 riches, price 1 rags/riches
	 * 
	 * Alice has 1 * 3 = 3 rags subtracted from their rags balance.
	 * 
	 * Bob places an order:
	 * Have riches, want rags, amount 8 riches, price 0.25 rags/riches
	 * 
	 * Bob has 8 riches subtracted from their riches balance.
	 * Bob expects at least 8 * 0.25 = 2 rags if his order fully completes.
	 * 
	 * Alice is offering more rags for riches than Bob expects.
	 * So Alice's order is a match for Bob's, and Alice's order price is used.
	 * 
	 * Bob wants to trade 8 riches, but Alice only wants to trade 3 riches,
	 * so the matched amount is 3 riches.
	 * 
	 * Bob gains 3 * 1 = 3 rags and Alice gains 3 riches.
	 * Alice's order has 0 riches left (fully completed).
	 * 
	 * Bob's order has 8 - 3 = 5 riches left.
	 * 
	 * At Bob's order's price of 0.25 rags/riches,
	 * it would take 1.25 rags to complete the rest of Bob's order.
	 * But rags are indivisible so this can't happen at that price.
	 * 
	 * However, someone could buy at a better price, e.g. 0.4 rags/riches,
	 * trading 2 rags for 5 riches.
	 * 
	 * Or Bob could cancel the rest of his order and be refunded 5 riches.
	 */
	@Test
	public void testPartialIndivisible() throws DataException {
		// Issue some indivisible assets
		long ragsAssetId;
		long richesAssetId;
		try (Repository repository = RepositoryManager.getRepository()) {
			// Issue indivisible asset
			ragsAssetId = AssetUtils.issueAsset(repository, "alice", "rags", 1000000_00000000L, false);

			// Issue another indivisible asset
			richesAssetId = AssetUtils.issueAsset(repository, "bob", "riches", 1000000_00000000L, false);
		}

		// "amount" will be in riches, "price" will be in rags/riches

		// Buying 3 riches @ 1 rags/riches max, so expecting to pay 3 rags max
		long aliceAmount = 3_00000000L;
		long alicePrice = 1_00000000L;
		long aliceCommitment = Amounts.roundUpScaledMultiply(aliceAmount, alicePrice); // rags

		// Selling 8 riches @ 0.25 rags/riches min, so expecting 2 rags min
		long bobAmount = 8_00000000L;
		long bobPrice = 25000000L;
		long bobCommitment = bobAmount; // riches

		long matchedAmount = Math.min(aliceAmount, bobAmount);

		long aliceReturn = aliceAmount; // riches
		long bobReturn = Amounts.roundDownScaledMultiply(matchedAmount, alicePrice);

		long bobSaving = 0L; // no price improvement

		AssetUtils.genericTradeTest(ragsAssetId, richesAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check matching of orders with prices that
	 * would have had reciprocals that can't be represented in floating binary.
	 * <p>
	 * For example, sell 2 TEST for 24 OTHER so
	 * unit price is 2 / 24 or 0.08333333(recurring) TEST/OTHER.
	 * <p>
	 * But although price is rounded down to 0.08333333,
	 * the price is the same for both sides.
	 * <p>
	 * Traded amounts are expected to be 24 OTHER
	 * and 1.99999992 TEST.
	 */
	@Test
	public void testNonExactFraction() throws DataException {
		// TEST has a lower assetId than OTHER
		// amounts are in OTHER
		// prices are in TEST/OTHER

		long aliceAmount = 24_00000000L; // OTHER
		long alicePrice = 8333333L; // TEST/OTHER
		long aliceCommitment = 1_99999992L; // 24 * 0.08333333 = 1.99999992 TEST

		long bobAmount = 24_00000000L; // OTHER
		long bobPrice = 8333333L; // TEST/OTHER
		long bobCommitment = 24_00000000L; // OTHER

		// Expected traded amounts
		long aliceReturn = 24_00000000L; // OTHER
		long bobReturn = 1_99999992L; // TEST

		long bobSaving = 0L; // no price improvement

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check matching of orders with price improvement.
	 */
	@Test
	public void testSimplePriceImprovement() throws DataException {
		// TEST has a lower assetId than OTHER
		// amounts are in OTHER
		// prices are in TEST/OTHER

		// Alice is buying OTHER
		long aliceAmount = 100_00000000L; // OTHER
		long alicePrice = 30000000L; // TEST/OTHER
		long aliceCommitment = 30_00000000L; // 100 * 0.3 = 30 TEST

		// Bob is selling OTHER
		long bobAmount = 100_00000000L; // OTHER
		long bobPrice = 20000000L; // TEST/OTHER
		long bobCommitment = 100_00000000L; // OTHER

		// Expected traded amounts
		long aliceReturn = 100_00000000L; // OTHER
		long bobReturn = 30_00000000L; // 100 * 0.3 = 30 TEST (Alice's price)

		long bobSaving = 0L; // No price improvement for Bob

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check matching of orders with price improvement.
	 */
	@Test
	public void testSimplePriceImprovementInverted() throws DataException {
		// GOLD has a higher assetId than OTHER
		// amounts are in GOLD
		// prices are in OTHER/GOLD

		// Alice is selling GOLD
		long aliceAmount = 100_00000000L; // GOLD
		long alicePrice = 2_00000000L; // OTHER/GOLD
		long aliceCommitment = 100_00000000L; // GOLD

		// Bob is buying GOLD
		long bobAmount = 50_00000000L; // GOLD
		long bobPrice = 3_00000000L; // OTHER/GOLD
		long bobCommitment = 150_00000000L; // 50 * 3 = 150 OTHER

		// Expected traded amounts
		long aliceReturn = 100_00000000L; // 50 * 2 = 100 OTHER
		long bobReturn = 50_00000000L; // 50 GOLD

		long bobSaving = 50_00000000L; // 50 * (3 - 2) = 50 OTHER

		AssetUtils.genericTradeTest(AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check that better prices are used in preference when matching orders.
	 */
	@Test
	public void testPriceImprovement() throws DataException {
		// GOLD has a higher assetId than OTHER
		// Amounts are in GOLD
		// Prices are in OTHER/GOLD

		long initialGoldAssetAmount = 24_00000000L;

		long basePrice = 1_00000000L;
		long betterPrice = 2_10000000L;
		long bestPrice = 2_40000000L;

		long midwayPrice = 1_5000000L;
		long matchingGoldAssetAmount = 12_00000000L;

		try (Repository repository = RepositoryManager.getRepository()) {
			// Give some OTHER to Chloe and Dilbert
			AssetUtils.transferAsset(repository, "bob", "chloe", AssetUtils.otherAssetId, 1000_00000000L);
			AssetUtils.transferAsset(repository, "bob", "dilbert", AssetUtils.otherAssetId, 1000_00000000L);

			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.otherAssetId, AssetUtils.goldAssetId);

			// Create 'better' initial order: buying GOLD @ betterPrice
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.goldAssetId, initialGoldAssetAmount, betterPrice);

			// Create 'best' initial - surrounded by other orders so price improvement code should re-order results
			byte[] chloeOrderId = AssetUtils.createOrder(repository, "chloe", AssetUtils.otherAssetId, AssetUtils.goldAssetId, initialGoldAssetAmount, bestPrice);

			// Create 'base' initial order: buying GOLD @ basePrice (shouldn't even match)
			byte[] dilbertOrderId = AssetUtils.createOrder(repository, "dilbert", AssetUtils.otherAssetId, AssetUtils.goldAssetId, initialGoldAssetAmount, basePrice);

			// Create matching order: selling GOLD @ midwayPrice which would match at least one buy order
			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.goldAssetId, AssetUtils.otherAssetId, matchingGoldAssetAmount, midwayPrice);

			// Check balances to check expected outcome
			long expectedBalance;

			// We're expecting Alice's order to match with Chloe's order (as Bob's and Dilberts's orders have worse prices)
			long matchedOtherAmount = Amounts.roundDownScaledMultiply(matchingGoldAssetAmount, bestPrice);
			long tradedGoldAssetAmount = matchingGoldAssetAmount;

			// XXX surely either "market maker" (i.e. target order) should receive benefit, or Alice should receive a partial refund?
			// NO refund due to price improvement - Alice receives more OTHER back than she was expecting
			long aliceSaving = 0L;

			// Alice GOLD
			long aliceCommitment = matchingGoldAssetAmount;
			expectedBalance = initialBalances.get("alice").get(AssetUtils.goldAssetId) - aliceCommitment + aliceSaving;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.goldAssetId, expectedBalance);

			// Alice OTHER
			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId) + matchedOtherAmount;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob OTHER
			long bobCommitment = Amounts.roundDownScaledMultiply(initialGoldAssetAmount, betterPrice);
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId) - bobCommitment;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			// Bob GOLD
			expectedBalance = initialBalances.get("bob").get(AssetUtils.goldAssetId);
			AccountUtils.assertBalance(repository, "bob", AssetUtils.goldAssetId, expectedBalance);

			// Chloe OTHER
			long chloeCommitment = Amounts.roundDownScaledMultiply(initialGoldAssetAmount, bestPrice);
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.otherAssetId) - chloeCommitment;
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.otherAssetId, expectedBalance);

			// Chloe GOLD
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.goldAssetId) + tradedGoldAssetAmount; // Alice traded with Chloe
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.goldAssetId, expectedBalance);

			// Dilbert OTHER
			long dilbertCommitment = Amounts.roundDownScaledMultiply(initialGoldAssetAmount, basePrice);
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.otherAssetId) - dilbertCommitment;
			AccountUtils.assertBalance(repository, "dilbert", AssetUtils.otherAssetId, expectedBalance);

			// Dilbert GOLD
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.goldAssetId);
			AccountUtils.assertBalance(repository, "dilbert", AssetUtils.goldAssetId, expectedBalance);

			// Check orders
			OrderData aliceOrderData = repository.getAssetRepository().fromOrderId(aliceOrderId);
			OrderData bobOrderData = repository.getAssetRepository().fromOrderId(bobOrderId);
			OrderData chloeOrderData = repository.getAssetRepository().fromOrderId(chloeOrderId);
			OrderData dilbertOrderData = repository.getAssetRepository().fromOrderId(dilbertOrderId);

			// Alice's fulfilled
			assertEquals("Alice's order's fulfilled amount incorrect", tradedGoldAssetAmount, aliceOrderData.getFulfilled());

			// Bob's fulfilled should be zero
			assertEquals("Bob's order should be totally unfulfilled", 0L, bobOrderData.getFulfilled());

			// Chloe's fulfilled
			assertEquals("Chloe's order's fulfilled amount incorrect", tradedGoldAssetAmount, chloeOrderData.getFulfilled());

			// Dilbert's fulfilled should be zero
			assertEquals("Dilbert's order should be totally unfulfilled", 0L, dilbertOrderData.getFulfilled());
		}
	}

	/**
	 * Check that better prices are used in preference when matching orders.
	 */
	@Test
	public void testPriceImprovementInverted() throws DataException {
		// Amounts are in OTHER
		// Prices are in TEST/OTHER

		long initialOtherAmount = 24_00000000L;

		long basePrice = 3_00000000L;
		long betterPrice = 2_10000000L;
		long bestPrice = 1_40000000L;

		long midwayPrice = 2_50000000L;
		long aliceOtherAmount = 12_00000000L;

		try (Repository repository = RepositoryManager.getRepository()) {
			// Give some OTHER to Chloe and Dilbert
			AssetUtils.transferAsset(repository, "bob", "chloe", AssetUtils.otherAssetId, 1000_00000000L);
			AssetUtils.transferAsset(repository, "bob", "dilbert", AssetUtils.otherAssetId, 1000_00000000L);

			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, AssetUtils.testAssetId, AssetUtils.otherAssetId);

			// Create 'better' initial order: selling OTHER @ betterPrice
			byte[] bobOrderId = AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.testAssetId, initialOtherAmount, betterPrice);

			// Create 'best' initial - surrounded by other orders so price improvement code should re-order results
			byte[] chloeOrderId = AssetUtils.createOrder(repository, "chloe", AssetUtils.otherAssetId, AssetUtils.testAssetId, initialOtherAmount, bestPrice);

			// Create 'base' initial order: selling OTHER @ basePrice (shouldn't even match)
			byte[] dilbertOrderId = AssetUtils.createOrder(repository, "dilbert", AssetUtils.otherAssetId, AssetUtils.testAssetId, initialOtherAmount, basePrice);

			// Create matching order: buying OTHER @ midwayPrice which would match at least one sell order
			byte[] aliceOrderId = AssetUtils.createOrder(repository, "alice", AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceOtherAmount, midwayPrice);

			// Check balances to check expected outcome
			long expectedBalance;

			// We're expecting Alice's order to match with Chloe's order (as Bob's and Dilberts's orders have worse prices)
			long matchedOtherAmount = aliceOtherAmount;
			long tradedTestAmount = Amounts.roundDownScaledMultiply(aliceOtherAmount, bestPrice);
			// Due to price improvement, Alice should get back some of her TEST
			long aliceSaving = Amounts.roundUpScaledMultiply(matchedOtherAmount, Math.abs(midwayPrice - bestPrice));

			// Alice TEST
			long aliceCommitment = Amounts.roundUpScaledMultiply(aliceOtherAmount, midwayPrice);
			expectedBalance = initialBalances.get("alice").get(AssetUtils.testAssetId) - aliceCommitment + aliceSaving;
			AccountUtils.assertBalance(repository, "alice", AssetUtils.testAssetId, expectedBalance);

			// Alice OTHER
			expectedBalance = initialBalances.get("alice").get(AssetUtils.otherAssetId) + matchedOtherAmount; // traded with Chloe
			AccountUtils.assertBalance(repository, "alice", AssetUtils.otherAssetId, expectedBalance);

			// Bob OTHER
			expectedBalance = initialBalances.get("bob").get(AssetUtils.otherAssetId) - initialOtherAmount;
			AccountUtils.assertBalance(repository, "bob", AssetUtils.otherAssetId, expectedBalance);

			// Bob TEST
			expectedBalance = initialBalances.get("bob").get(AssetUtils.testAssetId); // no trade
			AccountUtils.assertBalance(repository, "bob", AssetUtils.testAssetId, expectedBalance);

			// Chloe OTHER
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.otherAssetId) - initialOtherAmount;
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.otherAssetId, expectedBalance);

			// Chloe TEST
			expectedBalance = initialBalances.get("chloe").get(AssetUtils.testAssetId) + tradedTestAmount; // traded with Alice
			AccountUtils.assertBalance(repository, "chloe", AssetUtils.testAssetId, expectedBalance);

			// Dilbert OTHER
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.otherAssetId) - initialOtherAmount;
			AccountUtils.assertBalance(repository, "dilbert", AssetUtils.otherAssetId, expectedBalance);

			// Dilbert TEST
			expectedBalance = initialBalances.get("dilbert").get(AssetUtils.testAssetId); // no trade
			AccountUtils.assertBalance(repository, "dilbert", AssetUtils.testAssetId, expectedBalance);

			// Check orders
			OrderData aliceOrderData = repository.getAssetRepository().fromOrderId(aliceOrderId);
			OrderData bobOrderData = repository.getAssetRepository().fromOrderId(bobOrderId);
			OrderData chloeOrderData = repository.getAssetRepository().fromOrderId(chloeOrderId);
			OrderData dilbertOrderData = repository.getAssetRepository().fromOrderId(dilbertOrderId);

			// Alice's fulfilled
			assertEquals("Alice's order's fulfilled amount incorrect", matchedOtherAmount, aliceOrderData.getFulfilled());

			// Bob's fulfilled should be zero
			assertEquals("Bob's order should be totally unfulfilled", 0L, bobOrderData.getFulfilled());

			// Chloe's fulfilled
			assertEquals("Chloe's order's fulfilled amount incorrect", matchedOtherAmount, chloeOrderData.getFulfilled());

			// Dilbert's fulfilled should be zero
			assertEquals("Dilbert's order should be totally unfulfilled", 0L, dilbertOrderData.getFulfilled());
		}
	}

	/**
	 * Check that orders don't match.
	 * <p>
	 * "target" order with have-asset = amount-asset
	 */
	@Test
	public void testWorsePriceNoMatch() throws DataException {
		// amounts are in GOLD
		// prices are in OTHER/GOLD

		// Selling 10 GOLD @ 2 OTHER/GOLD min so wants 20 OTHER minimum
		long aliceAmount = 10_00000000L;
		long alicePrice = 2_00000000L;

		// Buying 10 GOLD @ 1 OTHER/GOLD max, paying 10 OTHER maximum
		long bobAmount = 10_00000000L;
		long bobPrice = 1_00000000L;

		long aliceCommitment = 10_00000000L; // 10 GOLD
		long bobCommitment = 10_00000000L; // 10 GOLD * 1 OTHER/GOLD = 10 OTHER

		// Orders should not match!
		long aliceReturn = 0L;
		long bobReturn = 0L;
		long bobSaving = 0L;

		AssetUtils.genericTradeTest(AssetUtils.goldAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

	/**
	 * Check that orders don't match.
	 * <p>
	 * "target" order with want-asset = amount-asset
	 */
	@Test
	public void testWorsePriceNoMatchInverted() throws DataException {
		// amounts are in OTHER
		// prices are in TEST/OTHER

		// Buying 10 OTHER @ 1 TEST/OTHER max, paying 10 TEST maximum
		long aliceAmount = 10_00000000L;
		long alicePrice = 1_00000000L;

		// Selling 10 OTHER @ 2 TEST/OTHER min, so wants 20 TEST minimum
		long bobAmount = 10_00000000L; // OTHER
		long bobPrice = 2_00000000L;

		long aliceCommitment = 10_00000000L; // 10 OTHER * 1 TEST/OTHER = 10 TEST
		long bobCommitment = 10_00000000L; // 10 OTHER

		// Orders should not match!
		long aliceReturn = 0L;
		long bobReturn = 0L;
		long bobSaving = 0L;

		AssetUtils.genericTradeTest(AssetUtils.testAssetId, AssetUtils.otherAssetId, aliceAmount, alicePrice, bobAmount, bobPrice, aliceCommitment, bobCommitment, aliceReturn, bobReturn, bobSaving);
	}

}
