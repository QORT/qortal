package org.qortal.network.message;

import java.nio.ByteBuffer;

public class GetUnconfirmedTransactionsMessage extends Message {

	public GetUnconfirmedTransactionsMessage() {
		super(MessageType.GET_UNCONFIRMED_TRANSACTIONS);

		this.dataBytes = EMPTY_DATA_BYTES;
	}

	private GetUnconfirmedTransactionsMessage(int id) {
		super(id, MessageType.GET_UNCONFIRMED_TRANSACTIONS);
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		return new GetUnconfirmedTransactionsMessage(id);
	}

}
