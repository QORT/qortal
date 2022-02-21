package org.qortal.test.crosschain.apps;

import java.util.Arrays;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script.ScriptType;
import org.qortal.crosschain.Bitcoin;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.crosschain.Litecoin;
import org.qortal.crosschain.BitcoinyHTLC;
import org.qortal.crypto.Crypto;

import com.google.common.hash.HashCode;

public class RedeemHTLC {

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	private static void usage(String error) {
		if (error != null)
			System.err.println(error);

		System.err.println(String.format("usage: Redeem (-b | -l) <P2SH-address> <refund-P2PKH> <redeem-PRIVATE-key> <secret> <locktime> <output-address>"));
		System.err.println("where: -b means use Bitcoin, -l means use Litecoin");
		System.err.println(String.format("example: Redeem -l "
				+ "2N4378NbEVGjmiUmoUD9g1vCY6kyx9tDUJ6 \\\n"
				+ "\tmsAfaDaJ8JiprxxFaAXEEPxKK3JaZCYpLv \\\n"
				+ "\tefdaed23c4bc85c8ccae40d774af3c2a10391c648b6420cdd83cd44c27fcb5955201c64e372d \\\n"
				+ "\t5468697320737472696e672069732065786163746c7920333220627974657321 \\\n"
				+ "\t1600184800 \\\n"
				+ "\tmrBpZYYGYMwUa8tRjTiXfP1ySqNXszWN5h"));
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
		byte[] redeemPrivateKey = null;
		byte[] secret = null;
		int lockTime = 0;
		Address outputAddress = null;

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

			redeemPrivateKey = HashCode.fromString(args[argIndex++]).asBytes();
			// Auto-trim
			if (redeemPrivateKey.length >= 37 && redeemPrivateKey.length <= 38)
				redeemPrivateKey = Arrays.copyOfRange(redeemPrivateKey, 1, 33);
			if (redeemPrivateKey.length != 32)
				usage("Redeem private key must be 32 bytes");

			secret = HashCode.fromString(args[argIndex++]).asBytes();
			if (secret.length == 0)
				usage("Invalid secret bytes");

			lockTime = Integer.parseInt(args[argIndex++]);

			outputAddress = Address.fromString(params, args[argIndex++]);
			if (outputAddress.getOutputScriptType() != ScriptType.P2PKH)
				usage("Output address invalid");
		} catch (IllegalArgumentException e) {
			usage(String.format("Invalid argument %d: %s", argIndex, e.getMessage()));
		}

		System.out.println(String.format("Using %s", bitcoiny.getBlockchainProvider().getNetId()));

		Coin p2shFee = Coin.valueOf(Common.getP2shFee(bitcoiny));
		if (p2shFee.isZero())
			return;

		System.out.println(String.format("Attempting to redeem HTLC %s to %s", p2shAddress, outputAddress));

		byte[] hashOfSecret = Crypto.hash160(secret);

		ECKey redeemKey = ECKey.fromPrivate(redeemPrivateKey);
		Address redeemAddress = Address.fromKey(params, redeemKey, ScriptType.P2PKH);

		byte[] redeemScriptBytes = BitcoinyHTLC.buildScript(refundAddress.getHash(), lockTime, redeemAddress.getHash(), hashOfSecret);

		byte[] redeemScriptHash = Crypto.hash160(redeemScriptBytes);
		Address derivedP2shAddress = LegacyAddress.fromScriptHash(params, redeemScriptHash);

		if (!derivedP2shAddress.equals(p2shAddress)) {
			System.err.println(String.format("Raw script bytes: %s", HashCode.fromBytes(redeemScriptBytes)));
			System.err.println(String.format("Derived P2SH address %s does not match given address %s", derivedP2shAddress, p2shAddress));
			System.exit(2);
			return;
		}

		// Actual live processing...

		int medianBlockTime = Common.checkMedianBlockTime(bitcoiny, null);
		if (medianBlockTime == 0)
			return;

		// Check P2SH is funded
		long p2shBalance = Common.getBalance(bitcoiny, p2shAddress.toString());
		if (p2shBalance == 0)
			return;

		// Grab all unspent outputs
		List<TransactionOutput> unspentOutputs = Common.getUnspentOutputs(bitcoiny, p2shAddress.toString());
		if (unspentOutputs.isEmpty())
			return;

		Coin redeemAmount = Coin.valueOf(p2shBalance).subtract(p2shFee);

		BitcoinyHTLC.Status htlcStatus = Common.determineHtlcStatus(bitcoiny, p2shAddress.toString(), redeemAmount.value);
		if (htlcStatus == null)
			return;

		if (htlcStatus != BitcoinyHTLC.Status.FUNDED) {
			System.err.println(String.format("Expecting %s HTLC status, but actual status is %s", "FUNDED", htlcStatus.name()));
			System.exit(2);
			return;
		}

		System.out.println(String.format("Spending %s of outputs, with %s as mining fee", bitcoiny.format(redeemAmount), bitcoiny.format(p2shFee)));

		Transaction redeemTransaction = BitcoinyHTLC.buildRedeemTransaction(bitcoiny.getNetworkParameters(), redeemAmount, redeemKey,
				unspentOutputs, redeemScriptBytes, secret, outputAddress.getHash());

		Common.broadcastTransaction(bitcoiny, redeemTransaction);
	}

}
