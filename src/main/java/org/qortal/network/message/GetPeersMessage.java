package org.qortal.network.message;

import java.nio.ByteBuffer;

public class GetPeersMessage extends Message {

	public GetPeersMessage() {
		super(MessageType.GET_PEERS);

		this.dataBytes = EMPTY_DATA_BYTES;
	}

	private GetPeersMessage(int id) {
		super(id, MessageType.GET_PEERS);
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		return new GetPeersMessage(id);
	}

}
