package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.crypto.Crypto;
import org.qortal.data.group.GroupData;
import org.qortal.data.transaction.RemoveGroupAdminTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RemoveGroupAdminTransaction extends Transaction {

	// Properties

	private RemoveGroupAdminTransactionData removeGroupAdminTransactionData;
	private Account adminAccount = null;

	// Constructors

	public RemoveGroupAdminTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.removeGroupAdminTransactionData = (RemoveGroupAdminTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.removeGroupAdminTransactionData.getAdmin());
	}

	// Navigation

	public Account getOwner() {
		return this.getCreator();
	}

	public Account getAdmin() {
		if (this.adminAccount == null)
			this.adminAccount = new Account(this.repository, this.removeGroupAdminTransactionData.getAdmin());

		return this.adminAccount;
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		int groupId = this.removeGroupAdminTransactionData.getGroupId();

		// Check admin address is valid
		if (!Crypto.isValidAddress(this.removeGroupAdminTransactionData.getAdmin()))
			return ValidationResult.INVALID_ADDRESS;

		GroupData groupData = this.repository.getGroupRepository().fromGroupId(groupId);

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account owner = getOwner();
		String groupOwner = this.repository.getGroupRepository().getOwner(groupId);
		boolean groupOwnedByNullAccount = Objects.equals(groupOwner, Group.NULL_OWNER_ADDRESS);

		// Require approval if transaction relates to a group owned by the null account
		if (groupOwnedByNullAccount && !this.needsGroupApproval())
			return ValidationResult.GROUP_APPROVAL_REQUIRED;

		// Check transaction's public key matches group's current owner (except for groups owned by the null account)
		if (!groupOwnedByNullAccount && !owner.getAddress().equals(groupOwner))
			return ValidationResult.INVALID_GROUP_OWNER;

		// Check transaction creator is a group member
		if (!this.repository.getGroupRepository().memberExists(groupId, this.getCreator().getAddress()))
			return ValidationResult.NOT_GROUP_MEMBER;

		Account admin = getAdmin();

		// Check member is an admin
		if (!this.repository.getGroupRepository().adminExists(groupId, admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		// Check admin is not group owner
		if (admin.getAddress().equals(groupData.getOwner()))
			return ValidationResult.INVALID_GROUP_OWNER;

		// Check creator has enough funds
		if (owner.getConfirmedBalance(Asset.QORT) < this.removeGroupAdminTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		// Update Group adminship
		Group group = new Group(this.repository, this.removeGroupAdminTransactionData.getGroupId());
		group.demoteFromAdmin(this.removeGroupAdminTransactionData);

		// Save this transaction with cached references to transactions that can help restore state
		this.repository.getTransactionRepository().save(this.removeGroupAdminTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group adminship
		Group group = new Group(this.repository, this.removeGroupAdminTransactionData.getGroupId());
		group.undemoteFromAdmin(this.removeGroupAdminTransactionData);

		// Save this transaction with removed group references
		this.repository.getTransactionRepository().save(this.removeGroupAdminTransactionData);
	}

}
