package org.qortal.network.message;

import org.qortal.transform.block.BlockTransformer;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class GetBlockMessage extends Message {

	private byte[] signature;

	public GetBlockMessage(byte[] signature) {
		super(MessageType.GET_BLOCK);

		this.dataBytes = Arrays.copyOf(signature, signature.length);
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetBlockMessage(int id, byte[] signature) {
		super(id, MessageType.GET_BLOCK);

		this.signature = signature;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		byte[] signature = new byte[BlockTransformer.BLOCK_SIGNATURE_LENGTH];
		bytes.get(signature);

		return new GetBlockMessage(id, signature);
	}

}
