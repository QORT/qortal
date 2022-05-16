package org.qortal.naming;

import org.qortal.account.Account;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.crypto.Crypto;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.BuyNameTransactionData;
import org.qortal.data.transaction.CancelSellNameTransactionData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.data.transaction.SellNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.UpdateNameTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.utils.Unicode;

public class Name {

	// Properties
	private Repository repository;
	private NameData nameData;

	// Useful constants
	public static final int MIN_NAME_SIZE = 3;
	public static final int MAX_NAME_SIZE = 40;
	public static final int MAX_DATA_SIZE = 4000;

	// Constructors

	/**
	 * Construct Name business object using info from register name transaction.
	 * 
	 * @param repository
	 * @param registerNameTransactionData
	 */
	public Name(Repository repository, RegisterNameTransactionData registerNameTransactionData) {
		this.repository = repository;

		String owner = Crypto.toAddress(registerNameTransactionData.getRegistrantPublicKey());
		String reducedName = Unicode.sanitize(registerNameTransactionData.getName());

		this.nameData = new NameData(registerNameTransactionData.getName(), reducedName, owner,
				registerNameTransactionData.getData(), registerNameTransactionData.getTimestamp(),
				registerNameTransactionData.getSignature(), registerNameTransactionData.getTxGroupId());
	}

	/**
	 * Construct Name business object using existing name in repository.
	 * 
	 * @param repository
	 * @param name
	 * @throws DataException
	 */
	public Name(Repository repository, String name) throws DataException {
		this.repository = repository;
		this.nameData = this.repository.getNameRepository().fromName(name);
	}

	// Processing

	public void register() throws DataException {
		this.repository.getNameRepository().save(this.nameData);
	}

	public void unregister() throws DataException {
		this.repository.getNameRepository().delete(this.nameData.getName());
	}

	public void update(UpdateNameTransactionData updateNameTransactionData) throws DataException {
		// Update reference in transaction data
		updateNameTransactionData.setNameReference(this.nameData.getReference());

		// New name reference is this transaction's signature
		this.nameData.setReference(updateNameTransactionData.getSignature());

		// Set name's last-updated timestamp
		this.nameData.setUpdated(updateNameTransactionData.getTimestamp());

		// Update name, reduced name, and data where appropriate
		if (!updateNameTransactionData.getNewName().isEmpty()) {
			this.nameData.setName(updateNameTransactionData.getNewName());
			this.nameData.setReducedName(updateNameTransactionData.getReducedNewName());

			// If we're changing the name, we need to delete old entry
			this.repository.getNameRepository().delete(updateNameTransactionData.getName());
		}

		if (!updateNameTransactionData.getNewData().isEmpty())
			this.nameData.setData(updateNameTransactionData.getNewData());

		// Save updated name data
		this.repository.getNameRepository().save(this.nameData);
	}

	public void revert(UpdateNameTransactionData updateNameTransactionData) throws DataException {
		// Previous name reference is taken from this transaction's cached copy
		byte[] nameReference = updateNameTransactionData.getNameReference();

		// Revert name's name-changing transaction reference
		this.nameData.setReference(nameReference);

		// Revert name's last-updated timestamp
		this.nameData.setUpdated(fetchPreviousUpdateTimestamp(nameReference));

		// We can find previous 'name' from update transaction
		this.nameData.setName(updateNameTransactionData.getName());

		// We can derive the previous 'reduced name' from the previous name
		this.nameData.setReducedName(Unicode.sanitize(updateNameTransactionData.getName()));

		// We might need to hunt for previous data value
		if (!updateNameTransactionData.getNewData().isEmpty())
			this.nameData.setData(findPreviousData(nameReference));

		this.repository.getNameRepository().save(this.nameData);

		if (!updateNameTransactionData.getNewName().isEmpty())
			// Name has changed, delete old entry
			this.repository.getNameRepository().delete(updateNameTransactionData.getNewName());

		// Remove reference to previous name-changing transaction
		updateNameTransactionData.setNameReference(null);
	}

	private String findPreviousData(byte[] nameReference) throws DataException {
		// Follow back through name-references until we hit the data we need
		while (true) {
			TransactionData previousTransactionData = this.repository.getTransactionRepository().fromSignature(nameReference);
			if (previousTransactionData == null)
				throw new DataException("Unable to revert name transaction as referenced transaction not found in repository");

			switch (previousTransactionData.getType()) {
				case REGISTER_NAME: {
					RegisterNameTransactionData previousRegisterNameTransactionData = (RegisterNameTransactionData) previousTransactionData;

					return previousRegisterNameTransactionData.getData();
				}

				case UPDATE_NAME: {
					UpdateNameTransactionData previousUpdateNameTransactionData = (UpdateNameTransactionData) previousTransactionData;

					if (!previousUpdateNameTransactionData.getNewData().isEmpty())
						return previousUpdateNameTransactionData.getNewData();

					nameReference = previousUpdateNameTransactionData.getNameReference();

					break;
				}

				case BUY_NAME: {
					BuyNameTransactionData previousBuyNameTransactionData = (BuyNameTransactionData) previousTransactionData;
					nameReference = previousBuyNameTransactionData.getNameReference();
					break;
				}

				default:
					throw new IllegalStateException("Unable to revert name transaction due to unsupported referenced transaction");
			}
		}
	}

	public void sell(SellNameTransactionData sellNameTransactionData) throws DataException {
		// Mark as for-sale and set price
		this.nameData.setIsForSale(true);
		this.nameData.setSalePrice(sellNameTransactionData.getAmount());

		// Save sale info into repository
		this.repository.getNameRepository().save(this.nameData);
	}

	public void unsell(SellNameTransactionData sellNameTransactionData) throws DataException {
		// Mark not for-sale and unset price
		this.nameData.setIsForSale(false);
		this.nameData.setSalePrice(null);

		// Save no-sale info into repository
		this.repository.getNameRepository().save(this.nameData);
	}

	public void cancelSell(CancelSellNameTransactionData cancelSellNameTransactionData) throws DataException {
		// Mark not for-sale but leave price in case we want to orphan
		this.nameData.setIsForSale(false);

		// Save sale info into repository
		this.repository.getNameRepository().save(this.nameData);
	}

	public void uncancelSell(CancelSellNameTransactionData cancelSellNameTransactionData) throws DataException {
		// Mark as for-sale using existing price
		this.nameData.setIsForSale(true);

		// Save no-sale info into repository
		this.repository.getNameRepository().save(this.nameData);
	}

	public void buy(BuyNameTransactionData buyNameTransactionData) throws DataException {
		// Save previous name-changing reference in this transaction's data
		// Caller is expected to save
		buyNameTransactionData.setNameReference(this.nameData.getReference());

		// Mark not for-sale but leave price in case we want to orphan
		this.nameData.setIsForSale(false);

		// Update seller's balance
		Account seller = new Account(this.repository, this.nameData.getOwner());
		seller.modifyAssetBalance(Asset.QORT, buyNameTransactionData.getAmount());

		// Set new owner
		Account buyer = new PublicKeyAccount(this.repository, buyNameTransactionData.getBuyerPublicKey());
		this.nameData.setOwner(buyer.getAddress());
		// Update buyer's balance
		buyer.modifyAssetBalance(Asset.QORT, - buyNameTransactionData.getAmount());

		// Set name-changing reference to this transaction
		this.nameData.setReference(buyNameTransactionData.getSignature());

		// Set name's last-updated timestamp
		this.nameData.setUpdated(buyNameTransactionData.getTimestamp());

		// Save updated name data
		this.repository.getNameRepository().save(this.nameData);
	}

	public void unbuy(BuyNameTransactionData buyNameTransactionData) throws DataException {
		// Mark as for-sale using existing price
		this.nameData.setIsForSale(true);
		this.nameData.setSalePrice(buyNameTransactionData.getAmount());

		// Previous name-changing reference is taken from this transaction's cached copy
		this.nameData.setReference(buyNameTransactionData.getNameReference());

		// Revert name's last-updated timestamp
		this.nameData.setUpdated(fetchPreviousUpdateTimestamp(buyNameTransactionData.getNameReference()));

		// Revert to previous owner
		this.nameData.setOwner(buyNameTransactionData.getSeller());

		// Save updated name data
		this.repository.getNameRepository().save(this.nameData);

		// Revert buyer's balance
		Account buyer = new PublicKeyAccount(this.repository, buyNameTransactionData.getBuyerPublicKey());
		buyer.modifyAssetBalance(Asset.QORT, buyNameTransactionData.getAmount());

		// Revert seller's balance
		Account seller = new Account(this.repository, buyNameTransactionData.getSeller());
		seller.modifyAssetBalance(Asset.QORT, - buyNameTransactionData.getAmount());

		// Clean previous name-changing reference from this transaction's data
		// Caller is expected to save
		buyNameTransactionData.setNameReference(null);
	}

	private Long fetchPreviousUpdateTimestamp(byte[] nameReference) throws DataException {
		TransactionData previousTransactionData = this.repository.getTransactionRepository().fromSignature(nameReference);
		if (previousTransactionData == null)
			throw new DataException("Unable to revert name transaction as referenced transaction not found in repository");

		// If we've hit REGISTER_NAME then we've run out of updates
		if (previousTransactionData.getType() == TransactionType.REGISTER_NAME)
			return null;

		return previousTransactionData.getTimestamp();
	}

	public NameData getNameData() {
		return this.nameData;
	}

}
