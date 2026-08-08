package org.qortal.transform.transaction;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.block.BlockChain;
import org.qortal.crypto.Crypto;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.ChatTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transaction.ChatTransaction;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ChatTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int NONCE_LENGTH = INT_LENGTH;
	private static final int HAS_RECIPIENT_LENGTH = BOOLEAN_LENGTH;
	private static final int RECIPIENT_LENGTH = ADDRESS_LENGTH;
	private static final int HAS_CHAT_REFERENCE_LENGTH = BOOLEAN_LENGTH;
	private static final int CHAT_REFERENCE_LENGTH = SIGNATURE_LENGTH;
	private static final int DATA_SIZE_LENGTH = INT_LENGTH;
	private static final int IS_TEXT_LENGTH = BOOLEAN_LENGTH;
	private static final int IS_ENCRYPTED_LENGTH = BOOLEAN_LENGTH;

	private static final int EXTRAS_LENGTH = NONCE_LENGTH + HAS_RECIPIENT_LENGTH + DATA_SIZE_LENGTH + IS_ENCRYPTED_LENGTH + IS_TEXT_LENGTH + HAS_CHAT_REFERENCE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.CHAT.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("sender's public key", TransformationType.PUBLIC_KEY);
		layout.add("proof-of-work nonce", TransformationType.INT);
		layout.add("has recipient?", TransformationType.BOOLEAN);
		layout.add("? recipient", TransformationType.ADDRESS);
		layout.add("message length", TransformationType.INT);
		layout.add("message", TransformationType.DATA);
		layout.add("is message encrypted?", TransformationType.BOOLEAN);
		layout.add("is message text?", TransformationType.BOOLEAN);
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

		boolean hasRecipient = byteBuffer.get() != 0;
		String recipient = hasRecipient ? Serialization.deserializeAddress(byteBuffer) : null;

		int dataSize = byteBuffer.getInt();
		// Don't allow invalid dataSize here to avoid run-time issues
		if (dataSize > ChatTransaction.MAX_DATA_SIZE)
			throw new TransformationException("ChatTransaction data size too large");

		byte[] data = new byte[dataSize];
		byteBuffer.get(data);

		boolean isEncrypted = byteBuffer.get() != 0;

		boolean isText = byteBuffer.get() != 0;

		long fee = byteBuffer.getLong();

		byte[] chatReference = null;

		if (timestamp >= BlockChain.getInstance().getChatReferenceTimestamp()) {
			boolean hasChatReference = byteBuffer.get() != 0;

			if (hasChatReference) {
				chatReference = new byte[CHAT_REFERENCE_LENGTH];
				byteBuffer.get(chatReference);
			}
		}

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, senderPublicKey, fee, signature);

		String sender = Crypto.toAddress(senderPublicKey);
		return new ChatTransactionData(baseTransactionData, sender, nonce, recipient, chatReference, data, isText, isEncrypted);
	}

	public static int getDataLength(TransactionData transactionData) {
		ChatTransactionData chatTransactionData = (ChatTransactionData) transactionData;

		int dataLength = getBaseLength(transactionData) + EXTRAS_LENGTH + chatTransactionData.getData().length;

		if (chatTransactionData.getRecipient() != null)
			dataLength += RECIPIENT_LENGTH;

		if (chatTransactionData.getChatReference() != null)
			dataLength += CHAT_REFERENCE_LENGTH;

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			ChatTransactionData chatTransactionData = (ChatTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			bytes.write(Ints.toByteArray(chatTransactionData.getNonce()));

			if (chatTransactionData.getRecipient() != null) {
				bytes.write((byte) 1);
				Serialization.serializeAddress(bytes, chatTransactionData.getRecipient());
			} else {
				bytes.write((byte) 0);
			}

			bytes.write(Ints.toByteArray(chatTransactionData.getData().length));

			bytes.write(chatTransactionData.getData());

			bytes.write((byte) (chatTransactionData.getIsEncrypted() ? 1 : 0));

			bytes.write((byte) (chatTransactionData.getIsText() ? 1 : 0));

			bytes.write(Longs.toByteArray(chatTransactionData.getFee()));

			if (transactionData.getTimestamp() >= BlockChain.getInstance().getChatReferenceTimestamp()) {
				// Include chat reference if it's not null
				if (chatTransactionData.getChatReference() != null) {
					bytes.write((byte) 1);
					bytes.write(chatTransactionData.getChatReference());
				} else {
					bytes.write((byte) 0);
				}
			}

			if (chatTransactionData.getSignature() != null)
				bytes.write(chatTransactionData.getSignature());

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
