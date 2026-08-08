package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.data.PaymentData;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.List;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class MultiPaymentTransactionData extends TransactionData {

	// Properties
	private byte[] senderPublicKey;
	private List<PaymentData> payments;

	// Constructors

	// For JAXB
	protected MultiPaymentTransactionData() {
		super(TransactionType.MULTI_PAYMENT);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	public MultiPaymentTransactionData(BaseTransactionData baseTransactionData, List<PaymentData> payments) {
		super(Transaction.TransactionType.MULTI_PAYMENT, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.payments = payments;
	}

	// Getters/setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public List<PaymentData> getPayments() {
		return this.payments;
	}

}
