package org.qortal.data.transaction;

import java.math.BigInteger;
import java.util.Arrays;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorNode;
import org.qortal.crypto.Crypto;
import org.qortal.transaction.Transaction.ApprovalStatus;
import org.qortal.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

/*
 * If you encounter an error like:
 * 
 * MessageBodyWriter not found for <some class>
 * 
 * then chances are that class is missing a no-argument constructor!
 */

@XmlSeeAlso({GenesisTransactionData.class, PaymentTransactionData.class, RegisterNameTransactionData.class, UpdateNameTransactionData.class,
	SellNameTransactionData.class, CancelSellNameTransactionData.class, BuyNameTransactionData.class,
	CreatePollTransactionData.class, VoteOnPollTransactionData.class, ArbitraryTransactionData.class,
	IssueAssetTransactionData.class, TransferAssetTransactionData.class,
	CreateAssetOrderTransactionData.class, CancelAssetOrderTransactionData.class,
	MultiPaymentTransactionData.class, DeployAtTransactionData.class, MessageTransactionData.class, ATTransactionData.class,
	CreateGroupTransactionData.class, UpdateGroupTransactionData.class,
	AddGroupAdminTransactionData.class, RemoveGroupAdminTransactionData.class,
	GroupBanTransactionData.class, CancelGroupBanTransactionData.class,
	GroupKickTransactionData.class, GroupInviteTransactionData.class,
	JoinGroupTransactionData.class, LeaveGroupTransactionData.class,
	GroupApprovalTransactionData.class, SetGroupTransactionData.class,
	UpdateAssetTransactionData.class,
	AccountFlagsTransactionData.class, RewardShareTransactionData.class,
	AccountLevelTransactionData.class, ChatTransactionData.class, PresenceTransactionData.class
})
//All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
// EclipseLink JAXB (MOXy) specific: use "type" field to determine subclass
@XmlDiscriminatorNode("type")
public abstract class TransactionData {

	// Properties shared with all transaction types
	@Schema(accessMode = AccessMode.READ_ONLY, hidden = true)
	protected TransactionType type;

	@XmlTransient // represented in transaction-specific properties
	@Schema(hidden = true)
	protected byte[] creatorPublicKey;

	@Schema(description = "timestamp when transaction created, in milliseconds since unix epoch", example = "__unix_epoch_time_milliseconds__")
	protected long timestamp;

	@Schema(description = "sender's last transaction ID", example = "real_transaction_reference_in_base58")
	protected byte[] reference;

	@Schema(description = "fee for processing transaction", example = "0.0001")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	protected Long fee; // can be null if fee not calculated yet

	@Schema(accessMode = AccessMode.READ_ONLY, description = "signature for transaction's raw bytes, using sender's private key", example = "real_transaction_signature_in_base58")
	protected byte[] signature;

	@Schema(description = "groupID for this transaction")
	protected int txGroupId;

	// Not always present
	@Schema(accessMode = AccessMode.READ_ONLY, hidden = true, description = "height of block containing transaction")
	protected Integer blockHeight;

	// Not always present
	@Schema(accessMode = AccessMode.READ_ONLY, description = "group-approval status")
	protected ApprovalStatus approvalStatus;

	// Not always present
	@Schema(accessMode = AccessMode.READ_ONLY, hidden = true, description = "block height when transaction approved")
	protected Integer approvalHeight;

	// Constructors

	// For JAXB
	protected TransactionData() {
	}

	// For JAXB
	protected TransactionData(TransactionType type) {
		this.type = type;
	}

	/** Constructor for use by transaction subclasses. */
	protected TransactionData(TransactionType type, BaseTransactionData baseTransactionData) {
		this.type = type;

		this.timestamp = baseTransactionData.timestamp;
		this.txGroupId = baseTransactionData.txGroupId;
		this.reference = baseTransactionData.reference;
		this.creatorPublicKey = baseTransactionData.creatorPublicKey;
		this.fee = baseTransactionData.fee;
		this.signature = baseTransactionData.signature;
		this.blockHeight = baseTransactionData.blockHeight;
		this.approvalStatus = baseTransactionData.approvalStatus;
		this.approvalHeight = baseTransactionData.approvalHeight;
	}

	// Getters/setters

	public TransactionType getType() {
		return this.type;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public int getTxGroupId() {
		return this.txGroupId;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

	public byte[] getCreatorPublicKey() {
		return this.creatorPublicKey;
	}

	@XmlTransient
	public void setCreatorPublicKey(byte[] creatorPublicKey) {
		this.creatorPublicKey = creatorPublicKey;
	}

	public Long getFee() {
		return this.fee;
	}

	public void setFee(Long fee) {
		this.fee = fee;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	public Integer getBlockHeight() {
		return this.blockHeight;
	}

	@XmlTransient
	public void setBlockHeight(Integer blockHeight) {
		this.blockHeight = blockHeight;
	}

	public ApprovalStatus getApprovalStatus() {
		return approvalStatus;
	}

	@XmlTransient
	public void setApprovalStatus(ApprovalStatus approvalStatus) {
		this.approvalStatus = approvalStatus;
	}

	public Integer getApprovalHeight() {
		return this.approvalHeight;
	}

	@XmlTransient
	public void setApprovalHeight(Integer approvalHeight) {
		this.approvalHeight = approvalHeight;
	}

	// JAXB special

	@XmlElement(name = "creatorAddress")
	protected String getCreatorAddress() {
		return Crypto.toAddress(this.creatorPublicKey);
	}

	// Comparison

	@Override
	public int hashCode() {
		byte[] bytes = this.signature;

		// No signature? Use reference instead
		if (bytes == null)
			bytes = this.reference;

		return new BigInteger(bytes).intValue();
	}

	@Override
	public boolean equals(Object other) {
		// Comparing exact same object
		if (this == other)
			return true;

		// If we don't have a signature then fail
		if (this.signature == null)
			return false;

		if (!(other instanceof TransactionData))
			return false;

		TransactionData otherTransactionData = (TransactionData) other;

		// If other transactionData has no signature then fail
		if (otherTransactionData.signature == null)
			return false;

		return Arrays.equals(this.signature, otherTransactionData.signature);
	}

}
