package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.block.BlockChain;
import org.qortal.data.transaction.AccountLevelTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Collections;
import java.util.List;

public class AccountLevelTransaction extends Transaction {

	// Properties

	private AccountLevelTransactionData accountLevelTransactionData;
	private Account targetAccount = null;

	// Constructors

	public AccountLevelTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.accountLevelTransactionData = (AccountLevelTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.accountLevelTransactionData.getTarget());
	}

	// Navigation

	public Account getTarget() {
		if (this.targetAccount == null)
			this.targetAccount = new Account(this.repository, this.accountLevelTransactionData.getTarget());

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
		Account target = getTarget();

		// Save this transaction
		this.repository.getTransactionRepository().save(this.accountLevelTransactionData);

		// Set account's initial level
		target.setLevel(this.accountLevelTransactionData.getLevel());

		// Set account's blocks minted adjustment
		List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
		int blocksMintedAdjustment = cumulativeBlocksByLevel.get(this.accountLevelTransactionData.getLevel());
		target.setBlocksMintedAdjustment(blocksMintedAdjustment);
	}

	@Override
	public void processReferencesAndFees() throws DataException {
		// Set account's reference
		getTarget().setLastReference(this.accountLevelTransactionData.getSignature());
	}

	@Override
	public void orphan() throws DataException {
		// Revert
		Account target = getTarget();

		// This is only ever a genesis block transaction so simply delete account
		this.repository.getAccountRepository().delete(target.getAddress());
	}

}
