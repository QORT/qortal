package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.BlockChain;
import org.qortal.crypto.Crypto;
import org.qortal.crypto.MemoryPoW;
import org.qortal.data.PaymentData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.payment.Payment;
import org.qortal.repository.DataException;
import org.qortal.repository.GroupRepository;
import org.qortal.repository.Repository;
import org.qortal.transform.TransformationException;
import org.qortal.transform.Transformer;
import org.qortal.transform.transaction.ChatTransactionTransformer;
import org.qortal.transform.transaction.MessageTransactionTransformer;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.NTP;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MessageTransaction extends Transaction {

	// Useful constants

	public static final int MAX_DATA_SIZE = 4000;
	public static final int POW_BUFFER_SIZE = 8 * 1024 * 1024; // bytes
	public static final int POW_DIFFICULTY_V1 = 14; // leading zero bits
	public static final int POW_DIFFICULTY_V2_CONFIRMABLE = 16; // leading zero bits
	public static final int POW_DIFFICULTY_V2_UNCONFIRMABLE = 12; // leading zero bits

	// Properties

	private MessageTransactionData messageTransactionData;

	/** Cached, lazy-instantiated payment data. Use {@link #getPaymentData()} instead! */
	private PaymentData paymentData = null;


	// Constructors

	public MessageTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.messageTransactionData = (MessageTransactionData) this.transactionData;
	}

	/** Constructs non-payment MessageTransaction. Caller will need to compute nonce/set fee and then sign. */
	public static MessageTransaction build(Repository repository, PrivateKeyAccount sender, int txGroupId, String recipient, byte[] data, boolean isText, boolean isEncrypted) throws DataException {
		long timestamp = NTP.getTime();
		byte[] reference = sender.getLastReference();
		if (reference == null) {
			reference = new byte[64];
			new Random().nextBytes(reference);
		}

		long fee = 0L;
		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, sender.getPublicKey(), fee, null);
		int version = 4;
		MessageTransactionData messageTransactionData = new MessageTransactionData(baseTransactionData, version, 0, recipient, 0, null, data, isText, isEncrypted);
		return new MessageTransaction(repository, messageTransactionData);
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		if (this.messageTransactionData.getRecipient() == null)
			return Collections.emptyList();

		return Collections.singletonList(this.messageTransactionData.getRecipient());
	}

	// Navigation

	public Account getSender() {
		return this.getCreator();
	}

	public Account getRecipient() {
		return new Account(this.repository, this.messageTransactionData.getRecipient());
	}

	// Processing

	private PaymentData getPaymentData() {
		if (this.paymentData == null)
			this.paymentData = new PaymentData(this.messageTransactionData.getRecipient(), this.messageTransactionData.getAssetId(), this.messageTransactionData.getAmount());

		return this.paymentData;
	}

	public void computeNonce() throws DataException {
		byte[] transactionBytes;

		try {
			transactionBytes = TransactionTransformer.toBytesForSigning(this.transactionData);
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for verification", e);
		}

		// Clear nonce from transactionBytes
		MessageTransactionTransformer.clearNonce(transactionBytes);

		// Calculate nonce
		this.messageTransactionData.setNonce(MemoryPoW.compute2(transactionBytes, POW_BUFFER_SIZE, getPoWDifficulty()));
	}

	public int getPoWDifficulty() {
		// The difficulty changes at the "mempow transactions updates" timestamp
		if (this.transactionData.getTimestamp() >= BlockChain.getInstance().getMemPoWTransactionUpdatesTimestamp()) {
			// If this message is confirmable then require a higher difficulty
			return this.isConfirmable() ? POW_DIFFICULTY_V2_CONFIRMABLE : POW_DIFFICULTY_V2_UNCONFIRMABLE;
		}
		// Before feature trigger timestamp, so use existing difficulty value
		return POW_DIFFICULTY_V1;
	}

	/**
	 * Returns whether MESSAGE transaction has valid txGroupId.
	 * <p>
	 * For MESSAGE transactions, a non-NO_GROUP txGroupId represents
	 * sending to a group, rather than to everyone.
	 * <p>
	 * If txGroupId is not NO_GROUP, then the sender needs to be
	 * a member of that group. The recipient, if supplied, also
	 * needs to be a member of that group.
	 */
	@Override
	protected boolean isValidTxGroupId() throws DataException {
		int txGroupId = this.transactionData.getTxGroupId();

		// txGroupId represents recipient group, unless NO_GROUP

		// Anyone can use NO_GROUP
		if (txGroupId == Group.NO_GROUP)
			return true;

		// Group even exist?
		if (!this.repository.getGroupRepository().groupExists(txGroupId))
			return false;

		GroupRepository groupRepository = this.repository.getGroupRepository();

		// Is transaction's creator is group member?
		PublicKeyAccount creator = this.getCreator();
		if (!groupRepository.memberExists(txGroupId, creator.getAddress()))
			return false;

		// If recipient address present, check they belong to group too.
		String recipient = this.messageTransactionData.getRecipient();
		if (recipient != null && !groupRepository.memberExists(txGroupId, recipient))
			return false;

		return true;
	}

	@Override
	public ValidationResult isFeeValid() throws DataException {
		// Allow zero or positive fee.
		// Actual enforcement of fee vs nonce is done in isSignatureValid().

		if (this.transactionData.getFee() < 0)
			return ValidationResult.NEGATIVE_FEE;

		return ValidationResult.OK;
	}

	@Override
	public boolean hasValidReference() throws DataException {
		// We shouldn't really get this far, but just in case:

		// Disable reference checking after feature trigger timestamp
		if (this.messageTransactionData.getTimestamp() >= BlockChain.getInstance().getDisableReferenceTimestamp()) {
			// Allow any value as long as it is the correct length
			return this.messageTransactionData.getReference() != null &&
					this.messageTransactionData.getReference().length == Transformer.SIGNATURE_LENGTH;
		}

		if (this.messageTransactionData.getReference() == null)
			return false;

		// If zero fee, then we rely on nonce and reference isn't important
		if (this.messageTransactionData.getFee() == 0)
			return true;

		return super.hasValidReference();
	}

	@Override
	public boolean isConfirmable() {
		// After feature trigger timestamp, only messages to an AT address can confirm
		if (this.transactionData.getTimestamp() >= BlockChain.getInstance().getMemPoWTransactionUpdatesTimestamp()) {
			if (this.messageTransactionData.getRecipient() == null || !this.messageTransactionData.getRecipient().toUpperCase().startsWith("A")) {
				// Message isn't to an AT address, so this transaction is unconfirmable
				return false;
			}
		}
		return true;
	}

	@Override
	public ValidationResult isValid() throws DataException {
		// Nonce checking is done via isSignatureValid() as that method is only called once per import

		// Check data length
		if (this.messageTransactionData.getData().length < 1 || this.messageTransactionData.getData().length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// If message has no recipient then it cannot have a payment
		if (this.messageTransactionData.getRecipient() == null && this.messageTransactionData.getAmount() != 0)
			return ValidationResult.INVALID_AMOUNT;

		// If message has no payment then we only need to do a simple balance check for fee
		if (this.messageTransactionData.getAmount() == 0) {
			if (getSender().getConfirmedBalance(Asset.QORT) < this.messageTransactionData.getFee())
				return ValidationResult.NO_BALANCE;

			return ValidationResult.OK;
		}

		// Wrap and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(this.messageTransactionData.getSenderPublicKey(), getPaymentData(),
				this.messageTransactionData.getFee(), true);
	}

	@Override
	public boolean isSignatureValid() {
		byte[] signature = this.transactionData.getSignature();
		if (signature == null)
			return false;

		byte[] transactionBytes;

		try {
			transactionBytes = ChatTransactionTransformer.toBytesForSigning(this.transactionData);
		} catch (TransformationException e) {
			throw new RuntimeException("Unable to transform transaction to byte array for verification", e);
		}

		if (!Crypto.verify(this.transactionData.getCreatorPublicKey(), signature, transactionBytes))
			return false;

		// If feee is non-zero then we don't check nonce
		if (this.messageTransactionData.getFee() > 0)
			return true;

		int nonce = this.messageTransactionData.getNonce();

		// Clear nonce from transactionBytes
		MessageTransactionTransformer.clearNonce(transactionBytes);

		// Check nonce
		return MemoryPoW.verify2(transactionBytes, POW_BUFFER_SIZE, getPoWDifficulty(), nonce);
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// If we have no amount then we can always process
		if (this.messageTransactionData.getAmount() == 0L)
			return ValidationResult.OK;

		// Wrap and delegate final processable checks to Payment class
		return new Payment(this.repository).isProcessable(this.messageTransactionData.getSenderPublicKey(),
				getPaymentData(), this.messageTransactionData.getFee(), true);
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		// Only certain MESSAGE transactions are able to confirm
		if (!this.isConfirmable()) {
			throw new DataException("Unconfirmable MESSAGE transactions should never be processed");
		}

		// If we have no amount then there's nothing to do
		if (this.messageTransactionData.getAmount() == 0L)
			return;

		// Wrap and delegate payment processing to Payment class.
		new Payment(this.repository).process(this.messageTransactionData.getSenderPublicKey(), getPaymentData());
	}

	@Override
	public void processReferencesAndFees() throws DataException {
		// If we have no amount then we only need to process sender's reference and fees
		if (this.messageTransactionData.getAmount() == 0L) {
			super.processReferencesAndFees();
			return;
		}

		// Wrap and delegate references processing to Payment class. Only update recipient's last reference if transferring QORT.
		new Payment(this.repository).processReferencesAndFees(this.messageTransactionData.getSenderPublicKey(),
				getPaymentData(), this.messageTransactionData.getFee(), this.messageTransactionData.getSignature(),
				false);
	}

	@Override
	public void orphan() throws DataException {
		// Only certain MESSAGE transactions are able to confirm
		if (!this.isConfirmable()) {
			throw new DataException("Unconfirmable MESSAGE transactions should never be orphaned");
		}

		// If we have no amount then there's nothing to do
		if (this.messageTransactionData.getAmount() == 0L)
			return;

		// Wrap and delegate payment processing to Payment class.
		new Payment(this.repository).orphan(this.messageTransactionData.getSenderPublicKey(), getPaymentData());
	}

	@Override
	public void orphanReferencesAndFees() throws DataException {
		// If we have no amount then we only need to orphan sender's reference and fees
		if (this.messageTransactionData.getAmount() == 0L) {
			super.orphanReferencesAndFees();
			return;
		}

		// Wrap and delegate references processing to Payment class. Only revert recipient's last reference if transferring QORT.
		new Payment(this.repository).orphanReferencesAndFees(this.messageTransactionData.getSenderPublicKey(),
				getPaymentData(), this.messageTransactionData.getFee(), this.messageTransactionData.getSignature(),
				this.messageTransactionData.getReference(), false);
	}

}
