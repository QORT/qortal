package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.data.transaction.AccountFlagsTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Collections;
import java.util.List;

public class AccountFlagsTransaction extends Transaction {

	// Properties

	private AccountFlagsTransactionData accountFlagsTransactionData;
	private Account targetAccount = null;

	// Constructors

	public AccountFlagsTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.accountFlagsTransactionData = (AccountFlagsTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.accountFlagsTransactionData.getTarget());
	}

	// Navigation

	public Account getTarget() {
		if (this.targetAccount == null)
			this.targetAccount = new Account(this.repository, this.accountFlagsTransactionData.getTarget());

		return this.targetAccount;
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Invalid outside of genesis block
		return ValidationResult.NO_FLAG_PERMISSION;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		Account target = this.getTarget();
		Integer previousFlags = target.getFlags();

		this.accountFlagsTransactionData.setPreviousFlags(previousFlags);

		// Save this transaction with target account's previous flags value
		this.repository.getTransactionRepository().save(this.accountFlagsTransactionData);

		// If account doesn't have entry in database yet (e.g. genesis block) then flags are zero
		if (previousFlags == null)
			previousFlags = 0;

		// Set account's new flags
		int newFlags = previousFlags
				& this.accountFlagsTransactionData.getAndMask()
				| this.accountFlagsTransactionData.getOrMask()
				^ this.accountFlagsTransactionData.getXorMask();

		target.setFlags(newFlags);
	}

	@Override
	public void processReferencesAndFees() throws DataException {
		// Set account's reference
		this.getTarget().setLastReference(this.accountFlagsTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert
		Account target = getTarget();
		Integer previousFlags = this.accountFlagsTransactionData.getPreviousFlags();

		// If previousFlags are null then account didn't exist before this transaction
		if (previousFlags == null)
			this.repository.getAccountRepository().delete(target.getAddress());
		else
			target.setFlags(previousFlags);

		// Remove previous flags from transaction itself
		this.accountFlagsTransactionData.setPreviousFlags(null);
		this.repository.getTransactionRepository().save(accountFlagsTransactionData);
	}

}
