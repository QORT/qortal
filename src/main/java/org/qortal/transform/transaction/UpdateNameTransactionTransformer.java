package org.qortal.transform.transaction;

import com.google.common.base.Utf8;
import com.google.common.primitives.Longs;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.UpdateNameTransactionData;
import org.qortal.naming.Name;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class UpdateNameTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int NEW_NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int NEW_DATA_SIZE_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = NAME_SIZE_LENGTH + NEW_NAME_SIZE_LENGTH + NEW_DATA_SIZE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.UPDATE_NAME.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("name owner's public key", TransformationType.PUBLIC_KEY);
		layout.add("name length", TransformationType.INT);
		layout.add("name", TransformationType.STRING);
		layout.add("new name's length (0 for no change)", TransformationType.INT);
		layout.add("new name", TransformationType.STRING);
		layout.add("new data length (0 for no change)", TransformationType.INT);
		layout.add("new data", TransformationType.STRING);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] ownerPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String name = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);

		String newName = Serialization.deserializeSizedString(byteBuffer, Name.MAX_NAME_SIZE);

		String newData = Serialization.deserializeSizedString(byteBuffer, Name.MAX_DATA_SIZE);

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, ownerPublicKey, fee, signature);

		return new UpdateNameTransactionData(baseTransactionData, name, newName, newData);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(updateNameTransactionData.getName())
				+ Utf8.encodedLength(updateNameTransactionData.getNewName())
				+ Utf8.encodedLength(updateNameTransactionData.getNewData());
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeSizedString(bytes, updateNameTransactionData.getName());

			Serialization.serializeSizedString(bytes, updateNameTransactionData.getNewName());

			Serialization.serializeSizedString(bytes, updateNameTransactionData.getNewData());

			bytes.write(Longs.toByteArray(updateNameTransactionData.getFee()));

			if (updateNameTransactionData.getSignature() != null)
				bytes.write(updateNameTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
