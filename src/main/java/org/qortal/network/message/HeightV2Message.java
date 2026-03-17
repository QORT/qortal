package org.qortal.network.message;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.transform.Transformer;
import org.qortal.transform.block.BlockTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class HeightV2Message extends Message {

	private int height;
	private byte[] signature;
	private long timestamp;
	private byte[] minterPublicKey;

	public HeightV2Message(int height, byte[] signature, long timestamp, byte[] minterPublicKey) {
		super(MessageType.HEIGHT_V2);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(Ints.toByteArray(height));

			bytes.write(signature);

			bytes.write(Longs.toByteArray(timestamp));

			bytes.write(minterPublicKey);
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private HeightV2Message(int id, int height, byte[] signature, long timestamp, byte[] minterPublicKey) {
		super(id, MessageType.HEIGHT_V2);

		this.height = height;
		this.signature = signature;
		this.timestamp = timestamp;
		this.minterPublicKey = minterPublicKey;
	}

	public int getHeight() {
		return this.height;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getMinterPublicKey() {
		return this.minterPublicKey;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		int height = bytes.getInt();

		byte[] signature = new byte[BlockTransformer.BLOCK_SIGNATURE_LENGTH];
		bytes.get(signature);

		long timestamp = bytes.getLong();

		byte[] minterPublicKey = new byte[Transformer.PUBLIC_KEY_LENGTH];
		bytes.get(minterPublicKey);

		return new HeightV2Message(id, height, signature, timestamp, minterPublicKey);
	}

}
