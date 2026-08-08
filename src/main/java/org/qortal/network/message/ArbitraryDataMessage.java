package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.transform.Transformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class ArbitraryDataMessage extends Message {

	private byte[] signature;
	private byte[] data;

	public ArbitraryDataMessage(byte[] signature, byte[] data) {
		super(MessageType.ARBITRARY_DATA);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(signature);

			bytes.write(Ints.toByteArray(data.length));

			bytes.write(data);
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private ArbitraryDataMessage(int id, byte[] signature, byte[] data) {
		super(id, MessageType.ARBITRARY_DATA);

		this.signature = signature;
		this.data = data;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public byte[] getData() {
		return this.data;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		byte[] signature = new byte[Transformer.SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		int dataLength = byteBuffer.getInt();

		if (byteBuffer.remaining() < dataLength)
			throw new BufferUnderflowException();

		byte[] data = new byte[dataLength];
		byteBuffer.get(data);

		return new ArbitraryDataMessage(id, signature, data);
	}

}
