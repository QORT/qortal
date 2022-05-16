package org.qortal.test.crosschain.apps;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.script.Script.ScriptType;
import org.qortal.crosschain.Litecoin;
import org.qortal.crosschain.Bitcoin;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.crosschain.BitcoinyHTLC;

import com.google.common.hash.HashCode;

public class BuildHTLC {

	private static void usage(String error) {
		if (error != null)
			System.err.println(error);

		System.err.println(String.format("usage: BuildHTLC (-b | -l) <refund-P2PKH> <amount> <redeem-P2PKH> <HASH160-of-secret> <locktime>"));
		System.err.println("where: -b means use Bitcoin, -l means use Litecoin");
		System.err.println(String.format("example: BuildHTLC -l "
				+ "msAfaDaJ8JiprxxFaAXEEPxKK3JaZCYpLv \\\n"
				+ "\t0.00008642 \\\n"
				+ "\tmrBpZYYGYMwUa8tRjTiXfP1ySqNXszWN5h \\\n"
				+ "\tdaf59884b4d1aec8c1b17102530909ee43c0151a \\\n"
				+ "\t1600000000"));
		System.exit(1);
	}

	public static void main(String[] args) {
		if (args.length < 6 || args.length > 6)
			usage(null);

		Common.init();

		Bitcoiny bitcoiny = null;
		NetworkParameters params = null;

		Address refundAddress = null;
		Coin amount = null;
		Address redeemAddress = null;
		byte[] hashOfSecret = null;
		int lockTime = 0;

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
			params = bitcoiny.getNetworkParameters();

			refundAddress = Address.fromString(params, args[argIndex++]);
			if (refundAddress.getOutputScriptType() != ScriptType.P2PKH)
				usage("Refund address must be in P2PKH form");

			amount = Coin.parseCoin(args[argIndex++]);

			redeemAddress = Address.fromString(params, args[argIndex++]);
			if (redeemAddress.getOutputScriptType() != ScriptType.P2PKH)
				usage("Redeem address must be in P2PKH form");

			hashOfSecret = HashCode.fromString(args[argIndex++]).asBytes();
			if (hashOfSecret.length != 20)
				usage("Hash of secret must be 20 bytes");

			lockTime = Integer.parseInt(args[argIndex++]);
			int refundTimeoutDelay = lockTime - (int) (System.currentTimeMillis() / 1000L);
			if (refundTimeoutDelay < 600 || refundTimeoutDelay > 30 * 24 * 60 * 60)
				usage("Locktime (seconds) should be at between 10 minutes and 1 month from now");
		} catch (IllegalArgumentException e) {
			usage(String.format("Invalid argument %d: %s", argIndex, e.getMessage()));
		}

		System.out.println(String.format("Using %s", bitcoiny.getBlockchainProvider().getNetId()));

		Coin p2shFee = Coin.valueOf(Common.getP2shFee(bitcoiny));
		if (p2shFee.isZero())
			return;

		System.out.println(String.format("Refund address: %s", refundAddress));
		System.out.println(String.format("Amount: %s", amount.toPlainString()));
		System.out.println(String.format("Redeem address: %s", redeemAddress));
		System.out.println(String.format("Refund/redeem miner's fee: %s", bitcoiny.format(p2shFee)));
		System.out.println(String.format("Script lockTime: %s (%d)", LocalDateTime.ofInstant(Instant.ofEpochSecond(lockTime), ZoneOffset.UTC), lockTime));
		System.out.println(String.format("Hash of secret: %s", HashCode.fromBytes(hashOfSecret)));

		byte[] redeemScriptBytes = BitcoinyHTLC.buildScript(refundAddress.getHash(), lockTime, redeemAddress.getHash(), hashOfSecret);
		System.out.println(String.format("Raw script bytes: %s", HashCode.fromBytes(redeemScriptBytes)));

		String p2shAddress = bitcoiny.deriveP2shAddress(redeemScriptBytes);
		System.out.println(String.format("P2SH address: %s", p2shAddress));

		amount = amount.add(p2shFee);

		// Fund P2SH
		System.out.println(String.format("\nYou need to fund %s with %s (includes redeem/refund fee of %s)",
				p2shAddress, bitcoiny.format(amount), bitcoiny.format(p2shFee)));
	}

}
