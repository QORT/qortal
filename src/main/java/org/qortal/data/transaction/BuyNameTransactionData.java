package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class BuyNameTransactionData extends TransactionData {

	// Properties

	@Schema(description = "buyer's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] buyerPublicKey;

	@Schema(description = "which name to buy", example = "my-name")
	private String name;

	@Schema(description = "selling price", example = "123.456")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long amount;

	@Schema(description = "seller's address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	private String seller;

	// For internal use when orphaning
	@XmlTransient
	@Schema(hidden = true)
	private byte[] nameReference;

	// Constructors

	// For JAXB
	protected BuyNameTransactionData() {
		super(TransactionType.BUY_NAME);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.buyerPublicKey;
	}

	/** From repository */
	public BuyNameTransactionData(BaseTransactionData baseTransactionData,
			String name, long amount, String seller, byte[] nameReference) {
		super(TransactionType.BUY_NAME, baseTransactionData);

		this.buyerPublicKey = baseTransactionData.creatorPublicKey;
		this.name = name;
		this.amount = amount;
		this.seller = seller;
		this.nameReference = nameReference;
	}

	/** From network/API */
	public BuyNameTransactionData(BaseTransactionData baseTransactionData, String name, long amount, String seller) {
		this(baseTransactionData, name, amount, seller, null);
	}

	// Getters / setters

	public byte[] getBuyerPublicKey() {
		return this.buyerPublicKey;
	}

	public String getName() {
		return this.name;
	}

	public long getAmount() {
		return this.amount;
	}

	public String getSeller() {
		return this.seller;
	}

	public byte[] getNameReference() {
		return this.nameReference;
	}

	public void setNameReference(byte[] nameReference) {
		this.nameReference = nameReference;
	}

}
