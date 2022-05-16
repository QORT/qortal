package org.qortal.data.transaction;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qortal.transaction.PresenceTransaction.PresenceType;
import org.qortal.transaction.Transaction.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class PresenceTransactionData extends TransactionData {

	// Properties
	@Schema(description = "sender's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] senderPublicKey;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private int nonce;

	private PresenceType presenceType;

	@Schema(description = "timestamp signature", example = "2yGEbwRFyhPZZckKA")
	private byte[] timestampSignature;

	// Constructors

	// For JAXB
	protected PresenceTransactionData() {
		super(TransactionType.PRESENCE);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	public PresenceTransactionData(BaseTransactionData baseTransactionData,
			int nonce, PresenceType presenceType, byte[] timestampSignature) {
		super(TransactionType.PRESENCE, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.nonce = nonce;
		this.presenceType = presenceType;
		this.timestampSignature = timestampSignature;
	}

	// Getters/Setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public int getNonce() {
		return this.nonce;
	}

	public void setNonce(int nonce) {
		this.nonce = nonce;
	}

	public PresenceType getPresenceType() {
		return this.presenceType;
	}

	public byte[] getTimestampSignature() {
		return this.timestampSignature;
	}

}
