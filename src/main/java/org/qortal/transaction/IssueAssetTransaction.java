package org.qortal.transaction;

import com.google.common.base.Utf8;
import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.data.transaction.IssueAssetTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.utils.Amounts;
import org.qortal.utils.Unicode;

import java.util.Collections;
import java.util.List;

public class IssueAssetTransaction extends Transaction {

	// Properties

	private IssueAssetTransactionData issueAssetTransactionData;

	// Constructors

	public IssueAssetTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.issueAssetTransactionData = (IssueAssetTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.emptyList();
	}

	// Navigation

	public Account getIssuer() {
		return this.getCreator();
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check name size bounds
		String assetName = this.issueAssetTransactionData.getAssetName();
		int assetNameLength = Utf8.encodedLength(assetName);
		if (assetNameLength < Asset.MIN_NAME_SIZE || assetNameLength > Asset.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check name is in normalized form (no leading/trailing whitespace, etc.)
		if (!assetName.equals(Unicode.normalize(assetName)))
			return ValidationResult.NAME_NOT_NORMALIZED;

		// Check description size bounds
		int assetDescriptionlength = Utf8.encodedLength(this.issueAssetTransactionData.getDescription());
		if (assetDescriptionlength < 1 || assetDescriptionlength > Asset.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check data field
		String data = this.issueAssetTransactionData.getData();
		int dataLength = Utf8.encodedLength(data);
		if (data == null || dataLength < 1 || dataLength > Asset.MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check quantity
		if (this.issueAssetTransactionData.getQuantity() < 1 || this.issueAssetTransactionData.getQuantity() > Asset.MAX_QUANTITY)
			return ValidationResult.INVALID_QUANTITY;

		// Check quantity versus indivisibility
		if (!this.issueAssetTransactionData.isDivisible() && this.issueAssetTransactionData.getQuantity() % Amounts.MULTIPLIER != 0)
			return ValidationResult.INVALID_QUANTITY;

		Account issuer = getIssuer();

		// Check issuer has enough funds
		if (issuer.getConfirmedBalance(Asset.QORT) < this.issueAssetTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Check the name isn't already taken
		if (this.repository.getAssetRepository().reducedAssetNameExists(this.issueAssetTransactionData.getReducedAssetName()))
			return ValidationResult.ASSET_ALREADY_EXISTS;

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		// Issue asset
		Asset asset = new Asset(this.repository, this.issueAssetTransactionData);
		asset.issue();

		// Add asset to issuer
		Account issuer = this.getIssuer();
		issuer.setConfirmedBalance(asset.getAssetData().getAssetId(), this.issueAssetTransactionData.getQuantity());

		// Note newly assigned asset ID in our transaction record
		this.issueAssetTransactionData.setAssetId(asset.getAssetData().getAssetId());

		// Save this transaction with newly assigned assetId
		this.repository.getTransactionRepository().save(this.issueAssetTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Remove asset from issuer
		Account issuer = this.getIssuer();
		issuer.deleteBalance(this.issueAssetTransactionData.getAssetId());

		// Deissue asset
		Asset asset = new Asset(this.repository, this.issueAssetTransactionData.getAssetId());
		asset.deissue();

		// Remove assigned asset ID from transaction info
		this.issueAssetTransactionData.setAssetId(null);

		// Save this transaction, with removed assetId
		this.repository.getTransactionRepository().save(this.issueAssetTransactionData);
	}

}
