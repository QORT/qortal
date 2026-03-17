package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.transform.block.BlockTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GetBlockSummariesMessage extends Message {

	private byte[] parentSignature;
	private int numberRequested;

	public GetBlockSummariesMessage(byte[] parentSignature, int numberRequested) {
		super(MessageType.GET_BLOCK_SUMMARIES);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(parentSignature);

			bytes.write(Ints.toByteArray(numberRequested));
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetBlockSummariesMessage(int id, byte[] parentSignature, int numberRequested) {
		super(id, MessageType.GET_BLOCK_SUMMARIES);

		this.parentSignature = parentSignature;
		this.numberRequested = numberRequested;
	}

	public byte[] getParentSignature() {
		return this.parentSignature;
	}

	public int getNumberRequested() {
		return this.numberRequested;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		byte[] parentSignature = new byte[BlockTransformer.BLOCK_SIGNATURE_LENGTH];
		bytes.get(parentSignature);

		int numberRequested = bytes.getInt();

		return new GetBlockSummariesMessage(id, parentSignature, numberRequested);
	}

}
