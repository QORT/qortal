package org.qortal.test.at;

import org.ciyam.at.*;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.Block;
import org.qortal.data.at.ATStateData;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.MessageTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.utils.BitTwiddling;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class GetNextTransactionTests extends Common {

	@Before
	public void before() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testGetNextTransaction() throws DataException {
		byte[] data = new byte[] { 0x44 };

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			byte[] creationBytes = buildGetNextTransactionAT();

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			byte[] rawNextTimestamp = new byte[32];
			Transaction transaction;

			// Confirm initial value is zero
			extractNextTxTimestamp(repository, atAddress, rawNextTimestamp);
			assertArrayEquals(new byte[32], rawNextTimestamp);

			// Send message to someone other than AT
			sendMessage(repository, deployer, data, deployer.getAddress());
			BlockUtils.mintBlock(repository);

			// Confirm AT does not find message
			extractNextTxTimestamp(repository, atAddress, rawNextTimestamp);
			assertArrayEquals(new byte[32], rawNextTimestamp);

			// Send message to AT
			transaction = sendMessage(repository, deployer, data, atAddress);
			BlockUtils.mintBlock(repository);

			// Confirm AT finds message
			BlockUtils.mintBlock(repository);
			assertTimestamp(repository, atAddress, transaction);

			// Mint a few blocks, then send non-AT message, followed by two AT messages (in same block)
			for (int i = 0; i < 5; ++i)
				BlockUtils.mintBlock(repository);

			sendMessage(repository, deployer, data, deployer.getAddress());

			Transaction transaction1 = sendMessage(repository, deployer, data, atAddress);
			Transaction transaction2 = sendMessage(repository, deployer, data, atAddress);

			BlockUtils.mintBlock(repository);

			// Confirm AT finds first message
			BlockUtils.mintBlock(repository);
			assertTimestamp(repository, atAddress, transaction1);

			// Confirm AT finds second message
			BlockUtils.mintBlock(repository);
			assertTimestamp(repository, atAddress, transaction2);
		}
	}

	private byte[] buildGetNextTransactionAT() {
		// Labels for data segment addresses
		int addrCounter = 0;

		// Beginning of data segment for easy extraction
		final int addrNextTx = addrCounter;
		addrCounter += 4;

		final int addrNextTxIndex = addrCounter++;

		final int addrLastTxTimestamp = addrCounter++;

		// Data segment
		ByteBuffer dataByteBuffer = ByteBuffer.allocate(addrCounter * MachineState.VALUE_SIZE);

		// skip addrNextTx
		dataByteBuffer.position(dataByteBuffer.position() + 4 * MachineState.VALUE_SIZE);

		// Store pointer to addrNextTx at addrNextTxIndex
		dataByteBuffer.putLong(addrNextTx);

		ByteBuffer codeByteBuffer = ByteBuffer.allocate(512);

		// Two-pass version
		for (int pass = 0; pass < 2; ++pass) {
			codeByteBuffer.clear();

			try {
				/* Initialization */

				// Use AT creation 'timestamp' as starting point for finding transactions sent to AT
				codeByteBuffer.put(OpCode.EXT_FUN_RET.compile(FunctionCode.GET_CREATION_TIMESTAMP, addrLastTxTimestamp));

				// Set restart position to after this opcode
				codeByteBuffer.put(OpCode.SET_PCS.compile());

				/* Loop, waiting for message to AT */

				// Find next transaction to this AT since the last one (if any)
				codeByteBuffer.put(OpCode.EXT_FUN_DAT.compile(FunctionCode.PUT_TX_AFTER_TIMESTAMP_INTO_A, addrLastTxTimestamp));
				// Copy A to data segment, starting at addrNextTx (as pointed to by addrNextTxIndex)
				codeByteBuffer.put(OpCode.EXT_FUN_DAT.compile(FunctionCode.GET_A_IND, addrNextTxIndex));
				// Stop if timestamp part of A is zero
				codeByteBuffer.put(OpCode.STZ_DAT.compile(addrNextTx));

				// Update our 'last found transaction's timestamp' using 'timestamp' from transaction
				codeByteBuffer.put(OpCode.EXT_FUN_RET.compile(FunctionCode.GET_TIMESTAMP_FROM_TX_IN_A, addrLastTxTimestamp));
				// Stop and wait for next block
				codeByteBuffer.put(OpCode.STP_IMD.compile());

			} catch (CompilationException e) {
				throw new IllegalStateException("Unable to compile AT?", e);
			}
		}

		codeByteBuffer.flip();

		byte[] codeBytes = new byte[codeByteBuffer.limit()];
		codeByteBuffer.get(codeBytes);

		final short ciyamAtVersion = 2;
		final short numCallStackPages = 0;
		final short numUserStackPages = 0;
		final long minActivationAmount = 0L;

		return MachineState.toCreationBytes(ciyamAtVersion, codeBytes, dataByteBuffer.array(), numCallStackPages, numUserStackPages, minActivationAmount);
	}

	private DeployAtTransaction doDeploy(Repository repository, PrivateKeyAccount deployer, byte[] creationBytes, long fundingAmount) throws DataException {
		long txTimestamp = System.currentTimeMillis();
		byte[] lastReference = deployer.getLastReference();

		if (lastReference == null) {
			System.err.println(String.format("Qortal account %s has no last reference", deployer.getAddress()));
			System.exit(2);
		}

		Long fee = null;
		String name = "Test AT";
		String description = "Test AT";
		String atType = "Test";
		String tags = "TEST";

		BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, deployer.getPublicKey(), fee, null);
		TransactionData deployAtTransactionData = new DeployAtTransactionData(baseTransactionData, name, description, atType, tags, creationBytes, fundingAmount, Asset.QORT);

		DeployAtTransaction deployAtTransaction = new DeployAtTransaction(repository, deployAtTransactionData);

		fee = deployAtTransaction.calcRecommendedFee();
		deployAtTransactionData.setFee(fee);

		TransactionUtils.signAndMint(repository, deployAtTransactionData, deployer);

		return deployAtTransaction;
	}

	private void extractNextTxTimestamp(Repository repository, String atAddress, byte[] rawNextTimestamp) throws DataException {
		// Check AT result
		ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);
		byte[] stateData = atStateData.getStateData();

		byte[] dataBytes = MachineState.extractDataBytes(stateData);

		System.arraycopy(dataBytes, 0, rawNextTimestamp, 0, rawNextTimestamp.length);
	}

	private MessageTransaction sendMessage(Repository repository, PrivateKeyAccount sender, byte[] data, String recipient) throws DataException {
		long txTimestamp = System.currentTimeMillis();
		byte[] lastReference = sender.getLastReference();

		if (lastReference == null) {
			System.err.println(String.format("Qortal account %s has no last reference", sender.getAddress()));
			System.exit(2);
		}

		Long fee = null;
		int version = 4;
		int nonce = 0;
		long amount = 0;
		Long assetId = null; // because amount is zero

		BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, sender.getPublicKey(), fee, null);
		TransactionData messageTransactionData = new MessageTransactionData(baseTransactionData, version, nonce, recipient, amount, assetId, data, false, false);

		MessageTransaction messageTransaction = new MessageTransaction(repository, messageTransactionData);

		fee = messageTransaction.calcRecommendedFee();
		messageTransactionData.setFee(fee);

		TransactionUtils.signAndImportValid(repository, messageTransactionData, sender);

		return messageTransaction;
	}

	private void assertTimestamp(Repository repository, String atAddress, Transaction transaction) throws DataException {
		int height = transaction.getHeight();
		byte[] transactionSignature = transaction.getTransactionData().getSignature();

		BlockData blockData = repository.getBlockRepository().fromHeight(height);
		assertNotNull(blockData);

		Block block = new Block(repository, blockData);

		List<Transaction> blockTransactions = block.getTransactions();
		int sequence;
		for (sequence = blockTransactions.size() - 1; sequence >= 0; --sequence)
			if (Arrays.equals(blockTransactions.get(sequence).getTransactionData().getSignature(), transactionSignature))
				break;

		assertNotSame(-1, sequence);

		byte[] rawNextTimestamp = new byte[32];
		extractNextTxTimestamp(repository, atAddress, rawNextTimestamp);

		Timestamp expectedTimestamp = new Timestamp(height, sequence);
		Timestamp actualTimestamp = new Timestamp(BitTwiddling.longFromBEBytes(rawNextTimestamp, 0));

		assertEquals(String.format("Expected height %d, seq %d but was height %d, seq %d",
					height, sequence,
					actualTimestamp.blockHeight, actualTimestamp.transactionSequence
				),
				expectedTimestamp.longValue(),
				actualTimestamp.longValue());

		byte[] expectedPartialSignature = new byte[24];
		System.arraycopy(transactionSignature, 8, expectedPartialSignature, 0, expectedPartialSignature.length);

		byte[] actualPartialSignature = new byte[24];
		System.arraycopy(rawNextTimestamp, 8, actualPartialSignature, 0, actualPartialSignature.length);

		assertArrayEquals(expectedPartialSignature, actualPartialSignature);
	}

}
