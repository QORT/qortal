package org.qortal.network.message;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.transform.Transformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GetArbitraryMetadataMessage extends Message {

	private byte[] signature;
	private long requestTime;
	private int requestHops;

	public GetArbitraryMetadataMessage(byte[] signature, long requestTime, int requestHops) {
		super(MessageType.GET_ARBITRARY_METADATA);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(signature);

			bytes.write(Longs.toByteArray(requestTime));

			bytes.write(Ints.toByteArray(requestHops));
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetArbitraryMetadataMessage(int id, byte[] signature, long requestTime, int requestHops) {
		super(id, MessageType.GET_ARBITRARY_METADATA);

		this.signature = signature;
		this.requestTime = requestTime;
		this.requestHops = requestHops;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public long getRequestTime() {
		return this.requestTime;
	}

	public int getRequestHops() {
		return this.requestHops;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		byte[] signature = new byte[Transformer.SIGNATURE_LENGTH];
		bytes.get(signature);

		long requestTime = bytes.getLong();

		int requestHops = bytes.getInt();

		return new GetArbitraryMetadataMessage(id, signature, requestTime, requestHops);
	}

}
