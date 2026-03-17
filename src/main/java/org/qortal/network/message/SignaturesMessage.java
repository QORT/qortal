package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.transform.block.BlockTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class SignaturesMessage extends Message {

	private List<byte[]> signatures;

	public SignaturesMessage(List<byte[]> signatures) {
		super(MessageType.SIGNATURES);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(Ints.toByteArray(signatures.size()));

			for (byte[] signature : signatures)
				bytes.write(signature);
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private SignaturesMessage(int id, List<byte[]> signatures) {
		super(id, MessageType.SIGNATURES);

		this.signatures = signatures;
	}

	public List<byte[]> getSignatures() {
		return this.signatures;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		int count = bytes.getInt();

		if (bytes.remaining() < count * BlockTransformer.BLOCK_SIGNATURE_LENGTH)
			throw new BufferUnderflowException();

		List<byte[]> signatures = new ArrayList<>();
		for (int i = 0; i < count; ++i) {
			byte[] signature = new byte[BlockTransformer.BLOCK_SIGNATURE_LENGTH];
			bytes.get(signature);
			signatures.add(signature);
		}

		return new SignaturesMessage(id, signatures);
	}

}
