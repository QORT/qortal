package org.qortal.test.crosschain.apps;

import java.security.Security;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.crosschain.BitcoinyHTLC;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.settings.Settings;
import org.qortal.utils.NTP;

import com.google.common.hash.HashCode;

public abstract class Common {

	public static void init() {
		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		Settings.fileInstance("settings-test.json");

		NTP.setFixedOffset(0L);
	}

	public static long getP2shFee(Bitcoiny bitcoiny) {
		long p2shFee;

		try {
			p2shFee = bitcoiny.getP2shFee(null);
		} catch (ForeignBlockchainException e) {
			System.err.println(String.format("Unable to determine P2SH fee: %s", e.getMessage()));
			return 0;
		}

		return p2shFee;
	}

	public static int checkMedianBlockTime(Bitcoiny bitcoiny, Integer lockTime) {
		int medianBlockTime;

		try {
			medianBlockTime = bitcoiny.getMedianBlockTime();
		} catch (ForeignBlockchainException e) {
			System.err.println(String.format("Unable to determine median block time: %s", e.getMessage()));
			return 0;
		}

		System.out.println(String.format("Median block time: %s", LocalDateTime.ofInstant(Instant.ofEpochSecond(medianBlockTime), ZoneOffset.UTC)));

		long now = System.currentTimeMillis();

		if (now < medianBlockTime * 1000L) {
			System.out.println(String.format("Too soon (%s) based on median block time %s",
					LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC),
					LocalDateTime.ofInstant(Instant.ofEpochSecond(medianBlockTime), ZoneOffset.UTC)));
			return 0;
		}

		if (lockTime != null && now < lockTime * 1000L) {
			System.err.println(String.format("Too soon (%s) based on lockTime %s",
					LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC),
					LocalDateTime.ofInstant(Instant.ofEpochSecond(lockTime), ZoneOffset.UTC)));
			return 0;
		}

		return medianBlockTime;
	}

	public static long getBalance(Bitcoiny bitcoiny, String address58) {
		long balance;

		try {
			balance = bitcoiny.getConfirmedBalance(address58);
		} catch (ForeignBlockchainException e) {
			System.err.println(String.format("Unable to check address %s balance: %s", address58, e.getMessage()));
			return 0;
		}

		System.out.println(String.format("Address %s balance: %s", address58, bitcoiny.format(balance)));

		return balance;
	}

	public static List<TransactionOutput> getUnspentOutputs(Bitcoiny bitcoiny, String address58) {
		List<TransactionOutput> unspentOutputs = Collections.emptyList();

		try {
			unspentOutputs = bitcoiny.getUnspentOutputs(address58);
		} catch (ForeignBlockchainException e) {
			System.err.println(String.format("Can't find unspent outputs for %s: %s", address58, e.getMessage()));
			return unspentOutputs;
		}

		System.out.println(String.format("Found %d output%s for %s",
				unspentOutputs.size(),
				(unspentOutputs.size() != 1 ? "s" : ""),
				address58));

		for (TransactionOutput fundingOutput : unspentOutputs)
			System.out.println(String.format("Output %s:%d amount %s",
					HashCode.fromBytes(fundingOutput.getParentTransactionHash().getBytes()), fundingOutput.getIndex(),
					bitcoiny.format(fundingOutput.getValue())));

		if (unspentOutputs.isEmpty())
			System.err.println(String.format("Can't use spent/unfunded %s", address58));

		if (unspentOutputs.size() != 1)
			System.err.println(String.format("Expecting only one unspent output?"));

		return unspentOutputs;
	}

	public static BitcoinyHTLC.Status determineHtlcStatus(Bitcoiny bitcoiny, String address58, long minimumAmount) {
		BitcoinyHTLC.Status htlcStatus = null;

		try {
			htlcStatus = BitcoinyHTLC.determineHtlcStatus(bitcoiny.getBlockchainProvider(), address58, minimumAmount);

			System.out.println(String.format("HTLC status: %s", htlcStatus.name()));
		} catch (ForeignBlockchainException e) {
			System.err.println(String.format("Unable to determine HTLC status: %s", e.getMessage()));
		}

		return htlcStatus;
	}

	public static void broadcastTransaction(Bitcoiny bitcoiny, Transaction transaction) {
		byte[] rawTransactionBytes = transaction.bitcoinSerialize();

		System.out.println(String.format("%nRaw transaction bytes:%n%s%n", HashCode.fromBytes(rawTransactionBytes).toString()));

		for (int countDown = 5; countDown >= 1; --countDown) {
			System.out.print(String.format("\rBroadcasting transaction in %d second%s... use CTRL-C to abort ", countDown, (countDown != 1 ? "s" : "")));
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException e) {
				System.exit(0);
			}
		}
		System.out.println("Broadcasting transaction...                                    ");

		try {
			bitcoiny.broadcastTransaction(transaction);
		} catch (ForeignBlockchainException e) {
			System.err.println(String.format("Failed to broadcast transaction: %s", e.getMessage()));
			System.exit(1);
		}
	}

}
