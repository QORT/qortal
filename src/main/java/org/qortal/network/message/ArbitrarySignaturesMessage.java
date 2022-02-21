package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.data.network.PeerData;
import org.qortal.transform.TransformationException;
import org.qortal.transform.Transformer;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ArbitrarySignaturesMessage extends Message {

	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;

	private String peerAddress;
	private int requestHops;
	private List<byte[]> signatures;

	public ArbitrarySignaturesMessage(String peerAddress, int requestHops, List<byte[]> signatures) {
		this(-1, peerAddress, requestHops, signatures);
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

	public int getRequestHops() {
		return this.requestHops;
	}

	public void setRequestHops(int requestHops) {
		this.requestHops = requestHops;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws UnsupportedEncodingException, TransformationException {
		String peerAddress = Serialization.deserializeSizedStringV2(bytes, PeerData.MAX_PEER_ADDRESS_SIZE);

		int requestHops = bytes.getInt();

		int signatureCount = bytes.getInt();

		if (bytes.remaining() != signatureCount * SIGNATURE_LENGTH)
			return null;

		List<byte[]> signatures = new ArrayList<>();
		for (int i = 0; i < signatureCount; ++i) {
			byte[] signature = new byte[SIGNATURE_LENGTH];
			bytes.get(signature);
			signatures.add(signature);
		}

		return new ArbitrarySignaturesMessage(id, peerAddress, requestHops, signatures);
	}

	@Override
	protected byte[] toData() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			Serialization.serializeSizedStringV2(bytes, this.peerAddress);

			bytes.write(Ints.toByteArray(this.requestHops));

			bytes.write(Ints.toByteArray(this.signatures.size()));

			for (byte[] signature : this.signatures)
				bytes.write(signature);

			return bytes.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

}
