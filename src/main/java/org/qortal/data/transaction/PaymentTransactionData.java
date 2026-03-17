package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema( allOf = { TransactionData.class } )
public class PaymentTransactionData extends TransactionData {

	// Properties

	@Schema(description = "sender's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] senderPublicKey;

	@Schema(description = "recipient's address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	private String recipient;

	@Schema(description = "amount to send", example = "123.456")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long amount;

	// Constructors

	// For JAXB
	protected PaymentTransactionData() {
		super(TransactionType.PAYMENT);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	/** From repository */
	public PaymentTransactionData(BaseTransactionData baseTransactionData, String recipient, long amount) {
		super(TransactionType.PAYMENT, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.recipient = recipient;
		this.amount = amount;
	}

	// Getters/Setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public long getAmount() {
		return this.amount;
	}

}
