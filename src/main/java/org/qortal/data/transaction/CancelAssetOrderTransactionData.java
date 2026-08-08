package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CancelAssetOrderTransactionData extends TransactionData {

	// Properties
	@Schema(description = "order ID to cancel", example = "real_order_ID_in_base58")
	private byte[] orderId;

	// Constructors

	// For JAXB
	protected CancelAssetOrderTransactionData() {
		super(TransactionType.CANCEL_ASSET_ORDER);
	}

	public CancelAssetOrderTransactionData(BaseTransactionData baseTransactionData, byte[] orderId) {
		super(Transaction.TransactionType.CANCEL_ASSET_ORDER, baseTransactionData);

		this.orderId = orderId;
	}

	// Getters/Setters

	public byte[] getOrderId() {
		return this.orderId;
	}

	// Re-expose creatorPublicKey for this transaction type for JAXB
	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "order creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public byte[] getOrderCreatorPublicKey() {
		return this.creatorPublicKey;
	}

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "order creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public void setOrderCreatorPublicKey(byte[] creatorPublicKey) {
		this.creatorPublicKey = creatorPublicKey;
	}

}
