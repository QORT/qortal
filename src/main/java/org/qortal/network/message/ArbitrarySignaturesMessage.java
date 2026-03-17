package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.data.network.PeerData;
import org.qortal.transform.TransformationException;
import org.qortal.transform.Transformer;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ArbitrarySignaturesMessage extends Message {

	private String peerAddress;
	private int requestHops;
	private List<byte[]> signatures;

	public ArbitrarySignaturesMessage(String peerAddress, int requestHops, List<byte[]> signatures) {
		super(MessageType.ARBITRARY_SIGNATURES);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			Serialization.serializeSizedStringV2(bytes, peerAddress);

			bytes.write(Ints.toByteArray(requestHops));

			bytes.write(Ints.toByteArray(signatures.size()));

			for (byte[] signature : signatures)
				bytes.write(signature);
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private ArbitrarySignaturesMessage(int id, String peerAddress, int requestHops, List<byte[]> signatures) {
		super(id, MessageType.ARBITRARY_SIGNATURES);

		this.peerAddress = peerAddress;
		this.requestHops = requestHops;
		this.signatures = signatures;
	}

	public String getPeerAddress() {
		return this.peerAddress;
	}

	public List<byte[]> getSignatures() {
		return this.signatures;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws MessageException {
		String peerAddress;
		try {
			peerAddress = Serialization.deserializeSizedStringV2(bytes, PeerData.MAX_PEER_ADDRESS_SIZE);
		} catch (TransformationException e) {
			throw new MessageException(e.getMessage(), e);
		}

		int requestHops = bytes.getInt();

		int signatureCount = bytes.getInt();

		if (bytes.remaining() < signatureCount * Transformer.SIGNATURE_LENGTH)
			throw new BufferUnderflowException();

		List<byte[]> signatures = new ArrayList<>();
		for (int i = 0; i < signatureCount; ++i) {
			byte[] signature = new byte[Transformer.SIGNATURE_LENGTH];
			bytes.get(signature);
			signatures.add(signature);
		}

		return new ArbitrarySignaturesMessage(id, peerAddress, requestHops, signatures);
	}

}
