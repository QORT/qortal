package org.qortal.transform.transaction;

import com.google.common.base.Utf8;
import com.google.common.primitives.Longs;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.BuyNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.naming.Name;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class BuyNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int SELLER_LENGTH = ADDRESS_LENGTH;

	private static final int EXTRAS_LENGTH = NAME_SIZE_LENGTH + AMOUNT_LENGTH + SELLER_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.BUY_NAME.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("buyer's public key", TransformationType.PUBLIC_KEY);
		layout.add("name length", TransformationType.INT);
		layout.add("name", TransformationType.STRING);
		layout.add("buy price", TransformationType.AMOUNT);
		layout.add("seller", TransformationType.ADDRESS);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] buyerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);

		long amount = byteBuffer.getLong();

		String seller = Serialization.deserializeAddress(byteBuffer);

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, buyerPublicKey, fee, signature);

		return new BuyNameTransactionData(baseTransactionData, name, amount, seller);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(buyNameTransactionData.getName());
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeSizedString(bytes, buyNameTransactionData.getName());

			bytes.write(Longs.toByteArray(buyNameTransactionData.getAmount()));

			Serialization.serializeAddress(bytes, buyNameTransactionData.getSeller());

			bytes.write(Longs.toByteArray(buyNameTransactionData.getFee()));

			if (buyNameTransactionData.getSignature() != null)
				bytes.write(buyNameTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
