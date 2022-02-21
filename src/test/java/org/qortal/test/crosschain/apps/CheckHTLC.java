package org.qortal.test.crosschain.apps;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.script.Script.ScriptType;
import org.qortal.crosschain.Bitcoin;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.crosschain.Litecoin;
import org.qortal.crosschain.BitcoinyHTLC;
import org.qortal.crypto.Crypto;

import com.google.common.hash.HashCode;

public class CheckHTLC {

	private static void usage(String error) {
		if (error != null)
			System.err.println(error);

		System.err.println(String.format("usage: CheckHTLC (-b | -l) <P2SH-address> <refund-P2PKH> <amount> <redeem-P2PKH> <HASH160-of-secret> <locktime>"));
		System.err.println("where: -b means use Bitcoin, -l means use Litecoin");
		System.err.println(String.format("example: CheckP2SH -l "
				+ "2N4378NbEVGjmiUmoUD9g1vCY6kyx9tDUJ6 \\\n"
				+ "msAfaDaJ8JiprxxFaAXEEPxKK3JaZCYpLv \\\n"
				+ "\t0.00008642 \\\n"
				+ "\tmrBpZYYGYMwUa8tRjTiXfP1ySqNXszWN5h \\\n"
				+ "\tdaf59884b4d1aec8c1b17102530909ee43c0151a \\\n"
				+ "\t1600184800"));
		System.exit(1);
	}

	public static void main(String[] args) {
		if (args.length < 7 || args.length > 7)
			usage(null);

		Common.init();

		Bitcoiny bitcoiny = null;
		NetworkParameters params = null;

		Address p2shAddress = null;
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

			p2shAddress = Address.fromString(params, args[argIndex++]);
			if (p2shAddress.getOutputScriptType() != ScriptType.P2SH)
				usage("P2SH address invalid");

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
		} catch (IllegalArgumentException e) {
			usage(String.format("Invalid argument %d: %s", argIndex, e.getMessage()));
		}

		System.out.println(String.format("Using %s", bitcoiny.getBlockchainProvider().getNetId()));

		Coin p2shFee = Coin.valueOf(Common.getP2shFee(bitcoiny));
		if (p2shFee.isZero())
			return;

		System.out.println(String.format("P2SH address: %s", p2shAddress));
		System.out.println(String.format("Refund PKH: %s", refundAddress));
		System.out.println(String.format("Redeem/refund amount: %s", amount.toPlainString()));
		System.out.println(String.format("Redeem PKH: %s", redeemAddress));
		System.out.println(String.format("Hash of secret: %s", HashCode.fromBytes(hashOfSecret)));
		System.out.println(String.format("Script lockTime: %s (%d)", LocalDateTime.ofInstant(Instant.ofEpochSecond(lockTime), ZoneOffset.UTC), lockTime));

		System.out.println(String.format("Redeem/refund miner's fee: %s", bitcoiny.format(p2shFee)));

		byte[] redeemScriptBytes = BitcoinyHTLC.buildScript(refundAddress.getHash(), lockTime, redeemAddress.getHash(), hashOfSecret);
		System.out.println(String.format("Raw script bytes: %s", HashCode.fromBytes(redeemScriptBytes)));

		byte[] redeemScriptHash = Crypto.hash160(redeemScriptBytes);
		Address derivedP2shAddress = LegacyAddress.fromScriptHash(params, redeemScriptHash);

		if (!derivedP2shAddress.equals(p2shAddress)) {
			System.err.println(String.format("Derived P2SH address %s does not match given address %s", derivedP2shAddress, p2shAddress));
			System.exit(2);
		}

		amount = amount.add(p2shFee);

		// Check network's median block time
		int medianBlockTime = Common.checkMedianBlockTime(bitcoiny, null);
		if (medianBlockTime == 0)
			return;

		// Check P2SH is funded
		Common.getBalance(bitcoiny, p2shAddress.toString());

		// Grab all unspent outputs
		Common.getUnspentOutputs(bitcoiny, p2shAddress.toString());

		Common.determineHtlcStatus(bitcoiny, p2shAddress.toString(), amount.value);
	}

}
