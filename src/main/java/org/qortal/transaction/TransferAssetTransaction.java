package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.block.BlockChain;
import org.qortal.data.PaymentData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.TransferAssetTransactionData;
import org.qortal.payment.Payment;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Collections;
import java.util.List;

public class TransferAssetTransaction extends Transaction {

	// Properties

	private TransferAssetTransactionData transferAssetTransactionData;
	private PaymentData paymentData = null;

	// Constructors

	public TransferAssetTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.transferAssetTransactionData = (TransferAssetTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.transferAssetTransactionData.getRecipient());
	}

	// Navigation

	public Account getSender() {
		return this.getCreator();
	}

	// Processing

	private PaymentData getPaymentData() {
		if (this.paymentData == null)
			this.paymentData = new PaymentData(this.transferAssetTransactionData.getRecipient(), this.transferAssetTransactionData.getAssetId(),
					this.transferAssetTransactionData.getAmount());

		return this.paymentData;
	}

	@Override
	public ValidationResult isValid() throws DataException {
		int blockchainHeight = this.repository.getBlockRepository().getBlockchainHeight();
		final int start = BlockChain.getInstance().getSelfSponsorshipAlgoV2Height();
		final int end = BlockChain.getInstance().getSelfSponsorshipAlgoV3Height();

		// Check if we are on algo runs
		if (blockchainHeight >= start && blockchainHeight <= end)
		       return ValidationResult.ASSET_NOT_SPENDABLE;

		// Wrap asset transfer as a payment and delegate final payment checks to Payment class
		return new Payment(this.repository).isValid(this.transferAssetTransactionData.getSenderPublicKey(), getPaymentData(), this.transferAssetTransactionData.getFee());
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Wrap asset transfer as a payment and delegate final processable checks to Payment class
		return new Payment(this.repository).isProcessable(this.transferAssetTransactionData.getSenderPublicKey(), getPaymentData(), this.transferAssetTransactionData.getFee());
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		// Wrap asset transfer as a payment and delegate processing to Payment class.
		new Payment(this.repository).process(this.transferAssetTransactionData.getSenderPublicKey(), getPaymentData());
	}

	@Override
	public void processReferencesAndFees() throws DataException {
		// Wrap asset transfer as a payment and delegate processing to Payment class. Only update recipient's last reference if transferring QORT.
		new Payment(this.repository).processReferencesAndFees(this.transferAssetTransactionData.getSenderPublicKey(), getPaymentData(), this.transferAssetTransactionData.getFee(),
				this.transferAssetTransactionData.getSignature(), false);
	}

	@Override
	public void orphan() throws DataException {
		// Wrap asset transfer as a payment and delegate processing to Payment class.
		new Payment(this.repository).orphan(this.transferAssetTransactionData.getSenderPublicKey(), getPaymentData());
	}

	@Override
	public void orphanReferencesAndFees() throws DataException {
		// Wrap asset transfer as a payment and delegate processing to Payment class. Only revert recipient's last reference if transferring QORT.
		new Payment(this.repository).orphanReferencesAndFees(this.transferAssetTransactionData.getSenderPublicKey(), getPaymentData(), this.transferAssetTransactionData.getFee(),
				this.transferAssetTransactionData.getSignature(), this.transferAssetTransactionData.getReference(), false);
	}

}
