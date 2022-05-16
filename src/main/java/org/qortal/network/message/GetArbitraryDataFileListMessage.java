package org.qortal.network.message;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.transform.Transformer;
import org.qortal.transform.transaction.TransactionTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.qortal.transform.Transformer.INT_LENGTH;
import static org.qortal.transform.Transformer.LONG_LENGTH;

public class GetArbitraryDataFileListMessage extends Message {

	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;
	private static final int HASH_LENGTH = TransactionTransformer.SHA256_LENGTH;

	private final byte[] signature;
	private List<byte[]> hashes;
	private final long requestTime;
	private int requestHops;

	public GetArbitraryDataFileListMessage(byte[] signature, List<byte[]> hashes, long requestTime, int requestHops) {
		this(-1, signature, hashes, requestTime, requestHops);
	}

	private GetArbitraryDataFileListMessage(int id, byte[] signature, List<byte[]> hashes, long requestTime, int requestHops) {
		super(id, MessageType.GET_ARBITRARY_DATA_FILE_LIST);

		this.signature = signature;
		this.hashes = hashes;
		this.requestTime = requestTime;
		this.requestHops = requestHops;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public List<byte[]> getHashes() {
		return this.hashes;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException {
		byte[] signature = new byte[SIGNATURE_LENGTH];

		bytes.get(signature);

		long requestTime = bytes.getLong();

		int requestHops = bytes.getInt();

		List<byte[]> hashes = null;
		if (bytes.hasRemaining()) {
			int hashCount = bytes.getInt();

			if (bytes.remaining() != hashCount * HASH_LENGTH) {
				return null;
			}

			hashes = new ArrayList<>();
			for (int i = 0; i < hashCount; ++i) {
				byte[] hash = new byte[HASH_LENGTH];
				bytes.get(hash);
				hashes.add(hash);
			}
		}

		return new GetArbitraryDataFileListMessage(id, signature, hashes, requestTime, requestHops);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(this.signature);

			bytes.write(Longs.toByteArray(this.requestTime));

			bytes.write(Ints.toByteArray(this.requestHops));

			if (this.hashes != null) {
				bytes.write(Ints.toByteArray(this.hashes.size()));

				for (byte[] hash : this.hashes) {
					bytes.write(hash);
				}
			}

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

	public long getRequestTime() {
		return this.requestTime;
	}

	public int getRequestHops() {
		return this.requestHops;
	}
	public void setRequestHops(int requestHops) {
		this.requestHops = requestHops;
	}

}
