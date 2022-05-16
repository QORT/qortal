package org.qortal.transaction;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.qortal.account.Account;
import org.qortal.controller.arbitrary.ArbitraryDataManager;
import org.qortal.controller.arbitrary.ArbitraryDataStorageManager;
import org.qortal.crypto.Crypto;
import org.qortal.crypto.MemoryPoW;
import org.qortal.data.PaymentData;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.network.Network;
import org.qortal.network.message.ArbitrarySignaturesMessage;
import org.qortal.network.message.Message;
import org.qortal.payment.Payment;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.ArbitraryTransactionTransformer;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.ArbitraryTransactionUtils;

public class ArbitraryTransaction extends Transaction {

	// Properties
	private ArbitraryTransactionData arbitraryTransactionData;

	// Other useful constants
	public static final int MAX_DATA_SIZE = 4000;
	public static final int MAX_METADATA_LENGTH = 32;
	public static final int HASH_LENGTH = TransactionTransformer.SHA256_LENGTH;
	public static final int POW_BUFFER_SIZE = 8 * 1024 * 1024; // bytes
	public static final int MAX_IDENTIFIER_LENGTH = 64;

	// Constructors

	public ArbitraryTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.arbitraryTransactionData = (ArbitraryTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return this.arbitraryTransactionData.getPayments().stream().map(PaymentData::getRecipient).collect(Collectors.toList());
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
		ArbitraryTransactionTransformer.clearNonce(transactionBytes);

		// Calculate nonce
		int difficulty = ArbitraryDataManager.getInstance().getPowDifficulty();
		this.arbitraryTransactionData.setNonce(MemoryPoW.compute2(transactionBytes, POW_BUFFER_SIZE, difficulty));
	}

	@Override
	public ValidationResult isFeeValid() throws DataException {
		if (this.transactionData.getFee() < 0)
			return ValidationResult.NEGATIVE_FEE;

		return ValidationResult.OK;
	}

	@Override
	public boolean hasValidReference() throws DataException {
		// We shouldn't really get this far, but just in case:
		if (this.arbitraryTransactionData.getReference() == null) {
			return false;
		}

		// If the account current doesn't have a last reference, and the fee is 0, we will allow any value.
		// This ensures that the first transaction for an account will be valid whilst still validating
		// the last reference from the second transaction onwards. By checking for a zero fee, we ensure
		// standard last reference validation when fee > 0.
		Account creator = getCreator();
		Long fee = this.arbitraryTransactionData.getFee();
		if (creator.getLastReference() == null && fee == 0) {
			return true;
		}

		return super.hasValidReference();
	}

	@Override
	public ValidationResult isValid() throws DataException {
		// Check that some data - or a data hash - has been supplied
		if (arbitraryTransactionData.getData() == null) {
			return ValidationResult.INVALID_DATA_LENGTH;
		}

		// Check data length
		if (arbitraryTransactionData.getData().length < 1 || arbitraryTransactionData.getData().length > MAX_DATA_SIZE) {
			return ValidationResult.INVALID_DATA_LENGTH;
		}

		// Check hashes and metadata
		if (arbitraryTransactionData.getDataType() == ArbitraryTransactionData.DataType.DATA_HASH) {
			// Check length of data hash
			if (arbitraryTransactionData.getData().length != HASH_LENGTH) {
				return ValidationResult.INVALID_DATA_LENGTH;
			}

			// Version 5+
			if (arbitraryTransactionData.getVersion() >= 5) {
				byte[] metadata = arbitraryTransactionData.getMetadataHash();

				// Check maximum length of metadata hash
				if (metadata != null && metadata.length > MAX_METADATA_LENGTH) {
					return ValidationResult.INVALID_DATA_LENGTH;
				}
			}
		}

		// Check raw data
		if (arbitraryTransactionData.getDataType() == ArbitraryTransactionData.DataType.RAW_DATA) {
			// Version 5+
			if (arbitraryTransactionData.getVersion() >= 5) {
				// Check reported length of the raw data
				// We should not download the raw data, so validation of that will be performed later
				if (arbitraryTransactionData.getSize() > ArbitraryDataFile.MAX_FILE_SIZE) {
					return ValidationResult.INVALID_DATA_LENGTH;
				}
			}
		}

		// Check name if one has been included
		if (arbitraryTransactionData.getName() != null) {
			NameData nameData = this.repository.getNameRepository().fromName(arbitraryTransactionData.getName());

			// Check the name is registered
			if (nameData == null) {
				return ValidationResult.NAME_DOES_NOT_EXIST;
			}

			// Check that the transaction signer owns the name
			if (!Objects.equals(this.getCreator().getAddress(), nameData.getOwner())) {
				return ValidationResult.INVALID_NAME_OWNER;
			}
		}

		// Wrap and delegate final payment validity checks to Payment class
		return new Payment(this.repository).isValid(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee());
	}

	@Override
	public boolean isSignatureValid() {
		byte[] signature = this.transactionData.getSignature();
		if (signature == null) {
			return false;
		}

		byte[] transactionBytes;

		try {
			transactionBytes = ArbitraryTransactionTransformer.toBytesForSigning(this.transactionData);
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for verification", e);
		}

		if (!Crypto.verify(this.transactionData.getCreatorPublicKey(), signature, transactionBytes)) {
			return false;
		}

		// Nonce wasn't added until version 5+
		if (arbitraryTransactionData.getVersion() >= 5) {

			int nonce = arbitraryTransactionData.getNonce();

			// Clear nonce from transactionBytes
			ArbitraryTransactionTransformer.clearNonce(transactionBytes);

			// Check nonce
			int difficulty = ArbitraryDataManager.getInstance().getPowDifficulty();
			return MemoryPoW.verify2(transactionBytes, POW_BUFFER_SIZE, difficulty, nonce);
		}

		return true;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Wrap and delegate final payment processable checks to Payment class
		return new Payment(this.repository).isProcessable(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee());
	}

	@Override
	protected void onImportAsUnconfirmed() throws DataException {
		// We may need to move files from the misc_ folder
		ArbitraryTransactionUtils.checkAndRelocateMiscFiles(arbitraryTransactionData);

		// If the data is local, we need to perform a few actions
		if (isDataLocal()) {

			// We have the data for this transaction, so invalidate the cache
			if (arbitraryTransactionData.getName() != null) {
				ArbitraryDataManager.getInstance().invalidateCache(arbitraryTransactionData);
			}

			// We also need to broadcast to the network that we are now hosting files for this transaction,
			// but only if these files are in accordance with our storage policy
			if (ArbitraryDataStorageManager.getInstance().canStoreData(arbitraryTransactionData)) {
				// Use a null peer address to indicate our own
				byte[] signature = arbitraryTransactionData.getSignature();
				Message arbitrarySignatureMessage = new ArbitrarySignaturesMessage(null, 0, Arrays.asList(signature));
				Network.getInstance().broadcast(broadcastPeer -> arbitrarySignatureMessage);
			}
		}
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		// Wrap and delegate payment processing to Payment class.
		new Payment(this.repository).process(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments());
	}

	@Override
	public void processReferencesAndFees() throws DataException {
		// Wrap and delegate reference and fee processing to Payment class. Always update recipients' last references regardless of asset.
		new Payment(this.repository).processReferencesAndFees(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee(), arbitraryTransactionData.getSignature(), true);
	}

	@Override
	public void orphan() throws DataException {
		// Wrap and delegate payment processing to Payment class.
		new Payment(this.repository).orphan(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments());
	}

	@Override
	public void orphanReferencesAndFees() throws DataException {
		// Wrap and delegate reference and fee processing to Payment class. Always revert recipients' last references regardless of asset.
		new Payment(this.repository).orphanReferencesAndFees(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee(), arbitraryTransactionData.getSignature(), arbitraryTransactionData.getReference(), true);
	}

	// Data access

	public boolean isDataLocal() throws DataException {
		return this.repository.getArbitraryRepository().isDataLocal(this.transactionData.getSignature());
	}

	/** Returns arbitrary data payload, fetching from network if needed. Can block for a while! */
	public byte[] fetchData() throws DataException {
		// If local, read from file
		if (isDataLocal()) {
			return this.repository.getArbitraryRepository().fetchData(this.transactionData.getSignature());
		}
		return null;
	}

}
