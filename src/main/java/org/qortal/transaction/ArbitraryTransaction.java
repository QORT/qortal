package org.qortal.transaction;

import java.util.List;
import java.util.stream.Collectors;

import org.qortal.account.Account;
import org.qortal.data.PaymentData;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.payment.Payment;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

public class ArbitraryTransaction extends Transaction {

	// Properties
	private ArbitraryTransactionData arbitraryTransactionData;

	// Other useful constants
	public static final int MAX_DATA_SIZE = 4000;

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

	@Override
	public ValidationResult isValid() throws DataException {
		// Check data length
		if (arbitraryTransactionData.getData().length < 1 || arbitraryTransactionData.getData().length > MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Wrap and delegate final payment validity checks to Payment class
		return new Payment(this.repository).isValid(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee());
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Wrap and delegate final payment processable checks to Payment class
		return new Payment(this.repository).isProcessable(arbitraryTransactionData.getSenderPublicKey(), arbitraryTransactionData.getPayments(),
				arbitraryTransactionData.getFee());
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
		if (isDataLocal())
			return this.repository.getArbitraryRepository().fetchData(this.transactionData.getSignature());

		// TODO If not local, attempt to fetch via network?
		return null;
	}

}
