package org.qortal.transaction;

import com.google.common.primitives.Longs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.account.Account;
import org.qortal.controller.OnlineAccountsManager;
import org.qortal.controller.tradebot.TradeBot;
import org.qortal.crosschain.ACCT;
import org.qortal.crosschain.SupportedBlockchain;
import org.qortal.crypto.Crypto;
import org.qortal.crypto.MemoryPoW;
import org.qortal.data.at.ATData;
import org.qortal.data.crosschain.CrossChainTradeData;
import org.qortal.data.transaction.PresenceTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.PresenceTransactionTransformer;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.Base58;
import org.qortal.utils.ByteArray;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public class PresenceTransaction extends Transaction {

	private static final Logger LOGGER = LogManager.getLogger(PresenceTransaction.class);

	// Properties
	private PresenceTransactionData presenceTransactionData;

	// Other useful constants
	public static final int POW_BUFFER_SIZE = 8 * 1024 * 1024; // bytes
	public static final int POW_DIFFICULTY = 8; // leading zero bits

	public enum PresenceType {
		REWARD_SHARE(0) {
			@Override
			public long getLifetime() {
				return OnlineAccountsManager.getOnlineTimestampModulus();
			}
		},
		TRADE_BOT(1) {
			@Override
			public long getLifetime() {
				return 30 * 60 * 1000L; // 30 minutes in milliseconds
			}
		};

		public final int value;
		private static final Map<Integer, PresenceType> map = stream(PresenceType.values()).collect(toMap(type -> type.value, type -> type));

		PresenceType(int value) {
			this.value = value;
		}

		public abstract long getLifetime();

		public static PresenceType valueOf(int value) {
			return map.get(value);
		}

		/** Returns PresenceType with matching <tt>name</tt> or <tt>null</tt> (instead of throwing IllegalArgumentException). */
		public static PresenceType fromString(String name) {
			try {
				return PresenceType.valueOf(name);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	// Constructors

	public PresenceTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.presenceTransactionData = (PresenceTransactionData) this.transactionData;
	}

	// More information

	@Override
	public long getDeadline() {
		return this.transactionData.getTimestamp() + this.presenceTransactionData.getPresenceType().getLifetime();
	}

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.emptyList();
	}

	// Navigation

	public Account getSender() {
		return this.getCreator();
	}

	// Processing

	public void computeNonce() throws DataException {
		byte[] transactionBytes;

		try {
			transactionBytes = TransactionTransformer.toBytesForSigning(this.transactionData);
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for verification", e);
		}

		// Clear nonce from transactionBytes
		PresenceTransactionTransformer.clearNonce(transactionBytes);

		// Calculate nonce
		this.presenceTransactionData.setNonce(MemoryPoW.compute2(transactionBytes, POW_BUFFER_SIZE, POW_DIFFICULTY));
	}

	/**
	 * Returns whether PRESENCE transaction has valid txGroupId.
	 * <p>
	 * We insist on NO_GROUP.
	 */
	@Override
	protected boolean isValidTxGroupId() throws DataException {
		int txGroupId = this.transactionData.getTxGroupId();

		return txGroupId == Group.NO_GROUP;
	}

	@Override
	public ValidationResult isFeeValid() throws DataException {
		if (this.transactionData.getFee() < 0)
			return ValidationResult.NEGATIVE_FEE;

		return ValidationResult.OK;
	}

	@Override
	public boolean hasValidReference() throws DataException {
		return true;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public boolean isConfirmable() {
		// PRESENCE transactions can't go into blocks
		return false;
	}

	@Override
	public ValidationResult isValid() throws DataException {
		// Nonce checking is done via isSignatureValid() as that method is only called once per import

		// If we exist in the repository then we've been imported as unconfirmed,
		// but we don't want to make it into a block, so return fake non-OK result.
		if (this.repository.getTransactionRepository().exists(this.presenceTransactionData.getSignature()))
			return ValidationResult.INVALID_BUT_OK;

		// We only support TRADE_BOT-type PRESENCE at this time
		if (PresenceType.TRADE_BOT != this.presenceTransactionData.getPresenceType())
			return ValidationResult.NOT_YET_RELEASED;

		// Check timestamp signature
		byte[] timestampSignature = this.presenceTransactionData.getTimestampSignature();
		byte[] timestampBytes = Longs.toByteArray(this.presenceTransactionData.getTimestamp());
		if (!Crypto.verify(this.transactionData.getCreatorPublicKey(), timestampSignature, timestampBytes))
			return ValidationResult.INVALID_TIMESTAMP_SIGNATURE;

		Map<ByteArray, Supplier<ACCT>> acctSuppliersByCodeHash = SupportedBlockchain.getAcctMap();
		Set<ByteArray> codeHashes = acctSuppliersByCodeHash.keySet();
		boolean isExecutable = true;

		List<ATData> atsData = repository.getATRepository().getAllATsByFunctionality(codeHashes, isExecutable);

		// Convert signer's public key to address form
		String signerAddress = Crypto.toAddress(this.transactionData.getCreatorPublicKey());

		for (ATData atData : atsData) {
			ByteArray atCodeHash = ByteArray.wrap(atData.getCodeHash());
			Supplier<ACCT> acctSupplier = acctSuppliersByCodeHash.get(atCodeHash);
			if (acctSupplier == null)
				continue;

			CrossChainTradeData crossChainTradeData = acctSupplier.get().populateTradeData(repository, atData);

			// OK if signer's public key (in address form) matches Bob's trade public key (in address form)
			if (signerAddress.equals(crossChainTradeData.qortalCreatorTradeAddress)) {
				TradeBot.getInstance().bridgePresence(this.presenceTransactionData.getTimestamp(), this.transactionData.getCreatorPublicKey(), timestampSignature, atData.getATAddress());
				return ValidationResult.OK;
			}

			// OK if signer's public key (in address form) matches Alice's trade public key (in address form)
			if (signerAddress.equals(crossChainTradeData.qortalPartnerAddress)) {
				TradeBot.getInstance().bridgePresence(this.presenceTransactionData.getTimestamp(), this.transactionData.getCreatorPublicKey(), timestampSignature, atData.getATAddress());
				return ValidationResult.OK;
			}
		}

		return ValidationResult.AT_UNKNOWN;
	}

	@Override
	public boolean isSignatureValid() {
		return false;

/*
		byte[] signature = this.transactionData.getSignature();
		if (signature == null)
			return false;

		byte[] transactionBytes;

		try {
			transactionBytes = PresenceTransactionTransformer.toBytesForSigning(this.transactionData);
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for verification", e);
		}

		if (!Crypto.verify(this.transactionData.getCreatorPublicKey(), signature, transactionBytes))
			return false;

		int nonce = this.presenceTransactionData.getNonce();

		// Clear nonce from transactionBytes
		PresenceTransactionTransformer.clearNonce(transactionBytes);

		// Check nonce
		return MemoryPoW.verify2(transactionBytes, POW_BUFFER_SIZE, POW_DIFFICULTY, nonce);
*/
	}

	/**
	 * Remove any PRESENCE transactions by the same signer that have older timestamps.
	 */
	@Override
	protected void onImportAsUnconfirmed() throws DataException {
		byte[] creatorPublicKey = this.transactionData.getCreatorPublicKey();
		List<TransactionData> creatorsPresenceTransactions = this.repository.getTransactionRepository().getUnconfirmedTransactions(TransactionType.PRESENCE, creatorPublicKey);

		if (creatorsPresenceTransactions.isEmpty())
			return;

		for (TransactionData transactionData : creatorsPresenceTransactions) {
			if (transactionData.getTimestamp() >= this.transactionData.getTimestamp())
				continue;

			LOGGER.debug(() -> String.format("Deleting older PRESENCE transaction %s", Base58.encode(transactionData.getSignature())));
			this.repository.getTransactionRepository().delete(transactionData);
		}
	}

	@Override
	public void process() throws DataException {
		throw new DataException("PRESENCE transactions should never be processed");
	}

	@Override
	public void orphan() throws DataException {
		throw new DataException("PRESENCE transactions should never be orphaned");
	}

}
