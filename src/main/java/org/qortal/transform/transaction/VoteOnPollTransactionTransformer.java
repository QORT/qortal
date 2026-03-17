package org.qortal.transform.transaction;

import com.google.common.base.Utf8;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.VoteOnPollTransactionData;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;
import org.qortal.voting.Poll;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VoteOnPollTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int POLL_OPTION_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = NAME_SIZE_LENGTH + POLL_OPTION_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.VOTE_ON_POLL.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("voter's public key", TransformationType.PUBLIC_KEY);
		layout.add("poll name length", TransformationType.INT);
		layout.add("poll name", TransformationType.STRING);
		layout.add("poll option index (0+)", TransformationType.INT);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] voterPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String pollName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);

		int optionIndex = byteBuffer.getInt();
		if (optionIndex < 0 || optionIndex >= Poll.MAX_OPTIONS)
			throw new TransformationException("Invalid option number for VoteOnPollTransaction");

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, voterPublicKey, fee, signature);

		return new VoteOnPollTransactionData(baseTransactionData, pollName, optionIndex);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		VoteOnPollTransactionData voteOnPollTransactionData = (VoteOnPollTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(voteOnPollTransactionData.getPollName());
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			VoteOnPollTransactionData voteOnPollTransactionData = (VoteOnPollTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeSizedString(bytes, voteOnPollTransactionData.getPollName());

			bytes.write(Ints.toByteArray(voteOnPollTransactionData.getOptionIndex()));

			bytes.write(Longs.toByteArray(voteOnPollTransactionData.getFee()));

			if (voteOnPollTransactionData.getSignature() != null)
				bytes.write(voteOnPollTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
