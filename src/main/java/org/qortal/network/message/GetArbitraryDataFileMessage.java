package org.qortal.network.message;

import org.qortal.transform.Transformer;
import org.qortal.transform.transaction.TransactionTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class GetArbitraryDataFileMessage extends Message {

	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;
	private static final int HASH_LENGTH = TransactionTransformer.SHA256_LENGTH;

	private final byte[] signature;
	private final byte[] hash;

	public GetArbitraryDataFileMessage(byte[] signature, byte[] hash) {
		this(-1, signature, hash);
	}

	private GetArbitraryDataFileMessage(int id, byte[] signature, byte[] hash) {
		super(id, MessageType.GET_ARBITRARY_DATA_FILE);

		this.signature = signature;
		this.hash = hash;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public byte[] getHash() {
		return this.hash;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		if (bytes.remaining() != HASH_LENGTH + SIGNATURE_LENGTH)
			return null;

		byte[] signature = new byte[SIGNATURE_LENGTH];
		bytes.get(signature);

		byte[] hash = new byte[HASH_LENGTH];
		bytes.get(hash);

		return new GetArbitraryDataFileMessage(id, signature, hash);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.signature);

			bytes.write(this.hash);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
