package org.qortal.test.crosschain.litecoinv1;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.controller.Controller;
import org.qortal.crosschain.LitecoinACCTv1;
import org.qortal.crypto.Crypto;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryFactory;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qortal.test.crosschain.apps.Common;
import org.qortal.transaction.MessageTransaction;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.Base58;

import com.google.common.hash.HashCode;

public class SendRedeemMessage {

	private static void usage(String error) {
		if (error != null)
			System.err.println(error);

		System.err.println(String.format("usage: SendRedeemMessage <partner trade PRIVATE key> <AT address> <secret> <Qortal receive address>"));
		System.err.println(String.format("example: SendRedeemMessage "
				+ "dbfe739f5a3ecf7b0a22cea71f73d86ec71355b740e5972bcdf9e8bb4721ab9d \\\n"
				+ "\tAaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \\\n"
				+ "\t5468697320737472696e672069732065786163746c7920333220627974657321 \\\n"
				+ "\tQqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"));
		System.exit(1);
	}

	public static void main(String[] args) {
		if (args.length != 4)
			usage(null);

		Common.init();

		byte[] tradePrivateKey = null;
		String atAddress = null;
		byte[] secret = null;
		String receiveAddress = null;

		int argIndex = 0;
		try {
			tradePrivateKey = HashCode.fromString(args[argIndex++]).asBytes();
			if (tradePrivateKey.length != 32)
				usage("Refund private key must be 32 bytes");

			atAddress = args[argIndex++];
			if (!Crypto.isValidAtAddress(atAddress))
				usage("Invalid AT address");

			secret = HashCode.fromString(args[argIndex++]).asBytes();
			if (secret.length != 32)
				usage("Secret must be 32 bytes");

			receiveAddress = args[argIndex++];
			if (!Crypto.isValidAddress(receiveAddress))
				usage("Invalid Qortal receive address");
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
			PrivateKeyAccount tradeAccount = new PrivateKeyAccount(repository, tradePrivateKey);

			byte[] messageData = LitecoinACCTv1.buildRedeemMessage(secret, receiveAddress);
			MessageTransaction messageTransaction = MessageTransaction.build(repository, tradeAccount, Group.NO_GROUP, atAddress, messageData, false, false);

			System.out.println("Computing nonce...");
			messageTransaction.computeNonce();
			messageTransaction.sign(tradeAccount);

			byte[] signedBytes = null;
			try {
				signedBytes = TransactionTransformer.toBytes(messageTransaction.getTransactionData());
			} catch (TransformationException e) {
				System.err.println(String.format("Unable to convert transaction to bytes: %s", e.getMessage()));
				System.exit(2);
			}

			System.out.println(String.format("%nSigned transaction in base58, ready for POST /transactions/process:%n%s", Base58.encode(signedBytes)));
		} catch (DataException e) {
			System.err.println(String.format("Repository issue: %s", e.getMessage()));
			System.exit(2);
		}
	}

}
