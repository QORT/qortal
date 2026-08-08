package org.qortal.test.api;

import org.junit.Before;
import org.junit.Test;
import org.qortal.api.ApiError;
import org.qortal.api.ApiException;
import org.qortal.api.resource.AssetsResource;
import org.qortal.api.resource.TransactionsResource.ConfirmationStatus;
import org.qortal.repository.AccountRepository.BalanceOrdering;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.ApiCommon;
import org.qortal.test.common.AssetUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AssetsApiTests extends ApiCommon {

	private static final String FAKE_ORDER_ID_BASE58 = "C3CPq7c8PY";

	private AssetsResource assetsResource;

	@Before
	public void buildResource() throws DataException {
		this.assetsResource = (AssetsResource) ApiCommon.buildResource(AssetsResource.class);

		// Create some dummy data
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Matching orders, to create a trade
			AssetUtils.createOrder(repository, "alice", AssetUtils.goldAssetId, AssetUtils.otherAssetId, 1_00000000L, 1_00000000L);
			AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.goldAssetId, 1_00000000L, 1_00000000L);

			// Open order
			AssetUtils.createOrder(repository, "bob", AssetUtils.otherAssetId, AssetUtils.testAssetId, 1_00000000L, 1_00000000L);
		}
	}

	@Test
	public void testResource() {
		assertNotNull(this.assetsResource);
	}

	@Test
	public void testGetAccountAssetPairOrders() {
		String address = aliceAddress;
		final int assetId = 0;
		final int otherAssetId = 1;

		for (Boolean includeClosed : ALL_BOOLEAN_VALUES) {
			for (Boolean includeFulfilled : ALL_BOOLEAN_VALUES) {
				assertNotNull(this.assetsResource.getAccountAssetPairOrders(address, assetId, otherAssetId, includeClosed, includeFulfilled, null, null, null));
				assertNotNull(this.assetsResource.getAccountAssetPairOrders(address, assetId, otherAssetId, includeClosed, includeFulfilled, 1, 1, true));
			}
		}
	}

	@Test
	public void testGetAccountOrders() {
		for (Boolean includeClosed : TF_BOOLEAN_VALUES) {
			for (Boolean includeFulfilled : TF_BOOLEAN_VALUES) {
				assertNotNull(this.assetsResource.getAccountOrders(aliceAddress, includeClosed, includeFulfilled, null, null, null));
				assertNotNull(this.assetsResource.getAccountOrders(aliceAddress, includeClosed, includeFulfilled, 1, 1, true));
			}
		}
	}

	@Test
	public void testGetAggregatedOpenOrders() {
		assertNotNull(this.assetsResource.getAggregatedOpenOrders(0, 1, null, null, null));
		assertNotNull(this.assetsResource.getAggregatedOpenOrders(0, 1, 1, 1, true));
	}

	@Test
	public void testGetAllAssets() {
		assertNotNull(this.assetsResource.getAllAssets(null, null, null, null));
		assertNotNull(this.assetsResource.getAllAssets(false, null, null, null));
		assertNotNull(this.assetsResource.getAllAssets(false, 1, 1, true));
	}

	@Test
	public void testGetAssetBalances() {
		List<String> addresses = Arrays.asList(aliceAddress, bobAddress);
		List<Long> assetIds = Arrays.asList(0L, 1L, 2L, 3L);

		for (BalanceOrdering balanceOrdering : BalanceOrdering.values()) {
			for (Boolean excludeZero : ALL_BOOLEAN_VALUES) {
				assertNotNull(this.assetsResource.getAssetBalances(Collections.emptyList(), assetIds, balanceOrdering, excludeZero, null, null, null));
				assertNotNull(this.assetsResource.getAssetBalances(addresses, Collections.emptyList(), balanceOrdering, excludeZero, null, null, null));
				assertNotNull(this.assetsResource.getAssetBalances(addresses, assetIds, balanceOrdering, excludeZero, null, null, null));
				assertNotNull(this.assetsResource.getAssetBalances(addresses, assetIds, balanceOrdering, excludeZero, 1, 1, true));
			}
		}
	}

	@Test
	public void testGetAssetInfo() {
		assertNotNull(this.assetsResource.getAssetInfo((int) 0L, null));
		assertNotNull(this.assetsResource.getAssetInfo(null, "QORT"));
	}

	@Test
	public void testGetAssetOrder() {
		try {
			assertNotNull(this.assetsResource.getAssetOrder(FAKE_ORDER_ID_BASE58));
		} catch (ApiException e) {
			assertTrue(e.error == ApiError.ORDER_UNKNOWN.getCode());
		}
	}

	@Test
	public void testGetAssetOrderTrades() {
		try {
			assertNotNull(this.assetsResource.getAssetOrderTrades(FAKE_ORDER_ID_BASE58, null, null, null));
		} catch (ApiException e) {
			assertTrue(e.error == ApiError.ORDER_UNKNOWN.getCode());
		}

		try {
			assertNotNull(this.assetsResource.getAssetOrderTrades(FAKE_ORDER_ID_BASE58, 1, 1, true));
		} catch (ApiException e) {
			assertTrue(e.error == ApiError.ORDER_UNKNOWN.getCode());
		}
	}

	@Test
	public void testGetAssetTrades() {
		assertNotNull(this.assetsResource.getAssetTrades(0, 1, null, null, null));
		assertNotNull(this.assetsResource.getAssetTrades(0, 1, 1, 1, true));
	}

	@Test
	public void testGetAssetTransactions() {
		for (ConfirmationStatus confirmationStatus : ConfirmationStatus.values()) {
			assertNotNull(this.assetsResource.getAssetTransactions(0, confirmationStatus, null, null, null));
			assertNotNull(this.assetsResource.getAssetTransactions(0, confirmationStatus, 1, 1, true));
		}
	}

	@Test
	public void testGetAssetTransfers() {
		assertNotNull(this.assetsResource.getAssetTransfers(0, null, null, null, null));
		assertNotNull(this.assetsResource.getAssetTransfers(0, null, 1, 1, true));
		assertNotNull(this.assetsResource.getAssetTransfers(0, aliceAddress, null, null, null));
		assertNotNull(this.assetsResource.getAssetTransfers(0, aliceAddress, 1, 1, true));
	}

	@Test
	public void testGetOpenOrders() {
		assertNotNull(this.assetsResource.getOpenOrders(0, 1, null, null, null));
		assertNotNull(this.assetsResource.getOpenOrders(0, 1, 1, 1, true));
	}

	@Test
	public void testGetRecentTrades() {
		List<Long> assetIds = Arrays.asList(1L, 2L, 3L);

		assertNotNull(this.assetsResource.getRecentTrades(assetIds, Collections.emptyList(), null, null, null));
		assertNotNull(this.assetsResource.getRecentTrades(assetIds, Collections.emptyList(), 1, 1, true));
		assertNotNull(this.assetsResource.getRecentTrades(assetIds, assetIds, null, null, null));
		assertNotNull(this.assetsResource.getRecentTrades(assetIds, assetIds, 1, 1, true));
	}

}
