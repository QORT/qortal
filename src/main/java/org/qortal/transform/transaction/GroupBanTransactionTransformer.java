package org.qortal.transform.transaction;

import com.google.common.base.Utf8;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.GroupBanTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GroupBanTransactionTransformer extends TransactionTransformer {

	// Property lengths
	private static final int GROUPID_LENGTH = INT_LENGTH;
	private static final int MEMBER_LENGTH = ADDRESS_LENGTH;
	private static final int REASON_SIZE_LENGTH = INT_LENGTH;
	private static final int TTL_LENGTH = INT_LENGTH;

	private static final int EXTRAS_LENGTH = GROUPID_LENGTH + MEMBER_LENGTH + REASON_SIZE_LENGTH + TTL_LENGTH;

	protected static final TransactionLayout layout;

	static {
		layout = new TransactionLayout();
		layout.add("txType: " + TransactionType.GROUP_BAN.valueString, TransformationType.INT);
		layout.add("timestamp", TransformationType.TIMESTAMP);
		layout.add("transaction's groupID", TransformationType.INT);
		layout.add("reference", TransformationType.SIGNATURE);
		layout.add("group admin's public key", TransformationType.PUBLIC_KEY);
		layout.add("group ID", TransformationType.INT);
		layout.add("account to ban", TransformationType.ADDRESS);
		layout.add("ban reason length", TransformationType.INT);
		layout.add("ban reason", TransformationType.STRING);
		layout.add("ban period (seconds) or 0 forever", TransformationType.INT);
		layout.add("fee", TransformationType.AMOUNT);
		layout.add("signature", TransformationType.SIGNATURE);
	}

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		int txGroupId = byteBuffer.getInt();

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] adminPublicKey = Serialization.deserializePublicKey(byteBuffer);

		int groupId = byteBuffer.getInt();

		String offender = Serialization.deserializeAddress(byteBuffer);

		String reason = Serialization.deserializeSizedString(byteBuffer, Group.MAX_REASON_SIZE);

		int timeToLive = byteBuffer.getInt();

		long fee = byteBuffer.getLong();

		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, adminPublicKey, fee, signature);

		return new GroupBanTransactionData(baseTransactionData, groupId, offender, reason, timeToLive);
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		GroupBanTransactionData groupBanTransactionData = (GroupBanTransactionData) transactionData;

		return getBaseLength(transactionData) + EXTRAS_LENGTH + Utf8.encodedLength(groupBanTransactionData.getReason());
	}

	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			GroupBanTransactionData groupBanTransactionData = (GroupBanTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			transformCommonBytes(transactionData, bytes);

			bytes.write(Ints.toByteArray(groupBanTransactionData.getGroupId()));

			Serialization.serializeAddress(bytes, groupBanTransactionData.getOffender());

			Serialization.serializeSizedString(bytes, groupBanTransactionData.getReason());

			bytes.write(Ints.toByteArray(groupBanTransactionData.getTimeToLive()));

			bytes.write(Longs.toByteArray(groupBanTransactionData.getFee()));

			if (groupBanTransactionData.getSignature() != null)
				bytes.write(groupBanTransactionData.getSignature());

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
