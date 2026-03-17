package org.qortal.network.message;

import java.nio.ByteBuffer;

public class UnsupportedMessage extends Message {

	public UnsupportedMessage() {
		super(MessageType.UNSUPPORTED);
		throw new UnsupportedOperationException("Unsupported message is unsupported!");
	}

	private UnsupportedMessage(int id) {
		super(id, MessageType.UNSUPPORTED);
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException {
		return new UnsupportedMessage(id);
	}

}
