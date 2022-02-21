package org.qortal.transaction;

import java.util.Collections;
import java.util.List;

import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.crypto.Crypto;
import org.qortal.data.group.GroupData;
import org.qortal.data.transaction.GroupBanTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

public class GroupBanTransaction extends Transaction {

	// Properties

	private GroupBanTransactionData groupBanTransactionData;
	private Account offenderAccount = null;

	// Constructors

	public GroupBanTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.groupBanTransactionData = (GroupBanTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.groupBanTransactionData.getOffender());
	}

	// Navigation

	public Account getAdmin() {
		return this.getCreator();
	}

	public Account getOffender() {
		if (this.offenderAccount == null)
			this.offenderAccount = new Account(this.repository, this.groupBanTransactionData.getOffender());

		return this.offenderAccount;
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		int groupId = this.groupBanTransactionData.getGroupId();

		// Check offender address is valid
		if (!Crypto.isValidAddress(this.groupBanTransactionData.getOffender()))
			return ValidationResult.INVALID_ADDRESS;

		GroupData groupData = this.repository.getGroupRepository().fromGroupId(groupId);

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account admin = getAdmin();

		// Can't ban if not an admin
		if (!this.repository.getGroupRepository().adminExists(groupId, admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		Account offender = getOffender();

		// Can't ban group owner
		if (offender.getAddress().equals(groupData.getOwner()))
			return ValidationResult.INVALID_GROUP_OWNER;

		// Can't ban another admin unless admin is the group owner
		if (!admin.getAddress().equals(groupData.getOwner()) && this.repository.getGroupRepository().adminExists(groupId, offender.getAddress()))
			return ValidationResult.INVALID_GROUP_OWNER;

		// Check admin has enough funds
		if (admin.getConfirmedBalance(Asset.QORT) < this.groupBanTransactionData.getFee())
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
		Group group = new Group(this.repository, this.groupBanTransactionData.getGroupId());
		group.ban(this.groupBanTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(this.groupBanTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, this.groupBanTransactionData.getGroupId());
		group.unban(this.groupBanTransactionData);

		// Save this transaction with removed member/admin references
		this.repository.getTransactionRepository().save(this.groupBanTransactionData);
	}

}
