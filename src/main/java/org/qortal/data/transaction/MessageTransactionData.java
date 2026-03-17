package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class MessageTransactionData extends TransactionData {

	// Properties

	private byte[] senderPublicKey;

	private int version;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private int nonce;

	// Not always present
	private String recipient;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long amount;

	// Not present if amount is zero
	private Long assetId;

	private byte[] data;

	private boolean isText;

	private boolean isEncrypted;

	// Constructors

	// For JAXB
	protected MessageTransactionData() {
		super(TransactionType.MESSAGE);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	public MessageTransactionData(BaseTransactionData baseTransactionData,
			int version, int nonce, String recipient, long amount, Long assetId, byte[] data, boolean isText, boolean isEncrypted) {
		super(TransactionType.MESSAGE, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.version = version;
		this.nonce = nonce;
		this.recipient = recipient;
		this.amount = amount;
		this.assetId = assetId;
		this.data = data;
		this.isText = isText;
		this.isEncrypted = isEncrypted;
	}

	// Getters/Setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public int getVersion() {
		return this.version;
	}

	public int getNonce() {
		return this.nonce;
	}

	public void setNonce(int nonce) {
		this.nonce = nonce;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public long getAmount() {
		return this.amount;
	}

	public Long getAssetId() {
		return this.assetId;
	}

	public byte[] getData() {
		return this.data;
	}

	public boolean isText() {
		return this.isText;
	}

	public boolean isEncrypted() {
		return this.isEncrypted;
	}

}
