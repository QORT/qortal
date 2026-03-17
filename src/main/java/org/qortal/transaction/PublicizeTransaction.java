package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.account.PublicKeyAccount;
import org.qortal.api.resource.TransactionsResource.ConfirmationStatus;
import org.qortal.asset.Asset;
import org.qortal.block.BlockChain;
import org.qortal.crypto.MemoryPoW;
import org.qortal.data.transaction.PublicizeTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.ChatTransactionTransformer;
import org.qortal.transform.transaction.PublicizeTransactionTransformer;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.NTP;

import java.util.Collections;
import java.util.List;

public class PublicizeTransaction extends Transaction {

	// Properties
	private PublicizeTransactionData publicizeTransactionData;

	// Other useful constants

	/** If time difference between transaction and now is greater than this then we don't verify proof-of-work. */
	public static final long HISTORIC_THRESHOLD = 2 * 7 * 24 * 60 * 60 * 1000L;
	public static final int POW_BUFFER_SIZE = 8 * 1024 * 1024; // bytes
	public static final int POW_DIFFICULTY = 14; // leading zero bits

	// Constructors

	public PublicizeTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.publicizeTransactionData = (PublicizeTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.emptyList();
	}

	// Navigation

	public Account getSender() {
		return this.getCreator();
	}

	// Processing

	public void computeNonce() {
		byte[] transactionBytes;

		try {
			transactionBytes = TransactionTransformer.toBytesForSigning(this.transactionData);
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for verification", e);
		}

		// Clear nonce from transactionBytes
		PublicizeTransactionTransformer.clearNonce(transactionBytes);

		// Calculate nonce
		this.publicizeTransactionData.setNonce(MemoryPoW.compute2(transactionBytes, POW_BUFFER_SIZE, POW_DIFFICULTY));
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
	public ValidationResult isValid() throws DataException {
		// Disable completely after feature-trigger timestamp, at the same time that mempow difficulties are being increased.
		// It could be enabled again in the future, but preferably with an enforced minimum fee instead of allowing a mempow nonce.
		if (this.transactionData.getTimestamp() >= BlockChain.getInstance().getMemPoWTransactionUpdatesTimestamp()) {
			return ValidationResult.NOT_SUPPORTED;
		}

		// There can be only one
		List<byte[]> signatures = this.repository.getTransactionRepository().getSignaturesMatchingCriteria(
				TransactionType.PUBLICIZE,
				this.transactionData.getCreatorPublicKey(),
				ConfirmationStatus.CONFIRMED,
				1, null, null);

		if (!signatures.isEmpty())
			return ValidationResult.TRANSACTION_ALREADY_EXISTS;

		// We only need to check recent transactions due to PoW verification overhead
		if (NTP.getTime() - this.transactionData.getTimestamp() < HISTORIC_THRESHOLD)
			if (!verifyNonce())
				return ValidationResult.INCORRECT_NONCE;

		// Validate fee if one has been included
		PublicKeyAccount creator = this.getCreator();
		if (this.transactionData.getFee() > 0)
			if (creator.getConfirmedBalance(Asset.QORT) < this.transactionData.getFee())
				return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	private boolean verifyNonce() {
		byte[] transactionBytes;

		try {
			transactionBytes = TransactionTransformer.toBytesForSigning(this.transactionData);
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for verification", e);
		}

		int nonce = this.publicizeTransactionData.getNonce();

		// Clear nonce from transactionBytes
		ChatTransactionTransformer.clearNonce(transactionBytes);

		// Check nonce
		return MemoryPoW.verify2(transactionBytes, POW_BUFFER_SIZE, POW_DIFFICULTY, nonce);
	}

	@Override
	public void process() throws DataException {
		// Save this transaction
		this.repository.getTransactionRepository().save(this.transactionData);

		// Ensure public key & address are saved
		this.getSender().ensureAccount();
	}

	@Override
	public void orphan() throws DataException {
		/* Don't actually need to do anything */
	}

}
