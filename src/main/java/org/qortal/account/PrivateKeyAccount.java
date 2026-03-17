package org.qortal.account;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.qortal.crypto.Crypto;
import org.qortal.repository.Repository;

public class PrivateKeyAccount extends PublicKeyAccount {

	private final byte[] privateKey;
	private final Ed25519PrivateKeyParameters edPrivateKeyParams;

	/**
	 * Create PrivateKeyAccount using byte[32] private key.
	 * 
	 * @param privateKey
	 *            byte[32] used to create private/public key pair
	 * @throws IllegalArgumentException
	 *             if passed invalid privateKey
	 */
	public PrivateKeyAccount(Repository repository, byte[] privateKey) {
		this(repository, new Ed25519PrivateKeyParameters(privateKey, 0));
	}

	private PrivateKeyAccount(Repository repository, Ed25519PrivateKeyParameters edPrivateKeyParams) {
		this(repository, edPrivateKeyParams, edPrivateKeyParams.generatePublicKey());
	}

	private PrivateKeyAccount(Repository repository, Ed25519PrivateKeyParameters edPrivateKeyParams, Ed25519PublicKeyParameters edPublicKeyParams) {
		super(repository, edPublicKeyParams);

		this.privateKey = edPrivateKeyParams.getEncoded();
		this.edPrivateKeyParams = edPrivateKeyParams;
	}

	public byte[] getPrivateKey() {
		return this.privateKey;
	}

	public byte[] sign(byte[] message) {
		return Crypto.sign(this.edPrivateKeyParams, message);
	}

	public byte[] getSharedSecret(byte[] publicKey) {
		return Crypto.getSharedSecret(this.privateKey, publicKey);
	}

	public byte[] getRewardSharePrivateKey(byte[] publicKey) {
		byte[] sharedSecret = this.getSharedSecret(publicKey);

		return Crypto.digest(sharedSecret);
	}

}
