package org.qortal.test.apps;

import com.google.common.primitives.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.crypto.Crypto;
import org.qortal.utils.Base58;

import java.security.SecureRandom;
import java.security.Security;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VanityGen {

	// From utils.Base58:
	private static final String ALPHABET_STR = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

	private static String prefix = null;

	private static void usage() {
		System.err.println("Usage: Vanitygen [-t threads] <leading-chars>");
		System.err.println("Example: VanityGen Qcat");
		System.exit(1);
	}

	private static class Generator implements Runnable {
		public void run() {
			Random random = new SecureRandom();
			byte[] entropy = new byte[16];

			while (true) {
				// Generate entropy internally
				random.nextBytes(entropy);

				// Use SHA256 to generate more bits
				byte[] hash = Crypto.digest(entropy);

				// Append first 4 bits from hash to end. (Actually 8 bits but we only use 4).
				byte checksum = (byte) (hash[0] & 0xf0);
				byte[] entropy132 = Bytes.concat(entropy, new byte[] { checksum });

				PrivateKeyAccount account = new PrivateKeyAccount(null, hash);

				if (!account.getAddress().startsWith(prefix))
					continue;

				System.out.println(String.format("Address: %s, public key: %s, private key: %s",
						account.getAddress(), Base58.encode(account.getPublicKey()), Base58.encode(hash)));
				System.out.flush();
			}
		}
	}

	public static void main(String[] args) {
		if (args.length == 0)
			usage();

		int threadCount = 1;

		int argIndex = 0;
		while (argIndex < args.length) {
			String arg = args[argIndex++];

			if (arg.equals("-t")) {
				if (argIndex >= args.length)
					usage();

				try {
					threadCount = Integer.parseInt(args[argIndex++]);
				} catch (NumberFormatException e) {
					usage();
				}

				continue;
			}

			if (prefix != null)
				usage();

			prefix = arg;
			if (!prefix.matches("[" + ALPHABET_STR + "]+")) {
				System.err.println("Only the following characters are allowed:\n" + ALPHABET_STR);
				System.exit(1);
			}
		}

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);

		for (int ti = 0; ti < threadCount; ++ti)
			executor.execute(new Generator());
	}

}
