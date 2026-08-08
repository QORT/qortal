package org.qortal.test.api;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.qortal.api.restricted.resource.AdminResource;
import org.qortal.repository.DataException;
import org.qortal.settings.Settings;
import org.qortal.test.common.ApiCommon;
import org.qortal.test.common.Common;

import static org.junit.Assert.assertNotNull;

public class AdminApiTests extends ApiCommon {

	private AdminResource adminResource;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Before
	public void buildResource() {
		this.adminResource = (AdminResource) ApiCommon.buildResource(AdminResource.class);
	}

	@Test
	public void testInfo() {
		assertNotNull(this.adminResource.info());
	}

	@Test
	public void testSummary() throws IllegalAccessException {
		// Set localAuthBypassEnabled to true, since we don't need to test authentication here
		FieldUtils.writeField(Settings.getInstance(), "localAuthBypassEnabled", true, true);

		assertNotNull(this.adminResource.summary());
	}

	@Test
	public void testGetMintingAccounts() {
		assertNotNull(this.adminResource.getMintingAccounts());
	}

}
