package org.qortal.test.naming;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.api.resource.TransactionsResource;
import org.qortal.block.BlockChain;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.BuyNameTransactionData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.data.transaction.SellNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.UpdateNameTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.RegisterNameTransaction;
import org.qortal.transaction.Transaction;

import java.util.Optional;

import static org.junit.Assert.*;

public class UpdateTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testUpdateName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String initialName = "initial-name";
			String initialReducedName = "initia1-name";
			String initialData = "{\"age\":30}";

			TransactionData initialTransactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, initialData);
			initialTransactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(initialTransactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, initialTransactionData, alice);

			// Check name, reduced name, and data exist
			assertTrue(repository.getNameRepository().nameExists(initialName));
			NameData nameData = repository.getNameRepository().fromName(initialName);
			assertEquals("initia1-name", nameData.getReducedName());
			assertEquals(initialData, nameData.getData());
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			String newName = "new-name";
			String newReducedName = "new-name";
			String newData = "";
			TransactionData updateTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, newName, newData);
			TransactionUtils.signAndMint(repository, updateTransactionData, alice);

			// Check old name no longer exists
			assertFalse(repository.getNameRepository().nameExists(initialName));

			// Check reduced name no longer exists
			assertNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check new name exists
			assertTrue(repository.getNameRepository().nameExists(newName));

			// Check reduced name and data are correct for new name
			NameData newNameData = repository.getNameRepository().fromName(newReducedName);
			assertEquals(newReducedName, newNameData.getReducedName());
			// Data should remain the same because it was empty in the UpdateNameTransactionData
			assertEquals(initialData, newNameData.getData());

			// Check updated timestamp is correct
			assertEquals((Long) updateTransactionData.getTimestamp(), repository.getNameRepository().fromName(newName).getUpdated());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check new name and reduced name no longer exist
			assertFalse(repository.getNameRepository().nameExists(newName));
			assertNull(repository.getNameRepository().fromReducedName(newReducedName));

			// Check old name and reduced name exist again
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check data and reduced name are still present for this name
			assertTrue(repository.getNameRepository().nameExists(initialName));
			nameData = repository.getNameRepository().fromName(initialName);
			assertEquals(initialReducedName, nameData.getReducedName());
			assertEquals(initialData, nameData.getData());

			// Check updated timestamp is empty
			assertNull(repository.getNameRepository().fromName(initialName).getUpdated());
		}
	}

	@Test
	public void testUpdateNameSameOwner() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String initialName = "initial-name";
			String initialData = "{\"age\":30}";

			String constantReducedName = "initia1-name";

			TransactionData initialTransactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, initialData);
			initialTransactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(initialTransactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, initialTransactionData, alice);

			// Check initial name exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(constantReducedName));

			String newName = "Initial-Name";
			String newData = "";
			TransactionData updateTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, newName, newData);
			TransactionUtils.signAndMint(repository, updateTransactionData, alice);

			// Check old name no longer exists
			assertFalse(repository.getNameRepository().nameExists(initialName));

			// Check new name exists
			assertTrue(repository.getNameRepository().nameExists(newName));
			assertNotNull(repository.getNameRepository().fromReducedName(constantReducedName));

			// Check updated timestamp is correct
			assertEquals((Long) updateTransactionData.getTimestamp(), repository.getNameRepository().fromName(newName).getUpdated());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check new name no longer exists
			assertFalse(repository.getNameRepository().nameExists(newName));

			// Check old name exists again
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(constantReducedName));

			// Check updated timestamp is empty
			assertNull(repository.getNameRepository().fromName(initialName).getUpdated());
		}
	}

	// Test that reverting using previous UPDATE_NAME works as expected
	@Test
	public void testDoubleUpdateName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String initialName = "initial-name";
			String initialReducedName = "initia1-name";
			String initialData = "{\"age\":30}";

			TransactionData initialTransactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, initialData);
			initialTransactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(initialTransactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, initialTransactionData, alice);

			// Check initial name exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			String middleName = "middle-name";
			String middleReducedName = "midd1e-name";
			String middleData = "";
			TransactionData middleTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, middleName, middleData);
			TransactionUtils.signAndMint(repository, middleTransactionData, alice);

			// Check old name no longer exists
			assertFalse(repository.getNameRepository().nameExists(initialName));
			assertNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check new name exists
			assertTrue(repository.getNameRepository().nameExists(middleName));
			assertNotNull(repository.getNameRepository().fromReducedName(middleReducedName));

			String newestName = "newest-name";
			String newestReducedName = "newest-name";
			String newestData = "newest-data";
			TransactionData newestTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), middleName, newestName, newestData);
			TransactionUtils.signAndMint(repository, newestTransactionData, alice);

			// Check previous name no longer exists
			assertFalse(repository.getNameRepository().nameExists(middleName));
			assertNull(repository.getNameRepository().fromReducedName(middleReducedName));

			// Check newest name exists
			assertTrue(repository.getNameRepository().nameExists(newestName));
			assertNotNull(repository.getNameRepository().fromReducedName(newestReducedName));

			// Check updated timestamp is correct
			assertEquals((Long) newestTransactionData.getTimestamp(), repository.getNameRepository().fromName(newestName).getUpdated());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check newest name no longer exists
			assertFalse(repository.getNameRepository().nameExists(newestName));
			assertNull(repository.getNameRepository().fromReducedName(newestReducedName));

			// Check previous name exists again
			assertTrue(repository.getNameRepository().nameExists(middleName));
			assertNotNull(repository.getNameRepository().fromReducedName(middleReducedName));

			// Check updated timestamp is correct
			assertEquals((Long) middleTransactionData.getTimestamp(), repository.getNameRepository().fromName(middleName).getUpdated());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check new name no longer exists
			assertFalse(repository.getNameRepository().nameExists(middleName));
			assertNull(repository.getNameRepository().fromReducedName(middleReducedName));

			// Check original name exists again
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check updated timestamp is empty
			assertNull(repository.getNameRepository().fromName(initialName).getUpdated());
		}
	}

	// Test that multiple UPDATE_NAME transactions work as expected, when using a matching name and newName string
	@Test
	public void testDoubleUpdateNameWithMatchingNewName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String name = "name";
			String reducedName = "name";
			String data = "{\"age\":30}";

			TransactionData initialTransactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
			initialTransactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(initialTransactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, initialTransactionData, alice);

			// Check name exists
			assertTrue(repository.getNameRepository().nameExists(name));
			assertNotNull(repository.getNameRepository().fromReducedName(reducedName));

			// Update name
			TransactionData middleTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), name, name, data);
			TransactionUtils.signAndMint(repository, middleTransactionData, alice);

			// Check name still exists
			assertTrue(repository.getNameRepository().nameExists(name));
			assertNotNull(repository.getNameRepository().fromReducedName(reducedName));

			// Update name again
			TransactionData newestTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), name, name, data);
			TransactionUtils.signAndMint(repository, newestTransactionData, alice);

			// Check name still exists
			assertTrue(repository.getNameRepository().nameExists(name));
			assertNotNull(repository.getNameRepository().fromReducedName(reducedName));

			// Check updated timestamp is correct
			assertEquals((Long) newestTransactionData.getTimestamp(), repository.getNameRepository().fromName(name).getUpdated());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check name still exists
			assertTrue(repository.getNameRepository().nameExists(name));
			assertNotNull(repository.getNameRepository().fromReducedName(reducedName));

			// Check updated timestamp is correct
			assertEquals((Long) middleTransactionData.getTimestamp(), repository.getNameRepository().fromName(name).getUpdated());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check name still exists
			assertTrue(repository.getNameRepository().nameExists(name));
			assertNotNull(repository.getNameRepository().fromReducedName(reducedName));

			// Check updated timestamp is empty
			assertNull(repository.getNameRepository().fromName(name).getUpdated());
		}
	}

	// Test that reverting using previous UPDATE_NAME works as expected
	@Test
	public void testIntermediateUpdateName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String initialName = "initial-name";
			String initialReducedName = "initia1-name";
			String initialData = "{\"age\":30}";

			TransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, initialData);
			transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check initial name exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Don't update name, but update data.
			// This tests whether reverting a future update/sale can find the correct previous name
			String middleName = "";
			String middleData = "middle-data";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, middleName, middleData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check old name still exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			String newestName = "newest-name";
			String newestReducedName = "newest-name";
			String newestData = "newest-data";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, newestName, newestData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check previous name no longer exists
			assertFalse(repository.getNameRepository().nameExists(initialName));
			assertNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check newest name exists
			assertTrue(repository.getNameRepository().nameExists(newestName));
			assertNotNull(repository.getNameRepository().fromReducedName(newestReducedName));

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check original name exists again
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check original name still exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));
		}
	}

	@Test
	public void testUpdateData() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String initialName = "initial-name";
			String initialReducedName = "initia1-name";
			String initialData = "{\"age\":30}";

			TransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, initialData);
			transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check initial name exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			String newName = "";
			String newData = "new-data";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, newName, newData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check name still exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check data is correct
			assertEquals(newData, repository.getNameRepository().fromName(initialName).getData());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check name still exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check old data restored
			assertEquals(initialData, repository.getNameRepository().fromName(initialName).getData());
		}
	}

	// Test that reverting using previous UPDATE_NAME works as expected
	@Test
	public void testDoubleUpdateData() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String initialName = "initial-name";
			String initialReducedName = "initia1-name";
			String initialData = "{\"age\":30}";

			TransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, initialData);
			transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check initial name exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// move passed primary initiation
			BlockUtils.mintBlocks(repository, BlockChain.getInstance().getMultipleNamesPerAccountHeight());

			// check primary name
			assertTrue(alice.getPrimaryName().isPresent());
			assertEquals(initialName, alice.getPrimaryName().get());

			// Update data
			String middleName = "middle-name";
			String middleReducedName = "midd1e-name";
			String middleData = "middle-data";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, middleName, middleData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// check primary name
			Optional<String> alicePrimaryName1 = alice.getPrimaryName();
			assertTrue(alicePrimaryName1.isPresent());
			assertEquals(middleName, alicePrimaryName1.get());

			// Check data is correct
			assertEquals(middleData, repository.getNameRepository().fromName(middleName).getData());

			String newestName = "newest-name";
			String newestReducedName = "newest-name";
			String newestData = "newest-data";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), middleName, newestName, newestData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check data is correct
			assertEquals(newestData, repository.getNameRepository().fromName(newestName).getData());

			// check primary name
			Optional<String> alicePrimaryName2 = alice.getPrimaryName();
			assertTrue(alicePrimaryName2.isPresent());
			assertEquals(newestName, alicePrimaryName2.get());

			// Check initial name no longer exists
			assertFalse(repository.getNameRepository().nameExists(initialName));
			assertNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check newest name exists
			assertTrue(repository.getNameRepository().nameExists(newestName));
			assertNotNull(repository.getNameRepository().fromReducedName(newestReducedName));

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check data is correct
			assertEquals(middleData, repository.getNameRepository().fromName(middleName).getData());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check data is correct
			assertEquals(initialData, repository.getNameRepository().fromName(initialName).getData());

			// Check initial name exists again
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));
		}
	}

	// Test that reverting using previous UPDATE_NAME works as expected
	@Test
	public void testIntermediateUpdateData() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Register-name
			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			String initialName = "initial-name";
			String initialReducedName = "initia1-name";
			String initialData = "{\"age\":30}";

			TransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, initialData);
			transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check initial name exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Don't update data, but update name.
			// This tests whether reverting a future update/sale can find the correct previous data
			String middleName = "middle-name";
			String middleReducedName = "midd1e-name";
			String middleData = "";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, middleName, middleData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check original name no longer exists
			assertFalse(repository.getNameRepository().nameExists(initialName));
			assertNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check middle name exists
			assertTrue(repository.getNameRepository().nameExists(middleName));
			assertNotNull(repository.getNameRepository().fromReducedName(middleReducedName));

			// Check data is correct
			assertEquals(initialData, repository.getNameRepository().fromName(middleName).getData());

			String newestName = "newest-name";
			String newestReducedName = "newest-name";
			String newestData = "newest-data";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), middleName, newestName, newestData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check middle name no longer exists
			assertFalse(repository.getNameRepository().nameExists(middleName));
			assertNull(repository.getNameRepository().fromReducedName(middleReducedName));

			// Check newest name exists
			assertTrue(repository.getNameRepository().nameExists(newestName));
			assertNotNull(repository.getNameRepository().fromReducedName(newestReducedName));

			// Check data is correct
			assertEquals(newestData, repository.getNameRepository().fromName(newestName).getData());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check middle name exists
			assertTrue(repository.getNameRepository().nameExists(middleName));
			assertNotNull(repository.getNameRepository().fromReducedName(middleReducedName));

			// Check data is correct
			assertEquals(initialData, repository.getNameRepository().fromName(middleName).getData());

			// orphan and recheck
			BlockUtils.orphanLastBlock(repository);

			// Check initial name exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Check data is correct
			assertEquals(initialData, repository.getNameRepository().fromName(initialName).getData());
		}
	}

	@Test
	public void testUpdatePrimaryName() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// mint passed the feature trigger block
			BlockUtils.mintBlocks(repository, BlockChain.getInstance().getMultipleNamesPerAccountHeight());

			PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");

			// register name 1
			String initialName = "initial-name";
			RegisterNameTransactionData registerNameTransactionData1 = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, "{}");
			registerNameTransactionData1.setFee(new RegisterNameTransaction(null, null).getUnitFee(registerNameTransactionData1.getTimestamp()));
			TransactionUtils.signAndMint(repository, registerNameTransactionData1, alice);

			// assert name 1 registration, assert primary name
			assertTrue(repository.getNameRepository().nameExists(initialName));

			Optional<String> primaryNameOptional = alice.getPrimaryName();
			assertTrue(primaryNameOptional.isPresent());
			assertEquals(initialName, primaryNameOptional.get());

			// register name 2
			String secondName = "second-name";
			RegisterNameTransactionData registerNameTransactionData2 = new RegisterNameTransactionData(TestTransaction.generateBase(alice), secondName, "{}");
			registerNameTransactionData2.setFee(new RegisterNameTransaction(null, null).getUnitFee(registerNameTransactionData2.getTimestamp()));
			TransactionUtils.signAndMint(repository, registerNameTransactionData2, alice);

			// assert name 2 registration, assert primary has not changed
			assertTrue(repository.getNameRepository().nameExists(secondName));

			// the name alice is trying to update to
			String newName = "updated-name";

			// update name, assert invalid
			updateName(repository, initialName, newName, Transaction.ValidationResult.NOT_SUPPORTED, alice);

			// check primary name did not update
			// check primary name update
			Optional<String> primaryNameNotUpdateOptional = alice.getPrimaryName();
			assertTrue(primaryNameNotUpdateOptional.isPresent());
			assertEquals(initialName, primaryNameNotUpdateOptional.get());

			// sell name 2, assert valid
			Long amount = 1000000L;
			SellNameTransactionData transactionData = new SellNameTransactionData(TestTransaction.generateBase(alice), secondName, amount);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check name is for sale
			NameData nameData = repository.getNameRepository().fromName(secondName);
			assertTrue(nameData.isForSale());
			assertEquals("price incorrect", amount, nameData.getSalePrice());

			// bob buys name 2, assert
			BuyNameTransactionData bobBuysName2Data = new BuyNameTransactionData(TestTransaction.generateBase(bob), secondName, amount, alice.getAddress());
			TransactionUtils.signAndMint(repository, bobBuysName2Data, bob);

			// update name, assert valid, assert primary name change
			updateName(repository, initialName, newName, Transaction.ValidationResult.OK, alice);

			// check primary name update
			Optional<String> primaryNameUpdateOptional = alice.getPrimaryName();
			assertTrue(primaryNameUpdateOptional.isPresent());
			assertEquals(newName, primaryNameUpdateOptional.get());

			assertEquals(alice.getPrimaryName(), alice.determinePrimaryName(TransactionsResource.ConfirmationStatus.CONFIRMED));
			assertEquals(bob.getPrimaryName(), bob.determinePrimaryName(TransactionsResource.ConfirmationStatus.CONFIRMED));
		}
	}

	/**
	 * Update Name
	 *
	 * @param repository
	 * @param initialName the name before the update
	 * @param newName the name after the update
	 * @param expectedValidationResult the validation result expected from the update
	 * @param account the account for the update
	 *
	 * @throws DataException
	 */
	private static void updateName(Repository repository, String initialName, String newName, Transaction.ValidationResult expectedValidationResult, PrivateKeyAccount account) throws DataException {
		TransactionData data = new UpdateNameTransactionData(TestTransaction.generateBase(account), initialName, newName, "{}");
		Transaction.ValidationResult result = TransactionUtils.signAndImport(repository,data, account);

		assertEquals("Transaction invalid", expectedValidationResult, result);

		BlockUtils.mintBlock(repository);

		if( Transaction.ValidationResult.OK.equals(expectedValidationResult) ) {
			assertTrue(repository.getNameRepository().nameExists(newName));
		}
		else {
			// the new name should not exist, because the update was invalid
			assertFalse(repository.getNameRepository().nameExists(newName));
		}
	}
}
