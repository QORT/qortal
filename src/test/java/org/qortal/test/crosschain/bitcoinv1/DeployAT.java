package org.qortal.test.crosschain.bitcoinv1;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.controller.Controller;
import org.qortal.crosschain.Bitcoin;
import org.qortal.crosschain.BitcoinACCTv1;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryFactory;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qortal.test.crosschain.apps.Common;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.Amounts;
import org.qortal.utils.Base58;

import com.google.common.hash.HashCode;

public class DeployAT {

	private static void usage(String error) {
		if (error != null)
			System.err.println(error);

		System.err.println(String.format("usage: DeployAT <your Qortal PRIVATE key> <QORT amount> <AT funding amount> <BTC amount> <your Bitcoin PKH/P2PKH> <HASH160-of-secret> <trade-timeout>"));
		System.err.println(String.format("example: DeployAT "
				+ "7Eztjz2TsxwbrWUYEaSdLbASKQGTfK2rR7ViFc5gaiZw \\\n"
				+ "\t10 \\\n"
				+ "\t10.1 \\\n"
				+ "\t0.00864200 \\\n"
				+ "\t750b06757a2448b8a4abebaa6e4662833fd5ddbb (or mrBpZYYGYMwUa8tRjTiXfP1ySqNXszWN5h) \\\n"
				+ "\tdaf59884b4d1aec8c1b17102530909ee43c0151a \\\n"
				+ "\t10080"));
		System.exit(1);
	}

	public static void main(String[] args) {
		if (args.length != 7)
			usage(null);

		Common.init();

		Bitcoiny bitcoiny = Bitcoin.getInstance();
		NetworkParameters params = bitcoiny.getNetworkParameters();

		byte[] refundPrivateKey = null;
		long redeemAmount = 0;
		long fundingAmount = 0;
		long expectedBitcoin = 0;
		byte[] bitcoinPublicKeyHash = null;
		byte[] hashOfSecret = null;
		int tradeTimeout = 0;

		int argIndex = 0;
		try {
			refundPrivateKey = Base58.decode(args[argIndex++]);
			if (refundPrivateKey.length != 32)
				usage("Refund private key must be 32 bytes");

			redeemAmount = Long.parseLong(args[argIndex++]);
			if (redeemAmount <= 0)
				usage("QORT amount must be positive");

			fundingAmount = Long.parseLong(args[argIndex++]);
			if (fundingAmount <= redeemAmount)
				usage("AT funding amount must be greater than QORT redeem amount");

			expectedBitcoin = Long.parseLong(args[argIndex++]);
			if (expectedBitcoin <= 0)
				usage("Expected BTC amount must be positive");

			String bitcoinPKHish = args[argIndex++];
			// Try P2PKH first
			try {
				Address bitcoinAddress = LegacyAddress.fromBase58(params, bitcoinPKHish);
				bitcoinPublicKeyHash = bitcoinAddress.getHash();
			} catch (AddressFormatException e) {
				// Try parsing as PKH hex string instead
				bitcoinPublicKeyHash = HashCode.fromString(bitcoinPKHish).asBytes();
			}
			if (bitcoinPublicKeyHash.length != 20)
				usage("Bitcoin PKH must be 20 bytes");

			hashOfSecret = HashCode.fromString(args[argIndex++]).asBytes();
			if (hashOfSecret.length != 20)
				usage("Hash of secret must be 20 bytes");

			tradeTimeout = Integer.parseInt(args[argIndex++]);
			if (tradeTimeout < 60 || tradeTimeout > 50000)
				usage("Trade timeout (minutes) must be between 60 and 50000");
		} catch (IllegalArgumentException e) {
			usage(String.format("Invalid argument %d: %s", argIndex, e.getMessage()));
		}

		try {
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(Controller.getRepositoryUrl());
			RepositoryManager.setRepositoryFactory(repositoryFactory);
		} catch (DataException e) {
			System.err.println(String.format("Repository start-up issue: %s", e.getMessage()));
			System.exit(2);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount refundAccount = new PrivateKeyAccount(repository, refundPrivateKey);
			System.out.println(String.format("Refund Qortal address: %s", refundAccount.getAddress()));

			System.out.println(String.format("QORT redeem amount: %s", Amounts.prettyAmount(redeemAmount)));

			System.out.println(String.format("AT funding amount: %s", Amounts.prettyAmount(fundingAmount)));

			System.out.println(String.format("HASH160 of secret: %s", HashCode.fromBytes(hashOfSecret)));

			// Deploy AT
			byte[] creationBytes = BitcoinACCTv1.buildQortalAT(refundAccount.getAddress(), bitcoinPublicKeyHash, hashOfSecret, redeemAmount, expectedBitcoin, tradeTimeout);
			System.out.println("AT creation bytes: " + HashCode.fromBytes(creationBytes).toString());

			long txTimestamp = System.currentTimeMillis();
			byte[] lastReference = refundAccount.getLastReference();

			if (lastReference == null) {
				System.err.println(String.format("Qortal account %s has no last reference", refundAccount.getAddress()));
				System.exit(2);
			}

			Long fee = null;
			String name = "QORT-BTC cross-chain trade";
			String description = String.format("Qortal-Bitcoin cross-chain trade");
			String atType = "ACCT";
			String tags = "QORT-BTC ACCT";

			BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, refundAccount.getPublicKey(), fee, null);
			TransactionData deployAtTransactionData = new DeployAtTransactionData(baseTransactionData, name, description, atType, tags, creationBytes, fundingAmount, Asset.QORT);

			Transaction deployAtTransaction = new DeployAtTransaction(repository, deployAtTransactionData);

			fee = deployAtTransaction.calcRecommendedFee();
			deployAtTransactionData.setFee(fee);

			deployAtTransaction.sign(refundAccount);

			byte[] signedBytes = null;
			try {
				signedBytes = TransactionTransformer.toBytes(deployAtTransactionData);
			} catch (TransformationException e) {
				System.err.println(String.format("Unable to convert transaction to base58: %s", e.getMessage()));
				System.exit(2);
			}

			System.out.println(String.format("%nSigned transaction in base58, ready for POST /transactions/process:%n%s", Base58.encode(signedBytes)));
		} catch (DataException e) {
			System.err.println(String.format("Repository issue: %s", e.getMessage()));
			System.exit(2);
		}
	}

}
