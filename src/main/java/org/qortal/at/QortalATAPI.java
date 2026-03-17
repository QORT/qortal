package org.qortal.at;

import com.google.common.primitives.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ciyam.at.*;
import org.qortal.account.Account;
import org.qortal.account.NullAccount;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.BlockChain;
import org.qortal.block.BlockChain.CiyamAtSettings;
import org.qortal.crypto.Crypto;
import org.qortal.data.at.ATData;
import org.qortal.data.block.BlockData;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.data.transaction.*;
import org.qortal.group.Group;
import org.qortal.repository.ATRepository;
import org.qortal.repository.ATRepository.NextTransactionInfo;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transaction.AtTransaction;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.utils.Base58;
import org.qortal.utils.BitTwiddling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QortalATAPI extends API {

	private static final byte[] ADDRESS_PADDING = new byte[32 - Account.ADDRESS_LENGTH];
	private static final Logger LOGGER = LogManager.getLogger(QortalATAPI.class);

	// Properties
	private Repository repository;
	private ATData atData;
	private long blockTimestamp;
	private final CiyamAtSettings ciyamAtSettings;

	/** List of generated AT transactions */
	List<AtTransaction> transactions;

	// Constructors

	public QortalATAPI(Repository repository, ATData atData, long blockTimestamp) {
		this.repository = repository;
		this.atData = atData;
		this.transactions = new ArrayList<>();
		this.blockTimestamp = blockTimestamp;

		this.ciyamAtSettings = BlockChain.getInstance().getCiyamAtSettings();
	}

	// Methods specific to Qortal AT processing, not inherited

	public Repository getRepository() {
		return this.repository;
	}

	public List<AtTransaction> getTransactions() {
		return this.transactions;
	}

	public boolean willExecute(int blockHeight) throws DataException {
		// Sleep-until-message/height checking
		Long sleepUntilMessageTimestamp = this.atData.getSleepUntilMessageTimestamp();

		if (sleepUntilMessageTimestamp != null) {
			// Quicker to check height, if sleep-until-height also active
			Integer sleepUntilHeight = this.atData.getSleepUntilHeight();

			boolean wakeDueToHeight = sleepUntilHeight != null && sleepUntilHeight != 0 && blockHeight >= sleepUntilHeight;

			boolean wakeDueToMessage = false;
			if (!wakeDueToHeight) {
				// No avoiding asking repository
				Timestamp previousTxTimestamp = new Timestamp(sleepUntilMessageTimestamp);
				NextTransactionInfo nextTransactionInfo = this.repository.getATRepository().findNextTransaction(this.atData.getATAddress(),
						previousTxTimestamp.blockHeight,
						previousTxTimestamp.transactionSequence);

				wakeDueToMessage = nextTransactionInfo != null;
			}

			// Can we skip?
			if (!wakeDueToHeight && !wakeDueToMessage)
				return false;
		}

		return true;
	}

	public void preExecute(MachineState state) {
		// Sleep-until-message/height checking
		Long sleepUntilMessageTimestamp = this.atData.getSleepUntilMessageTimestamp();

		if (sleepUntilMessageTimestamp != null) {
			// We've passed checks, so clear sleep-related flags/values
			this.setIsSleeping(state, false);
			this.setSleepUntilHeight(state, 0);
			this.atData.setSleepUntilMessageTimestamp(null);
		}
	}

	// Inherited methods from CIYAM AT API

	@Override
	public int getMaxStepsPerRound() {
		return this.ciyamAtSettings.maxStepsPerRound;
	}

	@Override
	public int getOpCodeSteps(OpCode opcode) {
		if (opcode.value >= OpCode.EXT_FUN.value && opcode.value <= OpCode.EXT_FUN_RET_DAT_2.value)
			return this.ciyamAtSettings.stepsPerFunctionCall;

		return 1;
	}

	@Override
	public long getFeePerStep() {
		return this.ciyamAtSettings.feePerStep;
	}

	@Override
	public int getCurrentBlockHeight() {
		try {
			return this.repository.getBlockRepository().getBlockchainHeight();
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch current blockchain height?", e);
		}
	}

	@Override
	public int getATCreationBlockHeight(MachineState state) {
		try {
			return this.repository.getATRepository().getATCreationBlockHeight(this.atData.getATAddress());
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch AT's creation block height?", e);
		}
	}

	@Override
	public void putPreviousBlockHashIntoA(MachineState state) {
		try {
			int previousBlockHeight = this.repository.getBlockRepository().getBlockchainHeight() - 1;

			// We only need signature, so only request a block summary
			List<BlockSummaryData> blockSummaries = this.repository.getBlockRepository().getBlockSummaries(previousBlockHeight, previousBlockHeight);
			if (blockSummaries == null || blockSummaries.size() != 1)
				throw new RuntimeException("AT API unable to fetch previous block hash?");

			// Block's signature is 128 bytes so we need to reduce this to 4 longs (32 bytes)
			// To be able to use hash to look up block, save height (8 bytes) and partial signature (24 bytes)
			this.setA1(state, previousBlockHeight);

			byte[] signature = blockSummaries.get(0).getSignature();
			// Save some of minter's signature and transactions signature, so middle 24 bytes of the full 128 byte signature.
			this.setA2(state, BitTwiddling.longFromBEBytes(signature, 52));
			this.setA3(state, BitTwiddling.longFromBEBytes(signature, 60));
			this.setA4(state, BitTwiddling.longFromBEBytes(signature, 68));
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch previous block?", e);
		}
	}

	@Override
	public void putTransactionAfterTimestampIntoA(Timestamp timestamp, MachineState state) {
		// Recipient is this AT
		String atAddress = this.atData.getATAddress();

		int height = timestamp.blockHeight;
		int sequence = timestamp.transactionSequence;

		if (state.getCurrentBlockHeight() < BlockChain.getInstance().getAtFindNextTransactionFixHeight())
			// Off-by-one bug still in effect
			sequence += 1;

		ATRepository.NextTransactionInfo nextTransactionInfo;
		try {
			nextTransactionInfo = this.getRepository().getATRepository().findNextTransaction(atAddress, height, sequence);
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch next transaction?", e);
		}

		if (nextTransactionInfo == null) {
			// No more transactions for AT at this time - zero A and exit
			this.zeroA(state);
			return;
		}

		// Found a transaction

		this.setA1(state, new Timestamp(nextTransactionInfo.height, timestamp.blockchainId, nextTransactionInfo.sequence).longValue());

		// Copy transaction's partial signature into the other three A fields for future verification that it's the same transaction
		this.setA2(state, BitTwiddling.longFromBEBytes(nextTransactionInfo.signature, 8));
		this.setA3(state, BitTwiddling.longFromBEBytes(nextTransactionInfo.signature, 16));
		this.setA4(state, BitTwiddling.longFromBEBytes(nextTransactionInfo.signature, 24));
	}

	@Override
	public long getTypeFromTransactionInA(MachineState state) {
		TransactionData transactionData = this.getTransactionFromA(state);

		switch (transactionData.getType()) {
			case PAYMENT:
				return ATTransactionType.PAYMENT.value;

			case MESSAGE:
				return ATTransactionType.MESSAGE.value;

			case AT:
				if (((ATTransactionData) transactionData).getAmount() != null)
					return ATTransactionType.PAYMENT.value;
				else
					return ATTransactionType.MESSAGE.value;

			default:
				return 0xffffffffffffffffL;
		}
	}

	@Override
	public long getAmountFromTransactionInA(MachineState state) {
		TransactionData transactionData = this.getTransactionFromA(state);

		switch (transactionData.getType()) {
			case PAYMENT:
				return ((PaymentTransactionData) transactionData).getAmount();

			case AT:
				Long amount = ((ATTransactionData) transactionData).getAmount();

				if (amount != null)
					return amount;

				// fall-through to default

			default:
				return 0xffffffffffffffffL;
		}
	}

	@Override
	public long getTimestampFromTransactionInA(MachineState state) {
		// Transaction's "timestamp" already stored in A1
		Timestamp timestamp = new Timestamp(this.getA1(state));
		return timestamp.longValue();
	}

	@Override
	public long generateRandomUsingTransactionInA(MachineState state) {
		// The plan here is to sleep for a block then use next block's signature
		// and this transaction's signature to generate pseudo-random, but deterministic, value.

		if (!isFirstOpCodeAfterSleeping(state)) {
			// First call

			// Sleep for a block
			this.setIsSleeping(state, true);

			return 0L; // not used
		} else {
			// Second call

			// HASH(A and new block hash)
			TransactionData transactionData = this.getTransactionFromA(state);

			try {
				BlockData blockData = this.repository.getBlockRepository().getLastBlock();

				if (blockData == null)
					throw new RuntimeException("AT API unable to fetch latest block?");

				byte[] input = Bytes.concat(transactionData.getSignature(), blockData.getSignature());

				byte[] hash = Crypto.digest(input);

				return BitTwiddling.longFromBEBytes(hash, 0);
			} catch (DataException e) {
				throw new RuntimeException("AT API unable to fetch latest block from repository?", e);
			}
		}
	}

	@Override
	public void putMessageFromTransactionInAIntoB(MachineState state) {
		// Zero B in case of issues or shorter-than-B message
		this.zeroB(state);

		TransactionData transactionData = this.getTransactionFromA(state);

		byte[] messageData = this.getMessageFromTransaction(transactionData);

		// Pad messageData to fit B
		if (messageData.length < 4 * 8)
			messageData = Bytes.ensureCapacity(messageData, 4 * 8, 0);

		// Endian must be correct here so that (for example) a SHA256 message can be compared to one generated locally
		this.setB(state, messageData);
	}

	@Override
	public void putAddressFromTransactionInAIntoB(MachineState state) {
		TransactionData transactionData = this.getTransactionFromA(state);

		String address;
		if (transactionData.getType() == TransactionType.AT) {
			// Use AT address from transaction data, as transaction's public key will always be fake
			address = ((ATTransactionData) transactionData).getATAddress();
		} else {
			byte[] publicKey = transactionData.getCreatorPublicKey();
			address = Crypto.toAddress(publicKey);
		}

		// Convert to byte form as this only takes 25 bytes,
		// compared to string-form's 34 bytes,
		// and we only have 32 bytes available.
		byte[] addressBytes = Bytes.ensureCapacity(Base58.decode(address), 32, 0); // pad to 32 bytes

		this.setB(state, addressBytes);
	}

	@Override
	public void putCreatorAddressIntoB(MachineState state) {
		byte[] publicKey = atData.getCreatorPublicKey();
		String address = Crypto.toAddress(publicKey);

		// Convert to byte form as this only takes 25 bytes,
		// compared to string-form's 34 bytes,
		// and we only have 32 bytes available.
		byte[] addressBytes = Bytes.ensureCapacity(Base58.decode(address), 32, 0); // pad to 32 bytes

		this.setB(state, addressBytes);
	}

	@Override
	public long getCurrentBalance(MachineState state) {
		try {
			Account atAccount = this.getATAccount();

			return atAccount.getConfirmedBalance(Asset.QORT);
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch AT's current balance?", e);
		}
	}

	@Override
	public void payAmountToB(long amount, MachineState state) {
		Account recipient = getAccountFromB(state);

		long timestamp = this.getNextTransactionTimestamp();
		byte[] reference = this.getLastReference();

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, reference, NullAccount.PUBLIC_KEY, 0L, null);
		ATTransactionData atTransactionData = new ATTransactionData(baseTransactionData, this.atData.getATAddress(),
				recipient.getAddress(), amount, this.atData.getAssetId());
		AtTransaction atTransaction = new AtTransaction(this.repository, atTransactionData);

		// Add to our transactions
		this.transactions.add(atTransaction);
	}

	@Override
	public void messageAToB(MachineState state) {
		byte[] message = this.getA(state);
		Account recipient = getAccountFromB(state);

		long timestamp = this.getNextTransactionTimestamp();
		byte[] reference = this.getLastReference();

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, reference, NullAccount.PUBLIC_KEY, 0L, null);
		ATTransactionData atTransactionData = new ATTransactionData(baseTransactionData, this.atData.getATAddress(),
				recipient.getAddress(), message);
		AtTransaction atTransaction = new AtTransaction(this.repository, atTransactionData);

		// Add to our transactions
		this.transactions.add(atTransaction);
	}

	@Override
	public long addMinutesToTimestamp(Timestamp timestamp, long minutes, MachineState state) {
		int blockHeight = timestamp.blockHeight;

		// At least one block in the future
		blockHeight += Math.max(minutes / this.ciyamAtSettings.minutesPerBlock, 1);

		return new Timestamp(blockHeight, 0).longValue();
	}

	@Override
	public void onFinished(long finalBalance, MachineState state) {
		if (finalBalance <= 0)
			return;

		// Refund remaining balance (if any) to AT's creator
		Account creator = this.getCreator();
		long timestamp = this.getNextTransactionTimestamp();
		byte[] reference = this.getLastReference();

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, reference, NullAccount.PUBLIC_KEY, 0L, null);
		ATTransactionData atTransactionData = new ATTransactionData(baseTransactionData, this.atData.getATAddress(),
				creator.getAddress(), finalBalance, this.atData.getAssetId());
		AtTransaction atTransaction = new AtTransaction(this.repository, atTransactionData);

		// Add to our transactions
		this.transactions.add(atTransaction);
	}

	@Override
	public void onFatalError(MachineState state, ExecutionException e) {
		LOGGER.error("AT " + this.atData.getATAddress() + " suffered fatal error: " + e.getMessage());
	}

	@Override
	public void platformSpecificPreExecuteCheck(int paramCount, boolean returnValueExpected, MachineState state, short rawFunctionCode)
			throws IllegalFunctionCodeException {
		QortalFunctionCode qortalFunctionCode = QortalFunctionCode.valueOf(rawFunctionCode);

		if (qortalFunctionCode == null)
			throw new IllegalFunctionCodeException("Unknown Qortal function code 0x" + String.format("%04x", rawFunctionCode) + " encountered");

		qortalFunctionCode.preExecuteCheck(paramCount, returnValueExpected, rawFunctionCode);
	}

	@Override
	public void platformSpecificPostCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
		QortalFunctionCode qortalFunctionCode = QortalFunctionCode.valueOf(rawFunctionCode);

		if (qortalFunctionCode == null)
			throw new IllegalFunctionCodeException("Unknown Qortal function code 0x" + String.format("%04x", rawFunctionCode) + " encountered");

		qortalFunctionCode.execute(functionData, state, rawFunctionCode);
	}

	// Utility methods

	public long calcFinalFees(MachineState state) {
		return state.getSteps() * this.ciyamAtSettings.feePerStep;
	}

	/** Returns partial transaction signature, used to verify we're operating on the same transaction and not naively using block height & sequence. */
	public static byte[] partialSignature(byte[] fullSignature) {
		return Arrays.copyOfRange(fullSignature, 8, 32);
	}

	/** Verify transaction's partial signature matches A2 thru A4 */
	private void verifyTransaction(TransactionData transactionData, MachineState state) {
		// Compare end of transaction's signature against A2 thru A4
		byte[] sig = transactionData.getSignature();

		if (this.getA2(state) != BitTwiddling.longFromBEBytes(sig, 8) || this.getA3(state) != BitTwiddling.longFromBEBytes(sig, 16) || this.getA4(state) != BitTwiddling.longFromBEBytes(sig, 24))
			throw new IllegalStateException("Transaction signature in A no longer matches signature from repository");
	}

	/** Returns transaction data from repository using block height & sequence from A1, checking the transaction signatures match too */
	/* package */ TransactionData getTransactionFromA(MachineState state) {
		Timestamp timestamp = new Timestamp(this.getA1(state));

		try {
			TransactionData transactionData = this.repository.getTransactionRepository().fromHeightAndSequence(timestamp.blockHeight,
					timestamp.transactionSequence);

			if (transactionData == null)
				throw new RuntimeException("AT API unable to fetch transaction?");

			// Check transaction referenced still matches the one from the repository
			verifyTransaction(transactionData, state);

			return transactionData;
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch transaction type?", e);
		}
	}

	/** Returns message data from transaction. */
	/*package*/ byte[] getMessageFromTransaction(TransactionData transactionData) {
		switch (transactionData.getType()) {
			case MESSAGE:
				return ((MessageTransactionData) transactionData).getData();

			case AT:
				return ((ATTransactionData) transactionData).getMessage();

			default:
				return null;
		}
	}

	/*package*/ void sleepUntilMessageOrHeight(MachineState state, long txTimestamp, Long sleepUntilHeight) {
		this.setIsSleeping(state, true);

		this.atData.setSleepUntilMessageTimestamp(txTimestamp);

		if (sleepUntilHeight != null)
			this.setSleepUntilHeight(state, sleepUntilHeight.intValue());
	}

	/** Returns AT's account */
	/* package */ Account getATAccount() {
		return new Account(this.repository, this.atData.getATAddress());
	}

	/** Returns AT's creator's account */
	private PublicKeyAccount getCreator() {
		return new PublicKeyAccount(this.repository, this.atData.getCreatorPublicKey());
	}

	/** Returns the timestamp to use for next AT Transaction */
	private long getNextTransactionTimestamp() {
		/*
		 * Use block's timestamp.
		 * 
		 * This is OK because AT transactions are always generated locally and order is preserved in Transaction.getDataComparator().
		 */
		return this.blockTimestamp;
	}

	/** Returns AT account's lastReference */
	private byte[] getLastReference() {
		// If we have transactions already, then use signature from last transaction
		if (!this.transactions.isEmpty())
			return this.transactions.get(this.transactions.size() - 1).getTransactionData().getSignature();

		try {
			// Look up AT's account's last reference from repository
			Account atAccount = this.getATAccount();

			return atAccount.getLastReference();
		} catch (DataException e) {
			throw new RuntimeException("AT API unable to fetch AT's last reference from repository?", e);
		}
	}

	/**
	 * Returns Account (possibly PublicKeyAccount) based on value in B.
	 * <p>
	 * If first byte in B starts with either address version bytes,<br>
	 * and bytes 26 to 32 are zero, then use as an address, but only if valid.
	 * <p>
	 * Otherwise, assume B is a public key.
	 */
	/*package*/ Account getAccountFromB(MachineState state) {
		byte[] bBytes = this.getB(state);

		if ((bBytes[0] == Crypto.ADDRESS_VERSION || bBytes[0] == Crypto.AT_ADDRESS_VERSION)
				&& Arrays.mismatch(bBytes, Account.ADDRESS_LENGTH, 32, ADDRESS_PADDING, 0, ADDRESS_PADDING.length) == -1) {
			// Extract only the bytes containing address
			byte[] addressBytes = Arrays.copyOf(bBytes, Account.ADDRESS_LENGTH);
			// If address (in byte form) is valid...
			if (Crypto.isValidAddress(addressBytes))
				// ...then return an Account using address (converted to Base58
				return new Account(this.repository, Base58.encode(addressBytes));
		}

		return new PublicKeyAccount(this.repository, bBytes);
	}

	/* Convenience methods to allow QortalFunctionCode package-visibility access to A/B-get/set methods. */

	protected byte[] getB(MachineState state) {
		return super.getB(state);
	}

	protected void setB(MachineState state, byte[] bBytes) {
		super.setB(state, bBytes);
	}

	protected void zeroB(MachineState state) {
		super.zeroB(state);
	}

}
