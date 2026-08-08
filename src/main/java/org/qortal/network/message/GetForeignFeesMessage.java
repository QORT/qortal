package org.qortal.network.message;

import org.qortal.data.crosschain.ForeignFeeDecodedData;
import org.qortal.utils.ForeignFeesMessageUtils;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GetForeignFeesMessage extends Message {

	private static final Map<Long, Map<Byte, byte[]>> EMPTY_ONLINE_ACCOUNTS = Collections.emptyMap();
	private final List<ForeignFeeDecodedData> foreignFeeDecodedData;

	public GetForeignFeesMessage(List<ForeignFeeDecodedData> foreignFeeDecodedData) {
		super(MessageType.GET_FOREIGN_FEES);

		this.foreignFeeDecodedData = foreignFeeDecodedData;

		// If we don't have ANY online accounts then it's an easier construction...
		if (foreignFeeDecodedData.isEmpty()) {
			this.dataBytes = EMPTY_DATA_BYTES;
			return;
		}

		this.dataBytes = ForeignFeesMessageUtils.fromDataToGetBytes(foreignFeeDecodedData);
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetForeignFeesMessage(int id, List<ForeignFeeDecodedData> foreignFeeDecodedData) {
		super(id, MessageType.GET_FOREIGN_FEES);

		this.foreignFeeDecodedData = foreignFeeDecodedData;
	}

	public List<ForeignFeeDecodedData> getForeignFeeData() {
		return foreignFeeDecodedData;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {

		return new GetForeignFeesMessage(id, ForeignFeesMessageUtils.fromGetBytesToData(bytes));
	}

}
