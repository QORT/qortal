package org.qortal.transform.transaction;

import com.google.common.base.Utf8;
import com.google.common.primitives.Longs;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.naming.Name;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class RegisterNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = NAME_SIZE_LENGTH + DATA_SIZE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.REGISTER_NAME.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("name registrant's public key", TransformationType.PUBLIC_KEY);
		layout.add("name length", TransformationType.INT);
		layout.add("name", TransformationType.STRING);
		layout.add("data length", TransformationType.INT);
		layout.add("data", TransformationType.STRING);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] registrantPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);

		String data = Serialization.deserializeSizedString(byteBuffer, Name.MAX_DATA_SIZE);

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, registrantPublicKey, fee, signature);

		return new RegisterNameTransactionData(baseTransactionData, name, data);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(registerNameTransactionData.getName())
				+ Utf8.encodedLength(registerNameTransactionData.getData());
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeSizedString(bytes, registerNameTransactionData.getName());

			Serialization.serializeSizedString(bytes, registerNameTransactionData.getData());

			bytes.write(Longs.toByteArray(registerNameTransactionData.getFee()));

			if (registerNameTransactionData.getSignature() != null)
				bytes.write(registerNameTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
