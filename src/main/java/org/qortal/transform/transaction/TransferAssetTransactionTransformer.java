package org.qortal.transform.transaction;

import com.google.common.primitives.Longs;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.TransferAssetTransactionData;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TransferAssetTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;

	private static final int EXTRAS_LENGTH = RECIPIENT_LENGTH + ASSET_ID_LENGTH + AMOUNT_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.TRANSFER_ASSET.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("asset owner's public key", TransformationType.PUBLIC_KEY);
		layout.add("recipient", TransformationType.ADDRESS);
		layout.add("asset ID", TransformationType.LONG);
		layout.add("asset quantity", TransformationType.AMOUNT);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] senderPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String recipient = Serialization.deserializeAddress(byteBuffer);

		long assetId = byteBuffer.getLong();

		long amount = byteBuffer.getLong();

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, senderPublicKey, fee, signature);

		return new TransferAssetTransactionData(baseTransactionData, recipient, amount, assetId);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		return getBaseLength(transactionData) + EXTRAS_LENGTH;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, transferAssetTransactionData.getRecipient());

			bytes.write(Longs.toByteArray(transferAssetTransactionData.getAssetId()));

			bytes.write(Longs.toByteArray(transferAssetTransactionData.getAmount()));

			bytes.write(Longs.toByteArray(transferAssetTransactionData.getFee()));

			if (transferAssetTransactionData.getSignature() != null)
				bytes.write(transferAssetTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
