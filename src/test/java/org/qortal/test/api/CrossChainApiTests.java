package org.qortal.test.api;

import org.junit.Before;
import org.junit.Test;
import org.qortal.api.ApiError;
import org.qortal.api.resource.CrossChainResource;
import org.qortal.crosschain.SupportedBlockchain;
import org.qortal.test.common.ApiCommon;

public class CrossChainApiTests extends ApiCommon {

	private static final SupportedBlockchain SPECIFIC_BLOCKCHAIN = null;

	private CrossChainResource crossChainResource;

	@Before
	public void buildResource() {
		this.crossChainResource = (CrossChainResource) ApiCommon.buildResource(CrossChainResource.class);
	}

	@Test
	public void testGetTradeOffers() {
		assertNoApiError((limit, offset, reverse) -> this.crossChainResource.getTradeOffers(SPECIFIC_BLOCKCHAIN, limit, offset, reverse));
	}

	@Test
	public void testGetCompletedTrades() {
		long minimumTimestamp = System.currentTimeMillis();
		assertNoApiError((limit, offset, reverse) -> this.crossChainResource.getCompletedTrades(SPECIFIC_BLOCKCHAIN, minimumTimestamp, limit, offset, reverse));
	}

	@Test
	public void testInvalidGetCompletedTrades() {
		Integer limit = null;
		Integer offset = null;
		Boolean reverse = null;

		assertApiError(ApiError.INVALID_CRITERIA, () -> this.crossChainResource.getCompletedTrades(SPECIFIC_BLOCKCHAIN, -1L /*minimumTimestamp*/, limit, offset, reverse));
		assertApiError(ApiError.INVALID_CRITERIA, () -> this.crossChainResource.getCompletedTrades(SPECIFIC_BLOCKCHAIN, 0L /*minimumTimestamp*/, limit, offset, reverse));
	}

}
