package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.repository.DataException;
import org.qortal.transform.Transformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class ArbitraryDataFileMessage extends Message {

	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;

	private final byte[] signature;
	private final ArbitraryDataFile arbitraryDataFile;

	public ArbitraryDataFileMessage(byte[] signature, ArbitraryDataFile arbitraryDataFile) {
		super(MessageType.ARBITRARY_DATA_FILE);

		this.signature = signature;
		this.arbitraryDataFile = arbitraryDataFile;
	}

	public ArbitraryDataFileMessage(int id, byte[] signature, ArbitraryDataFile arbitraryDataFile) {
		super(id, MessageType.ARBITRARY_DATA_FILE);

		this.signature = signature;
		this.arbitraryDataFile = arbitraryDataFile;
	}

	public ArbitraryDataFile getArbitraryDataFile() {
		return this.arbitraryDataFile;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws UnsupportedEncodingException {
		byte[] signature = new byte[SIGNATURE_LENGTH];
		byteBuffer.get(signature);

		int dataLength = byteBuffer.getInt();

		if (byteBuffer.remaining() != dataLength)
			return null;

		byte[] data = new byte[dataLength];
		byteBuffer.get(data);

		try {
			ArbitraryDataFile arbitraryDataFile = new ArbitraryDataFile(data, signature);
			return new ArbitraryDataFileMessage(id, signature, arbitraryDataFile);
		}
		catch (DataException e) {
			return null;
		}
	}

	@Override
	protected byte[] toData() {
		if (this.arbitraryDataFile == null) {
			return null;
		}

		byte[] data = this.arbitraryDataFile.getBytes();
		if (data == null) {
			return null;
		}

		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(signature);

			bytes.write(Ints.toByteArray(data.length));

			bytes.write(data);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

	public ArbitraryDataFileMessage cloneWithNewId(int newId) {
		ArbitraryDataFileMessage clone = new ArbitraryDataFileMessage(this.signature, this.arbitraryDataFile);
		clone.setId(newId);
		return clone;
	}

}
