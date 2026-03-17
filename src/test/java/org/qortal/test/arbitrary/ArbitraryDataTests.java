package org.qortal.test.arbitrary;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.arbitrary.ArbitraryDataDigest;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.arbitrary.ArbitraryDataFile.ResourceIdType;
import org.qortal.arbitrary.ArbitraryDataReader;
import org.qortal.arbitrary.exception.MissingDataException;
import org.qortal.arbitrary.metadata.ArbitraryDataMetadataPatch;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.arbitrary.ArbitraryDataManager;
import org.qortal.crypto.Crypto;
import org.qortal.data.transaction.ArbitraryTransactionData.Method;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.test.common.ArbitraryUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.RegisterNameTransaction;
import org.qortal.utils.Base58;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.Assert.*;

public class ArbitraryDataTests extends Common {

    @Before
    public void beforeTest() throws DataException, IllegalAccessException {
        Common.useDefaultSettings();

        // Set difficulty to 1 to speed up the tests
        FieldUtils.writeField(ArbitraryDataManager.getInstance(), "powDifficulty", 1, true);
    }

    @Test
    public void testCombineMultipleLayers() throws DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1");
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PUT, service, alice);

            // Create PATCH transaction
            Path path2 = Paths.get("src/test/resources/arbitrary/demo2");
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path2, name, identifier, Method.PATCH, service, alice);

            // Create another PATCH transaction
            Path path3 = Paths.get("src/test/resources/arbitrary/demo3");
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path3, name, identifier, Method.PATCH, service, alice);

            // Now build the latest data state for this name
            ArbitraryDataReader arbitraryDataReader = new ArbitraryDataReader(name, ResourceIdType.NAME, service, identifier);
            arbitraryDataReader.loadSynchronously(true);
            Path finalPath = arbitraryDataReader.getFilePath();

            // Ensure it exists
            assertTrue(Files.exists(finalPath));

            // Its directory hash should match the hash of demo3
            ArbitraryDataDigest path3Digest = new ArbitraryDataDigest(path3);
            path3Digest.compute();
            ArbitraryDataDigest finalPathDigest = new ArbitraryDataDigest(finalPath);
            finalPathDigest.compute();
            assertEquals(path3Digest.getHash58(), finalPathDigest.getHash58());

            // .. and its directory hash should also match the one included in the metadata
            ArbitraryDataMetadataPatch patchMetadata = new ArbitraryDataMetadataPatch(finalPath);
            patchMetadata.read();
            assertArrayEquals(patchMetadata.getCurrentHash(), path3Digest.getHash());

        }
    }

    @Test
    public void testPatchBeforePut() throws DataException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;

            // Create PATCH transaction, ensuring that an exception is thrown
            try {
                Path path1 = Paths.get("src/test/resources/arbitrary/demo1");
                ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PATCH, service, alice);
                fail("Creating transaction should fail due to nonexistent PUT transaction");

            } catch (DataException expectedException) {
                assertTrue(expectedException.getMessage().contains(String.format("Couldn't find PUT transaction for " +
                        "name %s, service %s and identifier ", name, service)));
            }

        }
    }

    @Test
    public void testNameDoesNotExist() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;

            // Ensure the name doesn't exist
            assertNull(repository.getNameRepository().fromName(name));

            // Create PUT transaction, ensuring that an exception is thrown
            try {
                Path path1 = Paths.get("src/test/resources/arbitrary/demo1");
                ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PUT, service, alice);
                fail("Creating transaction should fail due to the name being unregistered");

            } catch (DataException expectedException) {
                assertEquals("Arbitrary transaction invalid: NAME_DOES_NOT_EXIST", expectedException.getMessage());
            }
        }
    }

    @Test
    public void testUpdateResourceOwnedByAnotherCreator() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String name = "TEST"; // Can be anything for this test
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1");
            ArbitraryUtils.createAndMintTxn(repository, Base58.encode(alice.getPublicKey()), path1, name, identifier, Method.PUT, service, alice);

            // Bob attempts to update Alice's data
            PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");

            // Create PATCH transaction, ensuring that an exception is thrown
            try {
                Path path2 = Paths.get("src/test/resources/arbitrary/demo2");
                ArbitraryUtils.createAndMintTxn(repository, Base58.encode(bob.getPublicKey()), path2, name, identifier, Method.PATCH, service, bob);
                fail("Creating transaction should fail due to the name being registered to Alice instead of Bob");

            } catch (DataException expectedException) {
                assertEquals("Arbitrary transaction invalid: INVALID_NAME_OWNER", expectedException.getMessage());
            }
        }
    }

    @Test
    public void testUpdateResource() throws DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1");
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PUT, service, alice);

            // Now build the latest data state for this name
            ArbitraryDataReader arbitraryDataReader1 = new ArbitraryDataReader(name, ResourceIdType.NAME, service, identifier);
            arbitraryDataReader1.loadSynchronously(true);
            Path initialLayerPath = arbitraryDataReader1.getFilePath();
            ArbitraryDataDigest initialLayerDigest = new ArbitraryDataDigest(initialLayerPath);
            initialLayerDigest.compute();

            // Create PATCH transaction
            Path path2 = Paths.get("src/test/resources/arbitrary/demo2");
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path2, name, identifier, Method.PATCH, service, alice);

            // Rebuild the latest state
            ArbitraryDataReader arbitraryDataReader2 = new ArbitraryDataReader(name, ResourceIdType.NAME, service, identifier);
            arbitraryDataReader2.loadSynchronously(false);
            Path secondLayerPath = arbitraryDataReader2.getFilePath();
            ArbitraryDataDigest secondLayerDigest = new ArbitraryDataDigest(secondLayerPath);
            secondLayerDigest.compute();

            // Ensure that the second state is different to the first state
            assertFalse(Arrays.equals(initialLayerDigest.getHash(), secondLayerDigest.getHash()));

            // Its directory hash should match the hash of demo2
            ArbitraryDataDigest path2Digest = new ArbitraryDataDigest(path2);
            path2Digest.compute();
            assertEquals(path2Digest.getHash58(), secondLayerDigest.getHash58());
        }
    }

    @Test
    public void testIdentifier() throws DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = "test_identifier";
            Service service = Service.ARBITRARY_DATA;

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1");
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PUT, service, alice);

            // Build the latest data state for this name, with a null identifier, ensuring that it fails
            ArbitraryDataReader arbitraryDataReader1a = new ArbitraryDataReader(name, ResourceIdType.NAME, service, null);
            try {
                arbitraryDataReader1a.loadSynchronously(true);
                fail("Loading data with null identifier should fail due to nonexistent PUT transaction");

            } catch (DataException expectedException) {
                assertEquals(String.format("Couldn't find PUT transaction for name %s, service %s "
                        + "and identifier ", name.toLowerCase(), service), expectedException.getMessage());
            }

            // Build the latest data state for this name, with a different identifier, ensuring that it fails
            String differentIdentifier = "different_identifier";
            ArbitraryDataReader arbitraryDataReader1b = new ArbitraryDataReader(name, ResourceIdType.NAME, service, differentIdentifier);
            try {
                arbitraryDataReader1b.loadSynchronously(true);
                fail("Loading data with incorrect identifier should fail due to nonexistent PUT transaction");

            } catch (DataException expectedException) {
                assertEquals(String.format("Couldn't find PUT transaction for name %s, service %s "
                        + "and identifier %s", name.toLowerCase(), service, differentIdentifier), expectedException.getMessage());
            }

            // Now build the latest data state for this name, with the correct identifier
            ArbitraryDataReader arbitraryDataReader1c = new ArbitraryDataReader(name, ResourceIdType.NAME, service, identifier);
            arbitraryDataReader1c.loadSynchronously(true);
            Path initialLayerPath = arbitraryDataReader1c.getFilePath();
            ArbitraryDataDigest initialLayerDigest = new ArbitraryDataDigest(initialLayerPath);
            initialLayerDigest.compute();

            // Create PATCH transaction
            Path path2 = Paths.get("src/test/resources/arbitrary/demo2");
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path2, name, identifier, Method.PATCH, service, alice);

            // Rebuild the latest state
            ArbitraryDataReader arbitraryDataReader2 = new ArbitraryDataReader(name, ResourceIdType.NAME, service, identifier);
            arbitraryDataReader2.loadSynchronously(false);
            Path secondLayerPath = arbitraryDataReader2.getFilePath();
            ArbitraryDataDigest secondLayerDigest = new ArbitraryDataDigest(secondLayerPath);
            secondLayerDigest.compute();

            // Ensure that the second state is different to the first state
            assertFalse(Arrays.equals(initialLayerDigest.getHash(), secondLayerDigest.getHash()));

            // Its directory hash should match the hash of demo2
            ArbitraryDataDigest path2Digest = new ArbitraryDataDigest(path2);
            path2Digest.compute();
            assertEquals(path2Digest.getHash58(), secondLayerDigest.getHash58());
        }
    }

    @Test
    public void testBlankIdentifier() throws DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = ""; // Blank, not null
            Service service = Service.ARBITRARY_DATA;

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1");
            ArbitraryDataDigest path1Digest = new ArbitraryDataDigest(path1);
            path1Digest.compute();
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PUT, service, alice);

            // Now build the latest data state for this name with a null identifier, ensuring that it succeeds and the data matches
            ArbitraryDataReader arbitraryDataReader1a = new ArbitraryDataReader(name, ResourceIdType.NAME, service, null);
            arbitraryDataReader1a.loadSynchronously(true);
            Path initialLayerPath1a = arbitraryDataReader1a.getFilePath();
            ArbitraryDataDigest initialLayerDigest1a = new ArbitraryDataDigest(initialLayerPath1a);
            initialLayerDigest1a.compute();
            assertEquals(path1Digest.getHash58(), initialLayerDigest1a.getHash58());

            // It should also be accessible via a blank string, as we treat null and blank as the same thing
            ArbitraryDataReader arbitraryDataReader1b = new ArbitraryDataReader(name, ResourceIdType.NAME, service, "");
            arbitraryDataReader1b.loadSynchronously(true);
            Path initialLayerPath1b = arbitraryDataReader1b.getFilePath();
            ArbitraryDataDigest initialLayerDigest1b = new ArbitraryDataDigest(initialLayerPath1b);
            initialLayerDigest1b.compute();
            assertEquals(path1Digest.getHash58(), initialLayerDigest1b.getHash58());

            // Build the latest data state for this name, with a different identifier, ensuring that it fails
            String differentIdentifier = "different_identifier";
            ArbitraryDataReader arbitraryDataReader1c = new ArbitraryDataReader(name, ResourceIdType.NAME, service, differentIdentifier);
            try {
                arbitraryDataReader1c.loadSynchronously(true);
                fail("Loading data with incorrect identifier should fail due to nonexistent PUT transaction");

            } catch (DataException expectedException) {
                assertEquals(String.format("Couldn't find PUT transaction for name %s, service %s "
                        + "and identifier %s", name.toLowerCase(), service, differentIdentifier), expectedException.getMessage());
            }
        }
    }

    @Test
    public void testSingleFile() throws DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = "test1"; // Blank, not null
            Service service = Service.DOCUMENT; // Can be anything for this test

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1/lorem1.txt");
            byte[] path1FileDigest = Crypto.digest(path1.toFile());
            ArbitraryDataDigest path1DirectoryDigest = new ArbitraryDataDigest(path1.getParent());
            path1DirectoryDigest.compute();
            ArbitraryDataFile arbitraryDataFile = ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PUT, service, alice);

            // Now build the latest data state for this name
            ArbitraryDataReader arbitraryDataReader1 = new ArbitraryDataReader(name, ResourceIdType.NAME, service, identifier);
            arbitraryDataReader1.loadSynchronously(true);
            Path builtFilePath = Paths.get(arbitraryDataReader1.getFilePath().toString(), path1.getFileName().toString());
            byte[] builtFileDigest = Crypto.digest(builtFilePath.toFile());

            // Compare it against the hash of the original file
            assertArrayEquals(builtFileDigest, path1FileDigest);

            // The directory digest won't match because the file is renamed to "data"
            // We may need to find a way to retain the filename
            ArbitraryDataDigest builtDirectoryDigest = new ArbitraryDataDigest(arbitraryDataReader1.getFilePath());
            builtDirectoryDigest.compute();
            assertFalse(Objects.equals(path1DirectoryDigest.getHash58(), builtDirectoryDigest.getHash58()));
        }
    }

    @Test
    public void testOriginalCopyIndicatorFile() throws DataException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = "test1"; // Blank, not null
            Service service = Service.DOCUMENT; // Can be anything for this test

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1/lorem1.txt");
            ArbitraryDataDigest path1DirectoryDigest = new ArbitraryDataDigest(path1.getParent());
            path1DirectoryDigest.compute();
            ArbitraryDataFile arbitraryDataFile = ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PUT, service, alice);

            // Ensure that an ".original" file exists
            Path parentPath = arbitraryDataFile.getFilePath().getParent();
            Path originalCopyIndicatorFile = Paths.get(parentPath.toString(), ".original");
            assertTrue(Files.exists(originalCopyIndicatorFile));
        }
    }

    @Test
    public void testOriginalCopyIndicatorFileDisabled() throws DataException, IOException, IllegalAccessException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = "test1"; // Blank, not null
            Service service = Service.DOCUMENT; // Can be anything for this test

            // Set originalCopyIndicatorFileEnabled to false
            FieldUtils.writeField(Settings.getInstance(), "originalCopyIndicatorFileEnabled", false, true);

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1/lorem1.txt");
            ArbitraryDataDigest path1DirectoryDigest = new ArbitraryDataDigest(path1.getParent());
            path1DirectoryDigest.compute();
            ArbitraryDataFile arbitraryDataFile = ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PUT, service, alice);

            // Ensure that an ".original" file exists
            Path parentPath = arbitraryDataFile.getFilePath().getParent();
            Path originalCopyIndicatorFile = Paths.get(parentPath.toString(), ".original");
            assertFalse(Files.exists(originalCopyIndicatorFile));
        }
    }

    @Test
    public void testNameWithSpace() throws DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "Test Name";
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;

            // Register the name to Alice
            RegisterNameTransactionData transactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            transactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(transactionData.getTimestamp()));
            TransactionUtils.signAndMint(repository, transactionData, alice);

            // Create PUT transaction
            Path path1 = Paths.get("src/test/resources/arbitrary/demo1");
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, Method.PUT, service, alice);

            // Create PATCH transaction
            Path path2 = Paths.get("src/test/resources/arbitrary/demo2");
            ArbitraryUtils.createAndMintTxn(repository, publicKey58, path2, name, identifier, Method.PATCH, service, alice);

            // Now build the latest data state for this name
            ArbitraryDataReader arbitraryDataReader = new ArbitraryDataReader(name, ResourceIdType.NAME, service, identifier);
            arbitraryDataReader.loadSynchronously(true);
            Path finalPath = arbitraryDataReader.getFilePath();

            // Ensure it exists
            assertTrue(Files.exists(finalPath));

            // Its directory hash should match the hash of demo2
            ArbitraryDataDigest path2Digest = new ArbitraryDataDigest(path2);
            path2Digest.compute();
            ArbitraryDataDigest finalPathDigest = new ArbitraryDataDigest(finalPath);
            finalPathDigest.compute();
            assertEquals(path2Digest.getHash58(), finalPathDigest.getHash58());

            // .. and its directory hash should also match the one included in the metadata
            ArbitraryDataMetadataPatch patchMetadata = new ArbitraryDataMetadataPatch(finalPath);
            patchMetadata.read();
            assertArrayEquals(patchMetadata.getCurrentHash(), path2Digest.getHash());

        }
    }

}
