package org.qortal.test.crosschain.apps;

import java.security.Security;

import org.bitcoinj.core.AddressFormatException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qortal.crosschain.Bitcoin;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.crosschain.Litecoin;
import org.qortal.settings.Settings;

public class GetNextReceiveAddress {

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	private static void usage(String error) {
		if (error != null)
			System.err.println(error);

		System.err.println(String.format("usage: GetNextReceiveAddress (-b | -l) <xprv/xpub>"));
		System.err.println(String.format("example (testnet): GetNextReceiveAddress -l tpubD6NzVbkrYhZ4X3jV96Wo3Kr8Au2v9cUUEmPRk1smwduFrRVfBjkkw49rRYjgff1fGSktFMfabbvv8b1dmfyLjjbDax6QGyxpsNsx5PXukCB"));
		System.exit(1);
	}

	public static void main(String[] args) {
		if (args.length != 2)
			usage(null);

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		Settings.fileInstance("settings-test.json");

		Bitcoiny bitcoiny = null;
		String key58 = null;

		int argIndex = 0;
		try {
			switch (args[argIndex++]) {
				case "-b":
					bitcoiny = Bitcoin.getInstance();
					break;

				case "-l":
					bitcoiny = Litecoin.getInstance();
					break;

				default:
					usage("Only Bitcoin (-b) or Litecoin (-l) supported");
			}

			key58 = args[argIndex++];

			if (!bitcoiny.isValidDeterministicKey(key58))
				usage("Not valid xprv/xpub/tprv/tpub");
		} catch (NumberFormatException | AddressFormatException e) {
			usage(String.format("Argument format exception: %s", e.getMessage()));
		}

		System.out.println(String.format("Using %s", bitcoiny.getBlockchainProvider().getNetId()));

		String receiveAddress = null;
		try {
			receiveAddress = bitcoiny.getUnusedReceiveAddress(key58);
		} catch (ForeignBlockchainException e) {
			System.err.println(String.format("Failed to determine next receive address: %s", e.getMessage()));
			System.exit(1);
		}

		System.out.println(String.format("Next receive address: %s", receiveAddress));
	}

}
