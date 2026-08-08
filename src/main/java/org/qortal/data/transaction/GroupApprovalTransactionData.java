package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(
	allOf = {
		TransactionData.class
	}
)
public class GroupApprovalTransactionData extends TransactionData {

	// Properties
	@Schema(
		description = "admin's public key",
		example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP"
	)
	private byte[] adminPublicKey;
	@Schema(
		description = "transaction pending approval",
		example = "transaction_signature"
	)
	private byte[] pendingSignature;
	@Schema(
		description = "approval decision",
		example = "true"
	)
	private boolean approval;
	/** Reference to prior GROUP_APPROVAL transaction, used to rebuild approval status during orphaning. */
	// For internal use when orphaning
	@XmlTransient
	@Schema(
		hidden = true
	)
	private byte[] priorReference;

	// Constructors

	// For JAXB
	protected GroupApprovalTransactionData() {
		super(TransactionType.GROUP_APPROVAL);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	/** From repository */
	public GroupApprovalTransactionData(BaseTransactionData baseTransactionData, byte[] pendingSignature, boolean approval, byte[] priorReference) {
		super(TransactionType.GROUP_APPROVAL, baseTransactionData);

		this.adminPublicKey = baseTransactionData.creatorPublicKey;
		this.pendingSignature = pendingSignature;
		this.approval = approval;
		this.priorReference = priorReference;
	}

	/** From network/API */
	public GroupApprovalTransactionData(BaseTransactionData baseTransactionData, byte[] pendingSignature, boolean approval) {
		this(baseTransactionData, pendingSignature, approval, null);
	}

	// Getters / setters

	public byte[] getAdminPublicKey() {
		return this.adminPublicKey;
	}

	public byte[] getPendingSignature() {
		return this.pendingSignature;
	}

	public boolean getApproval() {
		return this.approval;
	}

	public byte[] getPriorReference() {
		return this.priorReference;
	}

	public void setPriorReference(byte[] priorReference) {
		this.priorReference = priorReference;
	}

}
