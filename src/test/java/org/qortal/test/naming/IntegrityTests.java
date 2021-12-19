package org.qortal.test.naming;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.controller.repository.NamesDatabaseIntegrityCheck;
import org.qortal.data.transaction.*;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.Transaction;

import static org.junit.Assert.*;

public class IntegrityTests extends Common {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @Test
    public void testValidName() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            // Register-name
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "test-name";
            String data = "{\"age\":30}";

            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the name exists and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(name).getData());

            // Run the database integrity check for this name
            NamesDatabaseIntegrityCheck integrityCheck = new NamesDatabaseIntegrityCheck();
            assertEquals(1, integrityCheck.rebuildName(name, repository));

            // Ensure the name still exists and the data is still correct
            assertEquals(data, repository.getNameRepository().fromName(name).getData());
        }
    }

    @Test
    public void testMissingName() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            // Register-name
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "test-name";
            String data = "{\"age\":30}";

            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the name exists and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(name).getData());

            // Now delete the name, to simulate a database inconsistency
            repository.getNameRepository().delete(name);

            // Ensure the name doesn't exist
            assertNull(repository.getNameRepository().fromName(name));

            // Run the database integrity check for this name and check that a row was modified
            NamesDatabaseIntegrityCheck integrityCheck = new NamesDatabaseIntegrityCheck();
            assertEquals(1, integrityCheck.rebuildName(name, repository));

            // Ensure the name exists again and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(name).getData());
        }
    }

    @Test
    public void testMissingNameAfterUpdate() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            // Register-name
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "test-name";
            String data = "{\"age\":30}";

            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the name exists and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(name).getData());

            // Update the name
            String newData = "{\"age\":31}";
            UpdateNameTransactionData updateTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), name, name, newData);
            TransactionUtils.signAndMint(repository, updateTransactionData, alice);

            // Ensure the name still exists and the data has been updated
            assertEquals(newData, repository.getNameRepository().fromName(name).getData());

            // Now delete the name, to simulate a database inconsistency
            repository.getNameRepository().delete(name);

            // Ensure the name doesn't exist
            assertNull(repository.getNameRepository().fromName(name));

            // Run the database integrity check for this name
            // We expect 2 modifications to be made - the original register name followed by the update
            NamesDatabaseIntegrityCheck integrityCheck = new NamesDatabaseIntegrityCheck();
            assertEquals(2, integrityCheck.rebuildName(name, repository));

            // Ensure the name exists and the data is correct
            assertEquals(newData, repository.getNameRepository().fromName(name).getData());
        }
    }

    @Test
    public void testMissingNameAfterRename() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            // Register-name
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "test-name";
            String data = "{\"age\":30}";

            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the name exists and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(name).getData());

            // Rename the name
            String newName = "new-name";
            String newData = "{\"age\":31}";
            UpdateNameTransactionData updateTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), name, newName, newData);
            TransactionUtils.signAndMint(repository, updateTransactionData, alice);

            // Ensure the new name exists and the data has been updated
            assertEquals(newData, repository.getNameRepository().fromName(newName).getData());

            // Ensure the old name doesn't exist
            assertNull(repository.getNameRepository().fromName(name));

            // Now delete the new name, to simulate a database inconsistency
            repository.getNameRepository().delete(newName);

            // Ensure the new name doesn't exist
            assertNull(repository.getNameRepository().fromName(newName));

            // Attempt to register the new name
            transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), newName, data);
            Transaction transaction = Transaction.fromData(repository, transactionData);
            transaction.sign(alice);

            // Transaction should be invalid, because the database inconsistency was fixed by RegisterNameTransaction.preProcess()
            Transaction.ValidationResult result = transaction.importAsUnconfirmed();
            assertTrue("Transaction should be invalid", Transaction.ValidationResult.OK != result);
            assertTrue("Name should already be registered", Transaction.ValidationResult.NAME_ALREADY_REGISTERED == result);
        }
    }

    @Test
    public void testRegisterMissingName() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            // Register-name
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "test-name";
            String data = "{\"age\":30}";

            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the name exists and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(name).getData());

            // Now delete the name, to simulate a database inconsistency
            repository.getNameRepository().delete(name);

            // Ensure the name doesn't exist
            assertNull(repository.getNameRepository().fromName(name));

            // Attempt to register the name again
            String duplicateName = "TEST-nÁme";
            transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), duplicateName, data);
            Transaction transaction = Transaction.fromData(repository, transactionData);
            transaction.sign(alice);

            // Transaction should be invalid, because the database inconsistency was fixed by RegisterNameTransaction.preProcess()
            Transaction.ValidationResult result = transaction.importAsUnconfirmed();
            assertTrue("Transaction should be invalid", Transaction.ValidationResult.OK != result);
            assertTrue("Name should already be registered", Transaction.ValidationResult.NAME_ALREADY_REGISTERED == result);
        }
    }

    @Test
    public void testUpdateMissingName() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            // Register-name
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String initialName = "test-name";
            String data = "{\"age\":30}";

            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, data);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the name exists and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(initialName).getData());

            // Now delete the name, to simulate a database inconsistency
            repository.getNameRepository().delete(initialName);

            // Ensure the name doesn't exist
            assertNull(repository.getNameRepository().fromName(initialName));

            // Attempt to update the name
            String newName = "new-name";
            String newData = "";
            TransactionData updateTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, newName, newData);
            Transaction transaction = Transaction.fromData(repository, updateTransactionData);
            transaction.sign(alice);

            // Transaction should be valid, because the database inconsistency was fixed by UpdateNameTransaction.preProcess()
            Transaction.ValidationResult result = transaction.importAsUnconfirmed();
            assertTrue("Transaction should be valid", Transaction.ValidationResult.OK == result);
        }
    }

    @Test
    public void testUpdateToMissingName() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            // Register-name
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String initialName = "test-name";
            String data = "{\"age\":30}";

            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), initialName, data);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the name exists and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(initialName).getData());

            // Register the second name that we will ultimately try and rename the first name to
            String secondName = "new-missing-name";
            String secondNameData = "{\"data2\":true}";
            transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), secondName, secondNameData);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the second name exists and the data is correct
            assertEquals(secondNameData, repository.getNameRepository().fromName(secondName).getData());

            // Now delete the second name, to simulate a database inconsistency
            repository.getNameRepository().delete(secondName);

            // Ensure the second name doesn't exist
            assertNull(repository.getNameRepository().fromName(secondName));

            // Attempt to rename the first name to the second name
            TransactionData updateTransactionData = new UpdateNameTransactionData(TestTransaction.generateBase(alice), initialName, secondName, secondNameData);
            Transaction transaction = Transaction.fromData(repository, updateTransactionData);
            transaction.sign(alice);

            // Transaction should be invalid, because the database inconsistency was fixed by UpdateNameTransaction.preProcess()
            // Therefore the name that we are trying to rename TO already exists
            Transaction.ValidationResult result = transaction.importAsUnconfirmed();
            assertTrue("Transaction should be invalid", Transaction.ValidationResult.OK != result);
            assertTrue("Destination name should already exist", Transaction.ValidationResult.NAME_ALREADY_REGISTERED == result);
        }
    }

    @Test
    public void testSellMissingName() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            // Register-name
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "test-name";
            String data = "{\"age\":30}";

            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the name exists and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(name).getData());

            // Now delete the name, to simulate a database inconsistency
            repository.getNameRepository().delete(name);

            // Ensure the name doesn't exist
            assertNull(repository.getNameRepository().fromName(name));

            // Attempt to sell the name
            TransactionData sellTransactionData = new SellNameTransactionData(TestTransaction.generateBase(alice), name, 123456);
            Transaction transaction = Transaction.fromData(repository, sellTransactionData);
            transaction.sign(alice);

            // Transaction should be valid, because the database inconsistency was fixed by SellNameTransaction.preProcess()
            Transaction.ValidationResult result = transaction.importAsUnconfirmed();
            assertTrue("Transaction should be valid", Transaction.ValidationResult.OK == result);
        }
    }

    @Test
    public void testBuyMissingName() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            // Register-name
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "test-name";
            String data = "{\"age\":30}";

            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, data);
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Ensure the name exists and the data is correct
            assertEquals(data, repository.getNameRepository().fromName(name).getData());

            // Now delete the name, to simulate a database inconsistency
            repository.getNameRepository().delete(name);

            // Ensure the name doesn't exist
            assertNull(repository.getNameRepository().fromName(name));

            // Attempt to sell the name
            long amount = 123456;
            TransactionData sellTransactionData = new SellNameTransactionData(TestTransaction.generateBase(alice), name, amount);
            TransactionUtils.signAndMint(repository, sellTransactionData, alice);

            // Ensure the name now exists
            assertNotNull(repository.getNameRepository().fromName(name));

            // Now delete the name again, to simulate another database inconsistency
            repository.getNameRepository().delete(name);

            // Ensure the name doesn't exist
            assertNull(repository.getNameRepository().fromName(name));

            // Bob now attempts to buy the name
            String seller = alice.getAddress();
            PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
            TransactionData buyTransactionData = new BuyNameTransactionData(TestTransaction.generateBase(bob), name, amount, seller);
            Transaction transaction = Transaction.fromData(repository, buyTransactionData);
            transaction.sign(bob);

            // Transaction should be valid, because the database inconsistency was fixed by SellNameTransaction.preProcess()
            Transaction.ValidationResult result = transaction.importAsUnconfirmed();
            assertTrue("Transaction should be valid", Transaction.ValidationResult.OK == result);
        }
    }

}
