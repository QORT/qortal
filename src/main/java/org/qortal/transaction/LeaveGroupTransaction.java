package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.data.group.GroupData;
import org.qortal.data.transaction.LeaveGroupTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Collections;
import java.util.List;

public class LeaveGroupTransaction extends Transaction {

	// Properties
	private LeaveGroupTransactionData leaveGroupTransactionData;

	// Constructors

	public LeaveGroupTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.leaveGroupTransactionData = (LeaveGroupTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.emptyList();
	}

	// Navigation

	public Account getLeaver() {
		return this.getCreator();
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		int groupId = this.leaveGroupTransactionData.getGroupId();

		GroupData groupData = this.repository.getGroupRepository().fromGroupId(groupId);

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account leaver = getLeaver();

		// Can't leave if group owner
		if (leaver.getAddress().equals(groupData.getOwner()))
			return ValidationResult.GROUP_OWNER_CANNOT_LEAVE;

		// Check leaver is actually a member of group
		if (!this.repository.getGroupRepository().memberExists(groupId, leaver.getAddress()))
			return ValidationResult.NOT_GROUP_MEMBER;

		// Check leaver has enough funds
		if (leaver.getConfirmedBalance(Asset.QORT) < this.leaveGroupTransactionData.getFee())
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
		Group group = new Group(this.repository, this.leaveGroupTransactionData.getGroupId());
		group.leave(this.leaveGroupTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(this.leaveGroupTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, this.leaveGroupTransactionData.getGroupId());
		group.unleave(this.leaveGroupTransactionData);

		// Save this transaction with removed member/admin references
		this.repository.getTransactionRepository().save(this.leaveGroupTransactionData);
	}

}
