package org.qortal.data.transaction;


import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.account.NullAccount;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class ATTransactionData extends TransactionData {

	// Properties

	private String atAddress;

	private String recipient;

	// Not always present
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private Long amount;

	// Not always present
	private Long assetId;

	// Not always present
	private byte[] message;

	// Constructors

	// For JAXB
	protected ATTransactionData() {
		super(TransactionType.AT);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = NullAccount.PUBLIC_KEY;
	}

	/** Constructing from repository */
	public ATTransactionData(BaseTransactionData baseTransactionData, String atAddress, String recipient, Long amount, Long assetId, byte[] message) {
		super(TransactionType.AT, baseTransactionData);

		this.creatorPublicKey = NullAccount.PUBLIC_KEY;
		this.atAddress = atAddress;
		this.recipient = recipient;
		this.amount = amount;
		this.assetId = assetId;
		this.message = message;
	}

	/** Constructing a new MESSAGE-type AT transaction */
	public ATTransactionData(BaseTransactionData baseTransactionData, String atAddress, String recipient, byte[] message) {
		this(baseTransactionData, atAddress, recipient, null, null, message);
	}

	/** Constructing a new PAYMENT-type AT transaction */
	public ATTransactionData(BaseTransactionData baseTransactionData, String atAddress, String recipient, long amount, long assetId) {
		this(baseTransactionData, atAddress, recipient, amount, assetId, null);
	}

	// Getters/Setters

	public String getATAddress() {
		return this.atAddress;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public Long getAmount() {
		return this.amount;
	}

	public Long getAssetId() {
		return this.assetId;
	}

	public byte[] getMessage() {
		return this.message;
	}

}
