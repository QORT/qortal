package org.qortal.transform.transaction;

import com.google.common.base.Utf8;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CreatePollTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;
import org.qortal.voting.Poll;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CreatePollTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int OWNER_LENGTH = ADDRESS_LENGTH;
	private static final int NAME_SIZE_LENGTH = INT_LENGTH;
	private static final int DESCRIPTION_SIZE_LENGTH = INT_LENGTH;
	private static final int OPTIONS_SIZE_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = OWNER_LENGTH + NAME_SIZE_LENGTH + DESCRIPTION_SIZE_LENGTH + OPTIONS_SIZE_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.CREATE_POLL.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("poll creator's public key", TransformationType.PUBLIC_KEY);
		layout.add("poll owner's address", TransformationType.ADDRESS);
		layout.add("poll name length", TransformationType.INT);
		layout.add("poll name", TransformationType.STRING);
		layout.add("poll description length", TransformationType.INT);
		layout.add("poll description", TransformationType.STRING);
		layout.add("number of options", TransformationType.INT);
		layout.add("* poll option length", TransformationType.INT);
		layout.add("* poll option", TransformationType.STRING);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] creatorPublicKey = Serialization.deserializePublicKey(byteBuffer);

		String owner = Serialization.deserializeAddress(byteBuffer);

		String pollName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);

		String description = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_DESCRIPTION_SIZE);

		int optionsCount = byteBuffer.getInt();
		if (optionsCount < 1 || optionsCount > Poll.MAX_OPTIONS)
			throw new TransformationException("Invalid number of options for CreatePollTransaction");

		List<PollOptionData> pollOptions = new ArrayList<>();
		for (int optionIndex = 0; optionIndex < optionsCount; ++optionIndex) {
			String optionName = Serialization.deserializeSizedString(byteBuffer, Poll.MAX_NAME_SIZE);

			pollOptions.add(new PollOptionData(optionName));
		}

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, signature);

		return new CreatePollTransactionData(baseTransactionData, owner, pollName, description, pollOptions);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		CreatePollTransactionData createPollTransactionData = (CreatePollTransactionData) transactionData;

		int dataLength = getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(createPollTransactionData.getPollName())
				+ Utf8.encodedLength(createPollTransactionData.getDescription());

		// Add lengths for each poll options
		for (PollOptionData pollOptionData : createPollTransactionData.getPollOptions())
			// option-string-length, option-string
			dataLength += INT_LENGTH + Utf8.encodedLength(pollOptionData.getOptionName());

		return dataLength;
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			CreatePollTransactionData createPollTransactionData = (CreatePollTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			Serialization.serializeAddress(bytes, createPollTransactionData.getOwner());

			Serialization.serializeSizedString(bytes, createPollTransactionData.getPollName());

			Serialization.serializeSizedString(bytes, createPollTransactionData.getDescription());

			List<PollOptionData> pollOptions = createPollTransactionData.getPollOptions();
			bytes.write(Ints.toByteArray(pollOptions.size()));

			for (PollOptionData pollOptionData : pollOptions)
				Serialization.serializeSizedString(bytes, pollOptionData.getOptionName());

			bytes.write(Longs.toByteArray(createPollTransactionData.getFee()));

			if (createPollTransactionData.getSignature() != null)
				bytes.write(createPollTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
