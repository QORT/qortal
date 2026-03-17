package org.qortal.test.apps;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.account.PublicKeyAccount;
import org.qortal.crypto.Crypto;
import org.qortal.utils.Base58;

import java.security.Security;

public class RewardShareKeys {

	private static void usage() {
		System.err.println("Usage: RewardShareKeys <minter-private-key> [<recipient-public-key>]");
		System.err.println("Example: RewardShareKeys pYQ6DpQBJ2n72TCLJLScEvwhf3boxWy2kQEPynakwpj 6rNn9b3pYRrG9UKqzMWYZ9qa8F3Zgv2mVWrULGHUusb");
		System.err.println("Example (self-share): RewardShareKeys pYQ6DpQBJ2n72TCLJLScEvwhf3boxWy2kQEPynakwpj");
		System.exit(1);
	}

	public static void main(String[] args) {
		if (args.length < 1 || args.length > 2)
			usage();

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		PrivateKeyAccount minterAccount = new PrivateKeyAccount(null, Base58.decode(args[0]));
		PublicKeyAccount recipientAccount = new PublicKeyAccount(null, args.length > 1 ? Base58.decode(args[1]) : minterAccount.getPublicKey());

		byte[] rewardSharePrivateKey = minterAccount.getRewardSharePrivateKey(recipientAccount.getPublicKey());
		byte[] rewardSharePublicKey = Crypto.toPublicKey(rewardSharePrivateKey);

		System.out.println(String.format("Minter account: %s", minterAccount.getAddress()));
		System.out.println(String.format("Minter's public key: %s", Base58.encode(minterAccount.getPublicKey())));

		System.out.println(String.format("Recipient account: %s", recipientAccount.getAddress()));

		System.out.println(String.format("Reward-share private key: %s", Base58.encode(rewardSharePrivateKey)));
		System.out.println(String.format("Reward-share public key: %s", Base58.encode(rewardSharePublicKey)));
	}

}
