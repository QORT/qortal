package org.qortal.transaction;

import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.block.BlockChain;
import org.qortal.crypto.Crypto;
import org.qortal.data.group.GroupData;
import org.qortal.data.transaction.CancelGroupInviteTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CancelGroupInviteTransaction extends Transaction {

	// Properties

	private CancelGroupInviteTransactionData cancelGroupInviteTransactionData;
	private Account inviteeAccount = null;

	// Constructors

	public CancelGroupInviteTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.cancelGroupInviteTransactionData = (CancelGroupInviteTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.cancelGroupInviteTransactionData.getInvitee());
	}

	// Navigation

	public Account getAdmin() {
		return this.getCreator();
	}

	public Account getInvitee() {
		if (this.inviteeAccount == null)
			this.inviteeAccount = new Account(this.repository, this.cancelGroupInviteTransactionData.getInvitee());

		return this.inviteeAccount;
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		int groupId = this.cancelGroupInviteTransactionData.getGroupId();

		// Check invitee address is valid
		if (!Crypto.isValidAddress(this.cancelGroupInviteTransactionData.getInvitee()))
			return ValidationResult.INVALID_ADDRESS;

		GroupData groupData = this.repository.getGroupRepository().fromGroupId(groupId);

		// Check group exists
		if (groupData == null)
			return ValidationResult.GROUP_DOES_NOT_EXIST;

		Account admin = getAdmin();

		// Check admin is actually an admin
		if (!this.repository.getGroupRepository().adminExists(groupId, admin.getAddress()))
			return ValidationResult.NOT_GROUP_ADMIN;

		Account invitee = getInvitee();

		// Check invite exists
		if (!this.repository.getGroupRepository().inviteExists(groupId, invitee.getAddress()))
			return ValidationResult.INVITE_UNKNOWN;

		// Check creator has enough funds
		if (admin.getConfirmedBalance(Asset.QORT) < this.cancelGroupInviteTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		// if null ownership group, then check for admin approval
		if( this.repository.getBlockRepository().getBlockchainHeight() >= BlockChain.getInstance().getNullGroupMembershipHeight() ) {
			String groupOwner = this.repository.getGroupRepository().getOwner(groupId);
			boolean groupOwnedByNullAccount = Objects.equals(groupOwner, Group.NULL_OWNER_ADDRESS);

			// Require approval if transaction relates to a group owned by the null account
			if (groupOwnedByNullAccount && !this.needsGroupApproval())
				return ValidationResult.GROUP_APPROVAL_REQUIRED;
		}

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		// Update Group Membership
		Group group = new Group(this.repository, this.cancelGroupInviteTransactionData.getGroupId());
		group.cancelInvite(this.cancelGroupInviteTransactionData);

		// Save this transaction with updated member/admin references to transactions that can help restore state
		this.repository.getTransactionRepository().save(this.cancelGroupInviteTransactionData);
	}

	@Override
	public void orphan() throws DataException {
		// Revert group membership
		Group group = new Group(this.repository, this.cancelGroupInviteTransactionData.getGroupId());
		group.uncancelInvite(this.cancelGroupInviteTransactionData);

		// Save this transaction with removed member/admin references
		this.repository.getTransactionRepository().save(this.cancelGroupInviteTransactionData);
	}

}
