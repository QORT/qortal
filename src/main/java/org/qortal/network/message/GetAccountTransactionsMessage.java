package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GetAccountTransactionsMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;

	private String address;
	private int limit;
	private int offset;

	public GetAccountTransactionsMessage(String address, int limit, int offset) {
		super(MessageType.GET_ACCOUNT_TRANSACTIONS);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// Send raw address instead of base58 encoded
			byte[] addressBytes = Base58.decode(address);
			bytes.write(addressBytes);

			bytes.write(Ints.toByteArray(limit));

			bytes.write(Ints.toByteArray(offset));

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetAccountTransactionsMessage(int id, String address, int limit, int offset) {
		super(id, MessageType.GET_ACCOUNT_TRANSACTIONS);

		this.address = address;
		this.limit = limit;
		this.offset = offset;
	}

	public String getAddress() {
		return this.address;
	}

	public int getLimit() { return this.limit; }

	public int getOffset() { return this.offset; }

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		byte[] addressBytes = new byte[ADDRESS_LENGTH];
		bytes.get(addressBytes);
		String address = Base58.encode(addressBytes);

		int limit = bytes.getInt();

		int offset = bytes.getInt();

		return new GetAccountTransactionsMessage(id, address, limit, offset);
	}

}
