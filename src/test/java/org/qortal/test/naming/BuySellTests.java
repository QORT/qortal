package org.qortal.test.naming;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.BuyNameTransactionData;
import org.qortal.data.transaction.CancelSellNameTransactionData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.data.transaction.SellNameTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.RegisterNameTransaction;
import org.qortal.utils.Amounts;
import org.qortal.utils.NTP;

public class BuySellTests extends Common {

	protected static final Random random = new Random();

	private Repository repository;
	private PrivateKeyAccount alice;
	private PrivateKeyAccount bob;

	private String name;
	private Long price;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();

		repository = RepositoryManager.getRepository();
		alice = Common.getTestAccount(repository, "alice");
		bob = Common.getTestAccount(repository, "bob");

		name = "test name" + " " + random.nextInt(1_000_000);
		price = random.nextInt(1000) * Amounts.MULTIPLIER;
	}

	@After
	public void afterTest() throws DataException {
		name = null;
		price = null;

		alice = null;
		bob = null;

		repository = null;

		Common.orphanCheck();
	}

	@Test
	public void testRegisterName() throws DataException {
		// Register-name
		RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "{}");
		transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));;
		TransactionUtils.signAndMint(repository, transactionData, alice);

		String name = transactionData.getName();

		// Check name does exist
		assertTrue(repository.getNameRepository().nameExists(name));

		// Orphan register-name
		BlockUtils.orphanLastBlock(repository);

		// Check name no longer exists
		assertFalse(repository.getNameRepository().nameExists(name));

		// Re-process register-name
		BlockUtils.mintBlock(repository);

		// Check name does exist
		assertTrue(repository.getNameRepository().nameExists(name));
	}

	@Test
	public void testSellName() throws DataException {
		// Register-name
		testRegisterName();

		// Sell-name
		SellNameTransactionData transactionData = new SellNameTransactionData(TestTransaction.generateBase(alice), name, price);
		TransactionUtils.signAndMint(repository, transactionData, alice);

		NameData nameData;

		// Check name is for sale
		nameData = repository.getNameRepository().fromName(name);
		assertTrue(nameData.isForSale());
		assertEquals("price incorrect", price, nameData.getSalePrice());

		// Orphan sell-name
		BlockUtils.orphanLastBlock(repository);

		// Check name no longer for sale
		nameData = repository.getNameRepository().fromName(name);
		assertFalse(nameData.isForSale());
		// Not concerned about price

		// Re-process sell-name
		BlockUtils.mintBlock(repository);

		// Check name is for sale
		nameData = repository.getNameRepository().fromName(name);
		assertTrue(nameData.isForSale());
		assertEquals("price incorrect", price, nameData.getSalePrice());

		// Orphan sell-name and register-name
		BlockUtils.orphanBlocks(repository, 2);

		// Check name no longer exists
		assertFalse(repository.getNameRepository().nameExists(name));
		nameData = repository.getNameRepository().fromName(name);
		assertNull(nameData);

		// Re-process register-name and sell-name
		BlockUtils.mintBlock(repository);
		// Unconfirmed sell-name transaction not included in previous block
		// as it isn't valid until name exists thanks to register-name transaction.
		BlockUtils.mintBlock(repository);

		// Check name does exist
		assertTrue(repository.getNameRepository().nameExists(name));

		// Check name is for sale
		nameData = repository.getNameRepository().fromName(name);
		assertTrue(nameData.isForSale());
		assertEquals("price incorrect", price, nameData.getSalePrice());
	}

	@Test
	public void testCancelSellName() throws DataException {
		// Register-name and sell-name
		testSellName();

		// Cancel Sell-name
		CancelSellNameTransactionData transactionData = new CancelSellNameTransactionData(TestTransaction.generateBase(alice), name);
		TransactionUtils.signAndMint(repository, transactionData, alice);

		NameData nameData;

		// Check name is no longer for sale
		nameData = repository.getNameRepository().fromName(name);
		assertFalse(nameData.isForSale());
		// Not concerned about price

		// Orphan cancel sell-name
		BlockUtils.orphanLastBlock(repository);

		// Check name is for sale
		nameData = repository.getNameRepository().fromName(name);
		assertTrue(nameData.isForSale());
		assertEquals("price incorrect", price, nameData.getSalePrice());
	}

	@Test
	public void testBuyName() throws DataException {
		// Register-name and sell-name
		testSellName();

		String seller = alice.getAddress();

		// Buy-name
		BuyNameTransactionData transactionData = new BuyNameTransactionData(TestTransaction.generateBase(bob), name, price, seller);
		TransactionUtils.signAndMint(repository, transactionData, bob);

		NameData nameData;

		// Check name is sold
		nameData = repository.getNameRepository().fromName(name);
		assertFalse(nameData.isForSale());
		// Not concerned about price

		// Orphan buy-name
		BlockUtils.orphanLastBlock(repository);

		// Check name is for sale (not sold)
		nameData = repository.getNameRepository().fromName(name);
		assertTrue(nameData.isForSale());
		assertEquals("price incorrect", price, nameData.getSalePrice());

		// Re-process buy-name
		BlockUtils.mintBlock(repository);

		// Check name is sold
		nameData = repository.getNameRepository().fromName(name);
		assertFalse(nameData.isForSale());
		// Not concerned about price
		assertEquals(bob.getAddress(), nameData.getOwner());

		// Orphan buy-name and sell-name
		BlockUtils.orphanBlocks(repository, 2);

		// Check name no longer for sale
		nameData = repository.getNameRepository().fromName(name);
		assertFalse(nameData.isForSale());
		// Not concerned about price
		assertEquals(alice.getAddress(), nameData.getOwner());

		// Re-process sell-name and buy-name
		BlockUtils.mintBlock(repository);
		// Unconfirmed buy-name transaction not included in previous block
		// as it isn't valid until name is for sale thanks to sell-name transaction.
		BlockUtils.mintBlock(repository);

		// Check name is sold
		nameData = repository.getNameRepository().fromName(name);
		assertFalse(nameData.isForSale());
		// Not concerned about price
		assertEquals(bob.getAddress(), nameData.getOwner());
	}

	@Test
	public void testSellBuySellName() throws DataException {
		// Register-name, sell-name, buy-name
		testBuyName();

		// Sell-name
		Long newPrice = random.nextInt(1000) * Amounts.MULTIPLIER;
		SellNameTransactionData transactionData = new SellNameTransactionData(TestTransaction.generateBase(bob), name, newPrice);
		TransactionUtils.signAndMint(repository, transactionData, bob);

		NameData nameData;

		// Check name is for sale
		nameData = repository.getNameRepository().fromName(name);
		assertTrue(nameData.isForSale());
		assertEquals("price incorrect", newPrice, nameData.getSalePrice());

		// Orphan sell-name
		BlockUtils.orphanLastBlock(repository);

		// Check name no longer for sale
		nameData = repository.getNameRepository().fromName(name);
		assertFalse(nameData.isForSale());
		// Not concerned about price

		// Re-process sell-name
		BlockUtils.mintBlock(repository);

		// Check name is for sale
		nameData = repository.getNameRepository().fromName(name);
		assertTrue(nameData.isForSale());
		assertEquals("price incorrect", newPrice, nameData.getSalePrice());

		// Orphan sell-name and buy-name
		BlockUtils.orphanBlocks(repository, 2);

		// Check name is for sale
		nameData = repository.getNameRepository().fromName(name);
		assertTrue(nameData.isForSale());
		// Note: original sale price
		assertEquals("price incorrect", price, nameData.getSalePrice());
		assertEquals(alice.getAddress(), nameData.getOwner());

		// Re-process buy-name and sell-name
		BlockUtils.mintBlock(repository);
		// Unconfirmed sell-name transaction not included in previous block
		// as it isn't valid until name owned by bob thanks to buy-name transaction.
		BlockUtils.mintBlock(repository);

		// Check name does exist
		assertTrue(repository.getNameRepository().nameExists(name));

		// Check name is for sale
		nameData = repository.getNameRepository().fromName(name);
		assertTrue(nameData.isForSale());
		assertEquals("price incorrect", newPrice, nameData.getSalePrice());
		assertEquals(bob.getAddress(), nameData.getOwner());
	}

}
