package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import com.google.common.primitives.Longs;

public class HelloMessage extends Message {

	private final long timestamp;
	private final String versionString;
	private final String senderPeerAddress;

	private HelloMessage(int id, long timestamp, String versionString, String senderPeerAddress) {
		super(id, MessageType.HELLO);

		this.timestamp = timestamp;
		this.versionString = versionString;
		this.senderPeerAddress = senderPeerAddress;
	}

	public HelloMessage(long timestamp, String versionString, String senderPeerAddress) {
		this(-1, timestamp, versionString, senderPeerAddress);
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public String getVersionString() {
		return this.versionString;
	}

	public String getSenderPeerAddress() {
		return this.senderPeerAddress;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws TransformationException {
		long timestamp = byteBuffer.getLong();

		String versionString = Serialization.deserializeSizedString(byteBuffer, 255);

		// Sender peer address added in v3.0, so is an optional field. Older versions won't send it.
		String senderPeerAddress = null;
		if (byteBuffer.hasRemaining()) {
			senderPeerAddress = Serialization.deserializeSizedString(byteBuffer, 255);
		}

		return new HelloMessage(id, timestamp, versionString, senderPeerAddress);
	}

	@Override
	protected byte[] toData() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		bytes.write(Longs.toByteArray(this.timestamp));

		Serialization.serializeSizedString(bytes, this.versionString);

		Serialization.serializeSizedString(bytes, this.senderPeerAddress);

		return bytes.toByteArray();
	}

}
