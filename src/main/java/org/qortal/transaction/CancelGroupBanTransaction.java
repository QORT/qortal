package org.qortal.transaction;

import java.util.Collections;
import java.util.List;

import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.crypto.Crypto;
import org.qortal.data.group.GroupData;
import org.qortal.data.transaction.CancelGroupBanTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

public class CancelGroupBanTransaction extends Transaction {

	// Properties

	private CancelGroupBanTransactionData groupUnbanTransactionData;
	private Account memberAccount = null;

	// Constructors

	public CancelGroupBanTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.groupUnbanTransactionData = (CancelGroupBanTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.groupUnbanTransactionData.getMember());
	}

	// Navigation

	public Account getAdmin() {
		return this.getCreator();
	}

	public Account getMember() {
		if (this.memberAccount == null)
			this.memberAccount = new Account(this.repository, this.groupUnbanTransactionData.getMember());

		return this.memberAccount;
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		int groupId = this.groupUnbanTransactionData.getGroupId();

		// Check member address is valid
		if (!Crypto.isValidAddress(this.groupUnbanTransactionData.getMember()))
			return ValidationResult.INVALID_ADDRESS;

		GroupData groupData = this.repository.getGroupRepository().fromGroupId(groupId);

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account admin = getAdmin();

		// Can't unban if not an admin
		if (!this.repository.getGroupRepository().adminExists(groupId, admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		Account member = getMember();

		// Check ban actually exists
		if (!this.repository.getGroupRepository().banExists(groupId, member.getAddress()))
			return ValidationResult.BAN_UNKNOWN;

		// Check admin has enough funds
		if (admin.getConfirmedBalance(Asset.QORT) < this.groupUnbanTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, this.groupUnbanTransactionData.getGroupId());
		group.cancelBan(this.groupUnbanTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(this.groupUnbanTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, this.groupUnbanTransactionData.getGroupId());
		group.uncancelBan(this.groupUnbanTransactionData);

		// Save this transaction with removed member/admin references
		this.repository.getTransactionRepository().save(this.groupUnbanTransactionData);
	}

}
