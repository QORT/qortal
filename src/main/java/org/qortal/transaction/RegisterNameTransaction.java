package org.qortal.transaction;

import com.google.common.base.Utf8;
import org.qortal.account.Account;
import org.qortal.api.resource.TransactionsResource;
import org.qortal.asset.Asset;
import org.qortal.block.BlockChain;
import org.qortal.controller.repository.NamesDatabaseIntegrityCheck;
import org.qortal.crypto.Crypto;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.naming.Name;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.utils.Unicode;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RegisterNameTransaction extends Transaction {

	// Properties
	private RegisterNameTransactionData registerNameTransactionData;

	// Constructors

	public RegisterNameTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.registerNameTransactionData = (RegisterNameTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.emptyList();
	}

	@Override
	public long getUnitFee(Long timestamp) {
		return BlockChain.getInstance().getNameRegistrationUnitFeeAtTimestamp(timestamp);
	}

	// Navigation

	public Account getRegistrant() {
		return this.getCreator();
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		Account registrant = getRegistrant();
		String name = this.registerNameTransactionData.getName();

		Optional<String> registrantPrimaryName = registrant.getPrimaryName();
		if( registrantPrimaryName.isPresent()  ) {

			NameData nameData = repository.getNameRepository().fromName(registrantPrimaryName.get());
			if (nameData.isForSale()) {
				return ValidationResult.NOT_SUPPORTED;
			}
		}

		int blockchainHeight = this.repository.getBlockRepository().getBlockchainHeight();
		final int start = BlockChain.getInstance().getSelfSponsorshipAlgoV2Height() - 1180;
		final int end = BlockChain.getInstance().getSelfSponsorshipAlgoV3Height();

		// Check name size bounds
		int nameLength = Utf8.encodedLength(name);
		if (nameLength < Name.MIN_NAME_SIZE || nameLength > Name.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check data size bounds
		int dataLength = Utf8.encodedLength(this.registerNameTransactionData.getData());
		if (dataLength > Name.MAX_DATA_SIZE)
			return ValidationResult.INVALID_DATA_LENGTH;

		// Check name is in normalized form (no leading/trailing whitespace, etc.)
		if (!name.equals(Unicode.normalize(name)))
			return ValidationResult.NAME_NOT_NORMALIZED;

		// Check name doesn't look like an address
		if (Crypto.isValidAddress(name))
			return ValidationResult.INVALID_ADDRESS;

		// Check registrant has enough funds
		if (registrant.getConfirmedBalance(Asset.QORT) < this.registerNameTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		// Check if we are on algo runs
		if (blockchainHeight >= start && blockchainHeight <= end)
			return ValidationResult.TEMPORARY_DISABLED;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Check the name isn't already taken
		if (this.repository.getNameRepository().reducedNameExists(this.registerNameTransactionData.getReducedName()))
			return ValidationResult.NAME_ALREADY_REGISTERED;

		// If accounts are only allowed one registered name then check for this
		if (BlockChain.getInstance().oneNamePerAccount(this.repository.getBlockRepository().getBlockchainHeight())
				&& !this.repository.getNameRepository().getNamesByOwner(getRegistrant().getAddress()).isEmpty())
			return ValidationResult.MULTIPLE_NAMES_FORBIDDEN;

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;

		// Rebuild this name in the Names table from the transaction history
		// This is necessary because in some rare cases names can be missing from the Names table after registration
		// but we have been unable to reproduce the issue and track down the root cause
		NamesDatabaseIntegrityCheck namesDatabaseIntegrityCheck = new NamesDatabaseIntegrityCheck();
		namesDatabaseIntegrityCheck.rebuildName(registerNameTransactionData.getName(), this.repository);
	}

	@Override
	public void process() throws DataException {
		// Register Name
		Name name = new Name(this.repository, this.registerNameTransactionData);
		name.register();
	}

	@Override
	public void orphan() throws DataException {
		// Unregister name
		Name name = new Name(this.repository, this.registerNameTransactionData.getName());
		name.unregister();
	}

}
