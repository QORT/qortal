package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class TransferPrivsTransactionData extends TransactionData {

	// Properties
	@Schema(example = "sender_public_key")
	private byte[] senderPublicKey;

	private String recipient;

	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private Integer previousSenderFlags;
	@XmlTransient
	@Schema(hidden = true)
	private Integer previousRecipientFlags;

	@XmlTransient
	@Schema(hidden = true)
	private Integer previousSenderBlocksMintedAdjustment;
	@XmlTransient
	@Schema(hidden = true)
	private Integer previousSenderBlocksMinted;

	// Constructors

	// For JAXB
	protected TransferPrivsTransactionData() {
		super(TransactionType.TRANSFER_PRIVS);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	/** Constructs using data from repository. */
	public TransferPrivsTransactionData(BaseTransactionData baseTransactionData, String recipient,
			Integer previousSenderFlags, Integer previousRecipientFlags,
			Integer previousSenderBlocksMintedAdjustment, Integer previousSenderBlocksMinted) {
		super(TransactionType.TRANSFER_PRIVS, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.recipient = recipient;

		this.previousSenderFlags = previousSenderFlags;
		this.previousRecipientFlags = previousRecipientFlags;

		this.previousSenderBlocksMintedAdjustment = previousSenderBlocksMintedAdjustment;
		this.previousSenderBlocksMinted = previousSenderBlocksMinted;
	}

	/** Constructs using data from network/API. */
	public TransferPrivsTransactionData(BaseTransactionData baseTransactionData, String recipient) {
		this(baseTransactionData, recipient, null, null, null, null);
	}

	// Getters/setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public Integer getPreviousSenderFlags() {
		return this.previousSenderFlags;
	}

	public void setPreviousSenderFlags(Integer previousSenderFlags) {
		this.previousSenderFlags = previousSenderFlags;
	}

	public Integer getPreviousRecipientFlags() {
		return this.previousRecipientFlags;
	}

	public void setPreviousRecipientFlags(Integer previousRecipientFlags) {
		this.previousRecipientFlags = previousRecipientFlags;
	}

	public Integer getPreviousSenderBlocksMintedAdjustment() {
		return this.previousSenderBlocksMintedAdjustment;
	}

	public void setPreviousSenderBlocksMintedAdjustment(Integer previousSenderBlocksMintedAdjustment) {
		this.previousSenderBlocksMintedAdjustment = previousSenderBlocksMintedAdjustment;
	}

	public Integer getPreviousSenderBlocksMinted() {
		return this.previousSenderBlocksMinted;
	}

	public void setPreviousSenderBlocksMinted(Integer previousSenderBlocksMinted) {
		this.previousSenderBlocksMinted = previousSenderBlocksMinted;
	}

}
