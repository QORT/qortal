package org.qortal.transaction;

import com.google.common.base.Utf8;
import org.qortal.account.Account;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.crypto.Crypto;
import org.qortal.data.asset.AssetData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.UpdateAssetTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Collections;
import java.util.List;

public class UpdateAssetTransaction extends Transaction {

	// Properties
	private UpdateAssetTransactionData updateAssetTransactionData;

	// Constructors

	public UpdateAssetTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.updateAssetTransactionData = (UpdateAssetTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.updateAssetTransactionData.getNewOwner());
	}

	// Navigation

	public PublicKeyAccount getOwner() {
		return this.getCreator();
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check asset actually exists
		AssetData assetData = this.repository.getAssetRepository().fromAssetId(this.updateAssetTransactionData.getAssetId());
		if (assetData == null)
			return ValidationResult.ASSET_DOES_NOT_EXIST;

		// Check new owner address is valid
		if (!Crypto.isValidAddress(this.updateAssetTransactionData.getNewOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check new description size bounds. Note: zero length means DO NOT CHANGE description
		int newDescriptionLength = Utf8.encodedLength(this.updateAssetTransactionData.getNewDescription());
		if (newDescriptionLength > Asset.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check new data size bounds. Note: zero length means DO NOT CHANGE data
		int newDataLength = Utf8.encodedLength(this.updateAssetTransactionData.getNewData());
		if (newDataLength > Asset.MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// As this transaction type could require approval, check txGroupId
		// matches groupID at creation
		if (assetData.getCreationGroupId() != this.updateAssetTransactionData.getTxGroupId())
			return ValidationResult.TX_GROUP_ID_MISMATCH;

		Account currentOwner = getOwner();

		// Check current owner has enough funds
		if (currentOwner.getConfirmedBalance(Asset.QORT) < this.updateAssetTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Check transaction's public key matches asset's current owner
		Account currentOwner = getOwner();
		AssetData assetData = this.repository.getAssetRepository().fromAssetId(this.updateAssetTransactionData.getAssetId());

		if (!assetData.getOwner().equals(currentOwner.getAddress()))
			return ValidationResult.INVALID_ASSET_OWNER;

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		// Update Asset
		Asset asset = new Asset(this.repository, this.updateAssetTransactionData.getAssetId());
		asset.update(this.updateAssetTransactionData);

		// Save this transaction, with updated "name reference" to previous transaction that updated name
		this.repository.getTransactionRepository().save(this.updateAssetTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert asset
		Asset asset = new Asset(this.repository, this.updateAssetTransactionData.getAssetId());
		asset.revert(this.updateAssetTransactionData);

		// Save this transaction, with removed "name reference" to previous transaction that updated name
		this.repository.getTransactionRepository().save(this.updateAssetTransactionData);
	}

}
