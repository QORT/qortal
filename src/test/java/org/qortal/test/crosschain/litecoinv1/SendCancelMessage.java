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

public class SendCancelMessage {

	private static void usage(String error) {
		if (error != null)
			System.err.println(error);

		System.err.println(String.format("usage: SendCancelMessage <your Qortal PRIVATE key> <AT address>"));
		System.err.println(String.format("example: SendCancelMessage "
				+ "7Eztjz2TsxwbrWUYEaSdLbASKQGTfK2rR7ViFc5gaiZw \\\n"
				+ "\tAaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
		System.exit(1);
	}

	public static void main(String[] args) {
		if (args.length != 2)
			usage(null);

		Common.init();

		byte[] qortalPrivateKey = null;
		String atAddress = null;

		int argIndex = 0;
		try {
			qortalPrivateKey = Base58.decode(args[argIndex++]);
			if (qortalPrivateKey.length != 32)
				usage("Refund private key must be 32 bytes");

			atAddress = args[argIndex++];
			if (!Crypto.isValidAtAddress(atAddress))
				usage("Invalid AT address");
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
			PrivateKeyAccount qortalAccount = new PrivateKeyAccount(repository, qortalPrivateKey);

			String creatorQortalAddress = qortalAccount.getAddress();
			System.out.println(String.format("Qortal address: %s", creatorQortalAddress));

			byte[] messageData = LitecoinACCTv1.getInstance().buildCancelMessage(creatorQortalAddress);
			MessageTransaction messageTransaction = MessageTransaction.build(repository, qortalAccount, Group.NO_GROUP, atAddress, messageData, false, false);

			System.out.println("Computing nonce...");
			messageTransaction.computeNonce();
			messageTransaction.sign(qortalAccount);

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
