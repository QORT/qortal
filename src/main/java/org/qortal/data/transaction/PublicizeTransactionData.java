package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class PublicizeTransactionData extends TransactionData {

	// Properties
	@Schema(description = "sender's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] senderPublicKey;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private int nonce;

	// Constructors

	// For JAXB
	protected PublicizeTransactionData() {
		super(TransactionType.PUBLICIZE);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	public PublicizeTransactionData(BaseTransactionData baseTransactionData, int nonce) {
		super(TransactionType.PUBLICIZE, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.nonce = nonce;
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

}
