package org.qortal.test.at;

import org.ciyam.at.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.at.QortalFunctionCode;
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

public class SleepUntilMessageTests extends Common {

	private static final byte[] messageData = new byte[] { 0x44 };
	private static final byte[] creationBytes = buildSleepUntilMessageAT();
	private static final long fundingAmount = 1_00000000L;

	private Repository repository = null;
	private PrivateKeyAccount deployer;
	private DeployAtTransaction deployAtTransaction;
	private Account atAccount;
	private String atAddress;
	private byte[] rawNextTimestamp = new byte[32];
	private Transaction transaction;

	@Before
	public void before() throws DataException {
		Common.useDefaultSettings();

		this.repository = RepositoryManager.getRepository();
		this.deployer = Common.getTestAccount(repository, "alice");

		this.deployAtTransaction = doDeploy(repository, deployer, creationBytes, fundingAmount);
		this.atAccount = deployAtTransaction.getATAccount();
		this.atAddress = deployAtTransaction.getATAccount().getAddress();
	}

	@After
	public void after() throws DataException {
		if (this.repository != null)
			this.repository.close();

		this.repository = null;
	}

	@Test
	public void testDeploy() throws DataException {
			// Confirm initial value is zero
			extractNextTxTimestamp(repository, atAddress, rawNextTimestamp);
			assertArrayEquals(new byte[32], rawNextTimestamp);
	}

	@Test
	public void testFeelessSleep() throws DataException {
		// Mint block to allow AT to initialize and call SLEEP_UNTIL_MESSAGE
		BlockUtils.mintBlock(repository);

		// Fetch AT's balance for this height
		long preMintBalance = atAccount.getConfirmedBalance(Asset.QORT);

		// Mint block
		BlockUtils.mintBlock(repository);

		// Fetch new AT balance
		long postMintBalance = atAccount.getConfirmedBalance(Asset.QORT);

		assertEquals(preMintBalance, postMintBalance);
	}

	@Test
	public void testFeelessSleep2() throws DataException {
		// Mint block to allow AT to initialize and call SLEEP_UNTIL_MESSAGE
		BlockUtils.mintBlock(repository);

		// Fetch AT's balance for this height
		long preMintBalance = atAccount.getConfirmedBalance(Asset.QORT);

		// Mint several blocks
		for (int i = 0; i < 10; ++i)
			BlockUtils.mintBlock(repository);

		// Fetch new AT balance
		long postMintBalance = atAccount.getConfirmedBalance(Asset.QORT);

		assertEquals(preMintBalance, postMintBalance);
	}

	@Test
	public void testSleepUntilMessage() throws DataException {
		// Mint block to allow AT to initialize and call SLEEP_UNTIL_MESSAGE
		BlockUtils.mintBlock(repository);

		// Send message to AT
		transaction = sendMessage(repository, deployer, messageData, atAddress);
		BlockUtils.mintBlock(repository);

		// Mint block so AT executes and finds message
		BlockUtils.mintBlock(repository);

		// Confirm AT finds message
		assertTimestamp(repository, atAddress, transaction);
	}

	private static byte[] buildSleepUntilMessageAT() {
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

				/* Sleep until message arrives */
				codeByteBuffer.put(OpCode.EXT_FUN_DAT.compile(QortalFunctionCode.SLEEP_UNTIL_MESSAGE.value, addrLastTxTimestamp));

				// Find next transaction to this AT since the last one (if any)
				codeByteBuffer.put(OpCode.EXT_FUN_DAT.compile(FunctionCode.PUT_TX_AFTER_TIMESTAMP_INTO_A, addrLastTxTimestamp));

				// Copy A to data segment, starting at addrNextTx (as pointed to by addrNextTxIndex)
				codeByteBuffer.put(OpCode.EXT_FUN_DAT.compile(FunctionCode.GET_A_IND, addrNextTxIndex));

				// Stop if timestamp part of A is zero
				codeByteBuffer.put(OpCode.STZ_DAT.compile(addrNextTx));

				// Update our 'last found transaction's timestamp' using 'timestamp' from transaction
				codeByteBuffer.put(OpCode.EXT_FUN_RET.compile(FunctionCode.GET_TIMESTAMP_FROM_TX_IN_A, addrLastTxTimestamp));

				// We're done
				codeByteBuffer.put(OpCode.FIN_IMD.compile());

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
