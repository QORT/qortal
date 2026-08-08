package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.data.account.AccountData;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AccountMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;
	private static final int REFERENCE_LENGTH = Transformer.SIGNATURE_LENGTH;
	private static final int PUBLIC_KEY_LENGTH = Transformer.PUBLIC_KEY_LENGTH;

	private AccountData accountData;

	public AccountMessage(AccountData accountData) {
		super(MessageType.ACCOUNT);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// Send raw address instead of base58 encoded
			byte[] address = Base58.decode(accountData.getAddress());
			bytes.write(address);

			bytes.write(accountData.getReference());

			bytes.write(accountData.getPublicKey());

			bytes.write(Ints.toByteArray(accountData.getDefaultGroupId()));

			bytes.write(Ints.toByteArray(accountData.getFlags()));

			bytes.write(Ints.toByteArray(accountData.getLevel()));

			bytes.write(Ints.toByteArray(accountData.getBlocksMinted()));

			bytes.write(Ints.toByteArray(accountData.getBlocksMintedAdjustment()));

			bytes.write(Ints.toByteArray(accountData.getBlocksMintedPenalty()));

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public AccountMessage(int id, AccountData accountData) {
		super(id, MessageType.ACCOUNT);

		this.accountData = accountData;
	}

	public AccountData getAccountData() {
		return this.accountData;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		byte[] addressBytes = new byte[ADDRESS_LENGTH];
		byteBuffer.get(addressBytes);
		String address = Base58.encode(addressBytes);

		byte[] reference = new byte[REFERENCE_LENGTH];
		byteBuffer.get(reference);

		byte[] publicKey = new byte[PUBLIC_KEY_LENGTH];
		byteBuffer.get(publicKey);

		int defaultGroupId = byteBuffer.getInt();

		int flags = byteBuffer.getInt();

		int level = byteBuffer.getInt();

		int blocksMinted = byteBuffer.getInt();

		int blocksMintedAdjustment = byteBuffer.getInt();

		int blocksMintedPenalty = byteBuffer.getInt();

		AccountData accountData = new AccountData(address, reference, publicKey, defaultGroupId, flags, level, blocksMinted, blocksMintedAdjustment, blocksMintedPenalty);
		return new AccountMessage(id, accountData);
	}

	public AccountMessage cloneWithNewId(int newId) {
		AccountMessage clone = new AccountMessage(this.accountData);
		clone.setId(newId);
		return clone;
	}

}
