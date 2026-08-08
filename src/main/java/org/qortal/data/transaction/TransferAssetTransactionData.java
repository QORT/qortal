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
public class TransferAssetTransactionData extends TransactionData {

	// Properties

	@Schema(example = "sender_public_key")
	private byte[] senderPublicKey;

	private String recipient;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long amount;

	private long assetId;

	// Used by API - not always present
	@Schema(accessMode = AccessMode.READ_ONLY)
	protected String assetName;

	// Constructors

	// For JAXB
	protected TransferAssetTransactionData() {
		super(TransactionType.TRANSFER_ASSET);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	/** Constructs using data from repository, including optional assetName. */
	public TransferAssetTransactionData(BaseTransactionData baseTransactionData, String recipient, long amount, long assetId, String assetName) {
		super(TransactionType.TRANSFER_ASSET, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.recipient = recipient;
		this.amount = amount;
		this.assetId = assetId;
		this.assetName = assetName;
	}

	/** Constructor excluding optional assetName. */
	public TransferAssetTransactionData(BaseTransactionData baseTransactionData, String recipient, long amount, long assetId) {
		this(baseTransactionData, recipient, amount, assetId, null);
	}

	// Getters/setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public long getAmount() {
		return this.amount;
	}

	public long getAssetId() {
		return this.assetId;
	}

}
