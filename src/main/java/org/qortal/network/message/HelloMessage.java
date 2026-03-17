package org.qortal.network.message;

import com.google.common.primitives.Longs;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class HelloMessage extends Message {

	private long timestamp;
	private String versionString;
	private String senderPeerAddress;

	public HelloMessage(long timestamp, String versionString, String senderPeerAddress) {
		super(MessageType.HELLO);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(Longs.toByteArray(timestamp));

			Serialization.serializeSizedString(bytes, versionString);

			Serialization.serializeSizedString(bytes, senderPeerAddress);
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private HelloMessage(int id, long timestamp, String versionString, String senderPeerAddress) {
		super(id, MessageType.HELLO);

		this.timestamp = timestamp;
		this.versionString = versionString;
		this.senderPeerAddress = senderPeerAddress;
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

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException {
		long timestamp = byteBuffer.getLong();

		String versionString;
		String senderPeerAddress = null;
		try {
			versionString = Serialization.deserializeSizedString(byteBuffer, 255);

			// Sender peer address added in v3.0, so is an optional field. Older versions won't send it.
			if (byteBuffer.hasRemaining()) {
				senderPeerAddress = Serialization.deserializeSizedString(byteBuffer, 255);
			}
		} catch (TransformationException e) {
			throw new MessageException(e.getMessage(), e);
		}

		return new HelloMessage(id, timestamp, versionString, senderPeerAddress);
	}

}
