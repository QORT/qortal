package org.qortal.network.message;

import java.nio.ByteBuffer;

public class PingMessage extends Message {

	public PingMessage() {
		super(MessageType.PING);

		this.dataBytes = EMPTY_DATA_BYTES;
	}

	private PingMessage(int id) {
		super(id, MessageType.PING);
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		return new PingMessage(id);
	}

}
