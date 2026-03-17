package org.qortal.network.message;

import java.nio.ByteBuffer;

public class PongMessage extends Message {

	public PongMessage() {
		super(MessageType.PONG);

		this.dataBytes = EMPTY_DATA_BYTES;
	}

	private PongMessage(int id) {
		super(id, MessageType.PONG);
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		return new PongMessage(id);
	}

}
