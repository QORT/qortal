package org.qortal.network.message;

import com.google.common.primitives.Ints;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ResponseMessage extends Message {

	public static final int DATA_LENGTH = 32;

	private int nonce;
	private byte[] data;

	public ResponseMessage(int nonce, byte[] data) {
		super(MessageType.RESPONSE);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream(4 + DATA_LENGTH);

		try {
			bytes.write(Ints.toByteArray(nonce));

			bytes.write(data);
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private ResponseMessage(int id, int nonce, byte[] data) {
		super(id, MessageType.RESPONSE);

		this.nonce = nonce;
		this.data = data;
	}

	public int getNonce() {
		return this.nonce;
	}

	public byte[] getData() {
		return this.data;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		int nonce = byteBuffer.getInt();

		byte[] data = new byte[DATA_LENGTH];
		byteBuffer.get(data);

		return new ResponseMessage(id, nonce, data);
	}

}
