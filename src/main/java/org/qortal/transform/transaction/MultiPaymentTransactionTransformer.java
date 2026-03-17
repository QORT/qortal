package org.qortal.transform.transaction;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.data.PaymentData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.MultiPaymentTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.PaymentTransformer;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MultiPaymentTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int PAYMENTS_COUNT_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = PAYMENTS_COUNT_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.MULTI_PAYMENT.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("sender's public key", TransformationType.PUBLIC_KEY);
		layout.add("number of payments", TransformationType.INT);
		layout.add("* recipient", TransformationType.ADDRESS);
		layout.add("* asset ID of payment", TransformationType.LONG);
		layout.add("* payment amount", TransformationType.AMOUNT);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int paymentsCount = byteBuffer.getInt();

		List<PaymentData> payments = new ArrayList<>();
		for (int i = 0; i < paymentsCount; ++i)
			payments.add(PaymentTransformer.fromByteBuffer(byteBuffer));

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, senderPublicKey, fee, signature);

		return new MultiPaymentTransactionData(baseTransactionData, payments);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + multiPaymentTransactionData.getPayments().size() * PaymentTransformer.getDataLength();
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			List<PaymentData> payments = multiPaymentTransactionData.getPayments();
			bytes.write(Ints.toByteArray(payments.size()));

			for (PaymentData paymentData : payments)
				bytes.write(PaymentTransformer.toBytes(paymentData));

			bytes.write(Longs.toByteArray(multiPaymentTransactionData.getFee()));

			if (multiPaymentTransactionData.getSignature() != null)
				bytes.write(multiPaymentTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
