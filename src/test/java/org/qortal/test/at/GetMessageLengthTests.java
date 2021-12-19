package org.qortal.test.at;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.Random;

import org.ciyam.at.CompilationException;
import org.ciyam.at.FunctionCode;
import org.ciyam.at.MachineState;
import org.ciyam.at.OpCode;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.at.QortalFunctionCode;
import org.qortal.data.at.ATStateData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AccountUtils;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.MessageTransaction;
import org.qortal.utils.BitTwiddling;

public class GetMessageLengthTests extends Common {

	private static final Random RANDOM = new Random();

	@Before
	public void before() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testGetMessageLength() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			byte[] creationBytes = buildMessageLengthAT();

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Send messages with known length
			checkMessageLength(repository, deployer, atAddress, 1);
			checkMessageLength(repository, deployer, atAddress, 10);
			checkMessageLength(repository, deployer, atAddress, 32);
			checkMessageLength(repository, deployer, atAddress, 99);

			// Finally, send a payment instead and check returned length is -1
			AccountUtils.pay(repository, deployer, atAddress, 123L);
			// Mint another block so AT can process payment
			BlockUtils.mintBlock(repository);

			// Check AT result
			ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);
			byte[] stateData = atStateData.getStateData();

			byte[] dataBytes = MachineState.extractDataBytes(stateData);

			long extractedLength = BitTwiddling.longFromBEBytes(dataBytes, 0);

			assertEquals(-1L, extractedLength);
		}
	}

	private void checkMessageLength(Repository repository, PrivateKeyAccount sender, String atAddress, int messageLength) throws DataException {
		byte[] testMessage = new byte[messageLength];
		RANDOM.nextBytes(testMessage);

		sendMessage(repository, sender, testMessage, atAddress);
		// Mint another block so AT can process message
		BlockUtils.mintBlock(repository);

		// Check AT result
		ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);
		byte[] stateData = atStateData.getStateData();

		byte[] dataBytes = MachineState.extractDataBytes(stateData);

		long extractedLength = BitTwiddling.longFromBEBytes(dataBytes, 0);

		assertEquals(messageLength, extractedLength);
	}

	private byte[] buildMessageLengthAT() {
		// Labels for data segment addresses
		int addrCounter = 0;

		// Make result first for easier extraction
		final int addrResult = addrCounter++;
		final int addrLastTxTimestamp = addrCounter++;

		// Data segment
		ByteBuffer dataByteBuffer = ByteBuffer.allocate(addrCounter * MachineState.VALUE_SIZE);

		// Code labels
		Integer labelCheckTx = null;

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
				// If no transaction found, A will be zero. If A is zero, set addrComparator to 1, otherwise 0.
				codeByteBuffer.put(OpCode.EXT_FUN_RET.compile(FunctionCode.CHECK_A_IS_ZERO, addrResult));
				// If addrResult is zero (i.e. A is non-zero, transaction was found) then go check transaction
				codeByteBuffer.put(OpCode.BZR_DAT.compile(addrResult, OpCode.calcOffset(codeByteBuffer, labelCheckTx)));
				// Stop and wait for next block
				codeByteBuffer.put(OpCode.STP_IMD.compile());

				/* Check transaction */
				labelCheckTx = codeByteBuffer.position();

				// Update our 'last found transaction's timestamp' using 'timestamp' from transaction
				codeByteBuffer.put(OpCode.EXT_FUN_RET.compile(FunctionCode.GET_TIMESTAMP_FROM_TX_IN_A, addrLastTxTimestamp));
				// Save message length
				codeByteBuffer.put(OpCode.EXT_FUN_RET.compile(QortalFunctionCode.GET_MESSAGE_LENGTH_FROM_TX_IN_A.value, addrResult));

				// Stop and wait for next block (and hence more transactions)
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

		TransactionUtils.signAndMint(repository, messageTransactionData, sender);

		return messageTransaction;
	}

}
