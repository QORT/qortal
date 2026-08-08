package org.qortal.test.api;

import org.junit.Before;
import org.junit.Test;
import org.qortal.api.resource.ArbitraryResource;
import org.qortal.api.resource.TransactionsResource.ConfirmationStatus;
import org.qortal.arbitrary.misc.Service;
import org.qortal.test.common.ApiCommon;

import static org.junit.Assert.assertNotNull;

public class ArbitraryApiTests extends ApiCommon {

	private ArbitraryResource arbitraryResource;

	@Before
	public void buildResource() {
		this.arbitraryResource = (ArbitraryResource) ApiCommon.buildResource(ArbitraryResource.class);
	}

	@Test
	public void testSearch() {
		Integer[] startingBlocks = new Integer[] { null, 0, 1, 999999999 };
		Integer[] blockLimits = new Integer[] { null, 0, 1, 999999999 };
		Integer[] txGroupIds = new Integer[] { null, 0, 1, 999999999 };
		Service[] services = new Service[] { Service.WEBSITE, Service.GIT_REPOSITORY, Service.BLOG_COMMENT };
		String[] names = new String[] { null, "Test" };
		String[] addresses = new String[] { null, this.aliceAddress };
		ConfirmationStatus[] confirmationStatuses = new ConfirmationStatus[] { ConfirmationStatus.UNCONFIRMED, ConfirmationStatus.CONFIRMED, ConfirmationStatus.BOTH };

		for (Integer startBlock : startingBlocks)
			for (Integer blockLimit : blockLimits)
				for (Integer txGroupId : txGroupIds)
					for (Service service : services)
						for (String name : names)
							for (String address : addresses)
								for (ConfirmationStatus confirmationStatus : confirmationStatuses) {
									if (confirmationStatus != ConfirmationStatus.CONFIRMED && (startBlock != null || blockLimit != null))
										continue;

									assertNotNull(this.arbitraryResource.searchTransactions(startBlock, blockLimit, txGroupId, service, name, address, confirmationStatus, 20, null, null));
									assertNotNull(this.arbitraryResource.searchTransactions(startBlock, blockLimit, txGroupId, service, name, address, confirmationStatus, 1, 1, true));
								}
	}

}
