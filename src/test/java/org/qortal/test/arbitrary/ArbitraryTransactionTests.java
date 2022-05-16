package org.qortal.test.arbitrary;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.arbitrary.exception.MissingDataException;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.arbitrary.ArbitraryDataManager;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.ArbitraryUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.ArbitraryTransaction;
import org.qortal.transaction.RegisterNameTransaction;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ArbitraryTransactionTests extends Common {

    @Before
    public void beforeTest() throws DataException, IllegalAccessException {
        Common.useDefaultSettings();
    }

    @Test
    public void testDifficultyTooLow() throws IllegalAccessException, DataException, IOException, MissingDataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount alice = Common.getTestAccount(repository, "alice");
            String publicKey58 = Base58.encode(alice.getPublicKey());
            String name = "TEST"; // Can be anything for this test
            String identifier = null; // Not used for this test
            Service service = Service.ARBITRARY_DATA;
            int chunkSize = 100;
            int dataLength = 900; // Actual data length will be longer due to encryption

            // Register the name to Alice
            RegisterNameTransactionData registerNameTransactionData = new RegisterNameTransactionData(TestTransaction.generateBase(alice), name, "");
            registerNameTransactionData.setFee(new RegisterNameTransaction(null, null).getUnitFee(registerNameTransactionData.getTimestamp()));;
            TransactionUtils.signAndMint(repository, registerNameTransactionData, alice);

            // Set difficulty to 1
            FieldUtils.writeField(ArbitraryDataManager.getInstance(), "powDifficulty", 1, true);

            // Create PUT transaction
            Path path1 = ArbitraryUtils.generateRandomDataPath(dataLength);
            ArbitraryDataFile arbitraryDataFile = ArbitraryUtils.createAndMintTxn(repository, publicKey58, path1, name, identifier, ArbitraryTransactionData.Method.PUT, service, alice, chunkSize);

            // Check that nonce validation succeeds
            byte[] signature = arbitraryDataFile.getSignature();
            TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
            ArbitraryTransaction transaction = new ArbitraryTransaction(repository, transactionData);
            assertTrue(transaction.isSignatureValid());

            // Increase difficulty to 15
            FieldUtils.writeField(ArbitraryDataManager.getInstance(), "powDifficulty", 15, true);

            // Make sure the nonce validation fails
            // Note: there is a very tiny chance this could succeed due to being extremely lucky
            // and finding a high difficulty nonce in the first couple of cycles. It will be rare
            // enough that we shouldn't need to account for it.
            assertFalse(transaction.isSignatureValid());

            // Reduce difficulty back to 1, to double check
            FieldUtils.writeField(ArbitraryDataManager.getInstance(), "powDifficulty", 1, true);
            assertTrue(transaction.isSignatureValid());

        }

    }

}
