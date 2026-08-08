package org.qortal.test.api;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.api.resource.NamesResource;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.data.transaction.SellNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.ApiCommon;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.RegisterNameTransaction;

import static org.junit.Assert.assertNotNull;

public class NamesApiTests extends ApiCommon {

	private NamesResource namesResource;

	@Before
	public void before() throws DataException {
		Common.useDefaultSettings();

		this.namesResource = (NamesResource) ApiCommon.buildResource(NamesResource.class);
	}

	@Test
	public void testResource() {
		assertNotNull(this.namesResource);
	}

	@Test
	public void testGetAllNames() {
		assertNotNull(this.namesResource.getAllNames(null, null, null, null));
		assertNotNull(this.namesResource.getAllNames(1L, 1, 1, true));
	}

	@Test
	public void testGetNamesByAddress() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";

			RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "{}");
			transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, transactionData, alice);

			assertNotNull(this.namesResource.getNamesByAddress(alice.getAddress(), null, null, null));
			assertNotNull(this.namesResource.getNamesByAddress(alice.getAddress(), 1, 1, true));
		}
	}

	@Test
	public void testGetName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";

			RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "{}");
			transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, transactionData, alice);

			assertNotNull(this.namesResource.getName(name));
		}
	}

	@Test
	public void testGetAllAssets() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "test-name";
			long price = 1_23456789L;

			TransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "{}");
			transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Sell-name
			transactionData = new SellNameTransactionData(TestTransaction.generateBase(alice), name, price);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			assertNotNull(this.namesResource.getNamesForSale(null, null, null));
			assertNotNull(this.namesResource.getNamesForSale(1, 1, true));
		}
	}

}
