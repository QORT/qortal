package org.qortal.test.api;

import static org.junit.Assert.*;

import java.util.Collections;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.qortal.api.resource.AddressesResource;
import org.qortal.test.common.ApiCommon;

public class AddressesApiTests extends ApiCommon {

	private AddressesResource addressesResource;

	@Before
	public void buildResource() {
		this.addressesResource = (AddressesResource) ApiCommon.buildResource(AddressesResource.class);
	}

	@Test
	public void testGetAccountInfo() {
		assertNotNull(this.addressesResource.getAccountInfo(aliceAddress));
	}

	@Test
	@Ignore(value = "Doesn't work, to be fixed later")
	public void testGetOnlineAccounts() {
		assertNotNull(this.addressesResource.getOnlineAccounts());
	}

	@Test
	public void testGetRewardShares() {
		assertNotNull(this.addressesResource.getRewardShares(Collections.singletonList(aliceAddress), null, null, null, null, null));
		assertNotNull(this.addressesResource.getRewardShares(null, Collections.singletonList(aliceAddress), null, null, null, null));
		assertNotNull(this.addressesResource.getRewardShares(Collections.singletonList(aliceAddress), Collections.singletonList(aliceAddress), null, null, null, null));
		assertNotNull(this.addressesResource.getRewardShares(null, null, Collections.singletonList(aliceAddress), null, null, null));
		assertNotNull(this.addressesResource.getRewardShares(Collections.singletonList(aliceAddress), Collections.singletonList(aliceAddress), Collections.singletonList(aliceAddress), null, null, null));
		assertNotNull(this.addressesResource.getRewardShares(Collections.singletonList(aliceAddress), Collections.singletonList(aliceAddress), Collections.singletonList(aliceAddress), 1, 1, true));
	}

}
