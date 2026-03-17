package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.crypto.Crypto;
import org.qortal.data.transaction.AddGroupAdminTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AddGroupAdminTransaction extends Transaction {

	// Properties

	private AddGroupAdminTransactionData addGroupAdminTransactionData;
	Account memberAccount = null;

	// Constructors

	public AddGroupAdminTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.addGroupAdminTransactionData = (AddGroupAdminTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.addGroupAdminTransactionData.getMember());
	}

	// Navigation

	public Account getOwner() {
		return this.getCreator();
	}

	public Account getMember() {
		if (this.memberAccount == null)
			this.memberAccount = new Account(this.repository, this.addGroupAdminTransactionData.getMember());

		return this.memberAccount;
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		int groupId = this.addGroupAdminTransactionData.getGroupId();
		String memberAddress = this.addGroupAdminTransactionData.getMember();

		// Check member address is valid
		if (!Crypto.isValidAddress(memberAddress))
			return ValidationResult.INVALID_ADDRESS;

		// Check group exists
		if (!this.repository.getGroupRepository().groupExists(groupId))
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

		// Check address is a group member
		if (!this.repository.getGroupRepository().memberExists(groupId, memberAddress))
			return ValidationResult.NOT_GROUP_MEMBER;

		// Check transaction creator is a group member
		if (!this.repository.getGroupRepository().memberExists(groupId, this.getCreator().getAddress()))
			return ValidationResult.NOT_GROUP_MEMBER;

		// Check group member is not already an admin
		if (this.repository.getGroupRepository().adminExists(groupId, memberAddress))
			return ValidationResult.ALREADY_GROUP_ADMIN;

		// Check group owner has enough funds
		if (owner.getConfirmedBalance(Asset.QORT) < this.addGroupAdminTransactionData.getFee())
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
		Group group = new Group(this.repository, this.addGroupAdminTransactionData.getGroupId());
		group.promoteToAdmin(this.addGroupAdminTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group adminship
		Group group = new Group(this.repository, this.addGroupAdminTransactionData.getGroupId());
		group.unpromoteToAdmin(this.addGroupAdminTransactionData);
	}

}
