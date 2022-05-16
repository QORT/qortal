package org.qortal.test.common;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.PaymentTransactionData;
import org.qortal.data.transaction.RewardShareTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.utils.Amounts;

public class AccountUtils {

	public static final int txGroupId = Group.NO_GROUP;
	public static final long fee = 1L * Amounts.MULTIPLIER;

	public static void pay(Repository repository, String testSenderName, String testRecipientName, long amount) throws DataException {
		PrivateKeyAccount sendingAccount = Common.getTestAccount(repository, testSenderName);
		PrivateKeyAccount recipientAccount = Common.getTestAccount(repository, testRecipientName);

		pay(repository, sendingAccount, recipientAccount.getAddress(), amount);
	}

	public static void pay(Repository repository, PrivateKeyAccount sendingAccount, String recipientAddress, long amount) throws DataException {
		byte[] reference = sendingAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, sendingAccount.getPublicKey(), fee, null);
		TransactionData transactionData = new PaymentTransactionData(baseTransactionData, recipientAddress, amount);

		TransactionUtils.signAndMint(repository, transactionData, sendingAccount);
	}

	public static TransactionData createRewardShare(Repository repository, String minter, String recipient, int sharePercent) throws DataException {
		PrivateKeyAccount mintingAccount = Common.getTestAccount(repository, minter);
		PrivateKeyAccount recipientAccount = Common.getTestAccount(repository, recipient);

		byte[] reference = mintingAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		byte[] rewardSharePrivateKey = mintingAccount.getRewardSharePrivateKey(recipientAccount.getPublicKey());
		byte[] rewardSharePublicKey = PrivateKeyAccount.toPublicKey(rewardSharePrivateKey);

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, mintingAccount.getPublicKey(), fee, null);
		TransactionData transactionData = new RewardShareTransactionData(baseTransactionData, recipientAccount.getAddress(), rewardSharePublicKey, sharePercent);

		return transactionData;
	}

	public static byte[] rewardShare(Repository repository, String minter, String recipient, int sharePercent) throws DataException {
		TransactionData transactionData = createRewardShare(repository, minter, recipient, sharePercent);

		PrivateKeyAccount rewardShareAccount = Common.getTestAccount(repository, minter);
		TransactionUtils.signAndMint(repository, transactionData, rewardShareAccount);

		PrivateKeyAccount recipientAccount = Common.getTestAccount(repository, recipient);
		byte[] rewardSharePrivateKey = rewardShareAccount.getRewardSharePrivateKey(recipientAccount.getPublicKey());

		return rewardSharePrivateKey;
	}

	public static Map<String, Map<Long, Long>> getBalances(Repository repository, long... assetIds) throws DataException {
		Map<String, Map<Long, Long>> balances = new HashMap<>();

		for (TestAccount account : Common.getTestAccounts(repository))
			for (Long assetId : assetIds) {
				long balance = account.getConfirmedBalance(assetId);

				balances.compute(account.accountName, (key, value) -> {
					if (value == null)
						value = new HashMap<Long, Long>();

					value.put(assetId, balance);

					return value;
				});
			}

		return balances;
	}

	public static long getBalance(Repository repository, String accountName, long assetId) throws DataException {
		return Common.getTestAccount(repository, accountName).getConfirmedBalance(assetId);
	}

	public static void assertBalance(Repository repository, String accountName, long assetId, long expectedBalance) throws DataException {
		long actualBalance = getBalance(repository, accountName, assetId);
		String assetName = repository.getAssetRepository().fromAssetId(assetId).getName();

		assertEquals(String.format("%s's %s [%d] balance incorrect", accountName, assetName, assetId), expectedBalance, actualBalance);
	}

}
