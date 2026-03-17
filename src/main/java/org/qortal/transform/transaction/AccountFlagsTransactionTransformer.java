package org.qortal.transform.transaction;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.data.transaction.AccountFlagsTransactionData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AccountFlagsTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int TARGET_LENGTH = ADDRESS_LENGTH;
	private static final int AND_MASK_LENGTH = INT_LENGTH;
	private static final int OR_MASK_LENGTH = INT_LENGTH;
	private static final int XOR_MASK_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = TARGET_LENGTH + AND_MASK_LENGTH + OR_MASK_LENGTH + XOR_MASK_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.ACCOUNT_FLAGS.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("account's public key", TransformationType.PUBLIC_KEY);
		layout.add("target account's address", TransformationType.ADDRESS);
		layout.add("flags AND mask", TransformationType.INT);
		layout.add("flags OR mask", TransformationType.INT);
		layout.add("flags XOR mask", TransformationType.INT);
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

		int andMask = byteBuffer.getInt();
		int orMask = byteBuffer.getInt();
		int xorMask = byteBuffer.getInt();

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, signature);

		return new AccountFlagsTransactionData(baseTransactionData, target, andMask, orMask, xorMask);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return getBaseLength(transactionData) + EXTRAS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			AccountFlagsTransactionData accountFlagsTransactionData = (AccountFlagsTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, accountFlagsTransactionData.getTarget());

			bytes.write(Ints.toByteArray(accountFlagsTransactionData.getAndMask()));
			bytes.write(Ints.toByteArray(accountFlagsTransactionData.getOrMask()));
			bytes.write(Ints.toByteArray(accountFlagsTransactionData.getXorMask()));

			bytes.write(Longs.toByteArray(accountFlagsTransactionData.getFee()));

			if (accountFlagsTransactionData.getSignature() != null)
				bytes.write(accountFlagsTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
