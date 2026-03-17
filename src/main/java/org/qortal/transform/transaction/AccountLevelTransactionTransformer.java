package org.qortal.transform.transaction;

import com.google.common.primitives.Longs;
import org.qortal.data.transaction.AccountLevelTransactionData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


public class AccountLevelTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int TARGET_LENGTH = ADDRESS_LENGTH;
	private static final int LEVEL_LENGTH = BYTE_LENGTH;

	private static final int EXTRAS_LENGTH = TARGET_LENGTH + LEVEL_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.ACCOUNT_LEVEL.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("account's public key", TransformationType.PUBLIC_KEY);
		layout.add("target account's address", TransformationType.ADDRESS);
		layout.add("level", TransformationType.BYTE);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String target = Serialization.deserializeAddress(byteBuffer);

		byte level = byteBuffer.get();

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, signature);

		return new AccountLevelTransactionData(baseTransactionData, target, level);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return getBaseLength(transactionData) + EXTRAS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			AccountLevelTransactionData accountLevelTransactionData = (AccountLevelTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, accountLevelTransactionData.getTarget());

			bytes.write(accountLevelTransactionData.getLevel());

			bytes.write(Longs.toByteArray(accountLevelTransactionData.getFee()));

			if (accountLevelTransactionData.getSignature() != null)
				bytes.write(accountLevelTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
