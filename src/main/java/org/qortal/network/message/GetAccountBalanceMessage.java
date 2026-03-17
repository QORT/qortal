package org.qortal.network.message;

import com.google.common.primitives.Longs;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GetAccountBalanceMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;

	private String address;
	private long assetId;

	public GetAccountBalanceMessage(String address, long assetId) {
		super(MessageType.GET_ACCOUNT_BALANCE);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// Send raw address instead of base58 encoded
			byte[] addressBytes = Base58.decode(address);
			bytes.write(addressBytes);

			bytes.write(Longs.toByteArray(assetId));

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetAccountBalanceMessage(int id, String address, long assetId) {
		super(id, MessageType.GET_ACCOUNT_BALANCE);

		this.address = address;
		this.assetId = assetId;
	}

	public String getAddress() {
		return this.address;
	}

	public long getAssetId() {
		return this.assetId;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		byte[] addressBytes = new byte[ADDRESS_LENGTH];
		bytes.get(addressBytes);
		String address = Base58.encode(addressBytes);

		long assetId = bytes.getLong();

		return new GetAccountBalanceMessage(id, address, assetId);
	}

}
