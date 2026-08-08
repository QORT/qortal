package org.qortal.test.serialization;

import com.google.common.hash.HashCode;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.transaction.ChatTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.Common;
import org.qortal.test.common.transaction.ChatTestTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.Base58;

import static org.junit.Assert.*;

public class ChatSerializationTests {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }


    @Test
    public void testChatSerializationWithChatReference() throws DataException, TransformationException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Build MESSAGE-type AT transaction with chatReference
            PrivateKeyAccount signingAccount = Common.getTestAccount(repository, "alice");
            ChatTransactionData transactionData = (ChatTransactionData) ChatTestTransaction.randomTransaction(repository, signingAccount, true);
            Transaction transaction = Transaction.fromData(repository, transactionData);
            transaction.sign(signingAccount);

            assertNotNull(transactionData.getChatReference());

            final int claimedLength = TransactionTransformer.getDataLength(transactionData);
            byte[] serializedTransaction = TransactionTransformer.toBytes(transactionData);
            assertEquals("Serialized CHAT transaction length differs from declared length", claimedLength, serializedTransaction.length);

            TransactionData deserializedTransactionData = TransactionTransformer.fromBytes(serializedTransaction);
            // Re-sign
            Transaction deserializedTransaction = Transaction.fromData(repository, deserializedTransactionData);
            deserializedTransaction.sign(signingAccount);
            assertEquals("Deserialized CHAT transaction signature differs", Base58.encode(transactionData.getSignature()), Base58.encode(deserializedTransactionData.getSignature()));

            // Re-serialize to check new length and bytes
            final int reclaimedLength = TransactionTransformer.getDataLength(deserializedTransactionData);
            assertEquals("Reserialized CHAT transaction declared length differs", claimedLength, reclaimedLength);

            byte[] reserializedTransaction = TransactionTransformer.toBytes(deserializedTransactionData);
            assertEquals("Reserialized CHAT transaction bytes differ", HashCode.fromBytes(serializedTransaction).toString(), HashCode.fromBytes(reserializedTransaction).toString());

            // Deserialized chat reference must match initial chat reference
            ChatTransactionData deserializedChatTransactionData = (ChatTransactionData) deserializedTransactionData;
            assertNotNull(deserializedChatTransactionData.getChatReference());
            assertArrayEquals(deserializedChatTransactionData.getChatReference(), transactionData.getChatReference());
        }
    }

    @Test
    public void testChatSerializationWithoutChatReference() throws DataException, TransformationException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Build MESSAGE-type AT transaction without chatReference
            PrivateKeyAccount signingAccount = Common.getTestAccount(repository, "alice");
            ChatTransactionData transactionData = (ChatTransactionData) ChatTestTransaction.randomTransaction(repository, signingAccount, true);
            transactionData.setChatReference(null);
            Transaction transaction = Transaction.fromData(repository, transactionData);
            transaction.sign(signingAccount);

            assertNull(transactionData.getChatReference());

            final int claimedLength = TransactionTransformer.getDataLength(transactionData);
            byte[] serializedTransaction = TransactionTransformer.toBytes(transactionData);
            assertEquals("Serialized CHAT transaction length differs from declared length", claimedLength, serializedTransaction.length);

            TransactionData deserializedTransactionData = TransactionTransformer.fromBytes(serializedTransaction);
            // Re-sign
            Transaction deserializedTransaction = Transaction.fromData(repository, deserializedTransactionData);
            deserializedTransaction.sign(signingAccount);
            assertEquals("Deserialized CHAT transaction signature differs", Base58.encode(transactionData.getSignature()), Base58.encode(deserializedTransactionData.getSignature()));

            // Re-serialize to check new length and bytes
            final int reclaimedLength = TransactionTransformer.getDataLength(deserializedTransactionData);
            assertEquals("Reserialized CHAT transaction declared length differs", claimedLength, reclaimedLength);

            byte[] reserializedTransaction = TransactionTransformer.toBytes(deserializedTransactionData);
            assertEquals("Reserialized CHAT transaction bytes differ", HashCode.fromBytes(serializedTransaction).toString(), HashCode.fromBytes(reserializedTransaction).toString());

            // Deserialized chat reference must match initial chat reference
            ChatTransactionData deserializedChatTransactionData = (ChatTransactionData) deserializedTransactionData;
            assertNull(deserializedChatTransactionData.getChatReference());
            assertArrayEquals(deserializedChatTransactionData.getChatReference(), transactionData.getChatReference());
        }
    }

}
