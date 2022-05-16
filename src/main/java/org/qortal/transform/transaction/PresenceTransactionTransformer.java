package org.qortal.transform.transaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.PresenceTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transaction.PresenceTransaction.PresenceType;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class PresenceTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int NONCE_LENGTH = INT_LENGTH;
	private static final int PRESENCE_TYPE_LENGTH = BYTE_LENGTH;
	private static final int TIMESTAMP_SIGNATURE_LENGTH = SIGNATURE_LENGTH;

	private static final int EXTRAS_LENGTH = NONCE_LENGTH + PRESENCE_TYPE_LENGTH + TIMESTAMP_SIGNATURE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.PRESENCE.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("sender's public key", TransformationType.PUBLIC_KEY);
		layout.add("proof-of-work nonce", TransformationType.INT);
		layout.add("presence type (reward-share=0, trade-bot=1)", TransformationType.BYTE);
		layout.add("timestamp-signature", TransformationType.SIGNATURE);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int nonce = byteBuffer.getInt();

		PresenceType presenceType = PresenceType.valueOf(byteBuffer.get());

		byte[] timestampSignature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(timestampSignature);

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, senderPublicKey, fee, signature);

		return new PresenceTransactionData(baseTransactionData, nonce, presenceType, timestampSignature);
	}

	public static int getDataLength(TransactionData transactionData) {
		return getBaseLength(transactionData) + EXTRAS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			PresenceTransactionData presenceTransactionData = (PresenceTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			bytes.write(Ints.toByteArray(presenceTransactionData.getNonce()));

			bytes.write(presenceTransactionData.getPresenceType().value);

			bytes.write(presenceTransactionData.getTimestampSignature());

			bytes.write(Longs.toByteArray(presenceTransactionData.getFee()));

			if (presenceTransactionData.getSignature() != null)
				bytes.write(presenceTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

	public static void clearNonce(byte[] transactionBytes) {
		int nonceIndex = TYPE_LENGTH + TIMESTAMP_LENGTH + GROUPID_LENGTH + REFERENCE_LENGTH + PUBLIC_KEY_LENGTH;

		transactionBytes[nonceIndex++] = (byte) 0;
		transactionBytes[nonceIndex++] = (byte) 0;
		transactionBytes[nonceIndex++] = (byte) 0;
		transactionBytes[nonceIndex++] = (byte) 0;
	}

}
