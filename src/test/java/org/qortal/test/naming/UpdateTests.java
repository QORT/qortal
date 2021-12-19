package org.qortal.test.naming;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.UpdateNameTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;

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
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check initial name exists
			assertTrue(repository.getNameRepository().nameExists(initialName));
			assertNotNull(repository.getNameRepository().fromReducedName(initialReducedName));

			// Update data
			String middleName = "middle-name";
			String middleReducedName = "midd1e-name";
			String middleData = "middle-data";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, middleName, middleData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check data is correct
			assertEquals(middleData, repository.getNameRepository().fromName(middleName).getData());

			String newestName = "newest-name";
			String newestReducedName = "newest-name";
			String newestData = "newest-data";
			transactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), middleName, newestName, newestData);
			TransactionUtils.signAndMint(repository, transactionData, alice);

			// Check data is correct
			assertEquals(newestData, repository.getNameRepository().fromName(newestName).getData());

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

}
