package org.qortal.test.common;

import com.google.common.primitives.Longs;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.account.PublicKeyAccount;
import org.qortal.crypto.Crypto;
import org.qortal.crypto.Qortal25519Extras;
import org.qortal.data.network.OnlineAccountData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.PaymentTransactionData;
import org.qortal.data.transaction.RewardShareTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transaction.Transaction;
import org.qortal.transform.Transformer;
import org.qortal.utils.Amounts;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.qortal.crypto.Qortal25519Extras.signForAggregation;

public class AccountUtils {

	public static final int txGroupId = Group.NO_GROUP;
	public static final long fee = 1L * Amounts.MULTIPLIER;

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
		return createRewardShare(repository, mintingAccount, recipientAccount, sharePercent, fee);
	}

	public static TransactionData createRewardShare(Repository repository, PrivateKeyAccount mintingAccount, PrivateKeyAccount recipientAccount, int sharePercent, long fee) throws DataException {
		byte[] reference = mintingAccount.getLastReference();
		long timestamp = repository.getTransactionRepository().fromSignature(reference).getTimestamp() + 1;

		byte[] rewardSharePrivateKey = mintingAccount.getRewardSharePrivateKey(recipientAccount.getPublicKey());
		byte[] rewardSharePublicKey = Crypto.toPublicKey(rewardSharePrivateKey);

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

	public static byte[] rewardShare(Repository repository, PrivateKeyAccount minterAccount, PrivateKeyAccount recipientAccount, int sharePercent) throws DataException {
		TransactionData transactionData = createRewardShare(repository, minterAccount, recipientAccount, sharePercent, fee);

		TransactionUtils.signAndMint(repository, transactionData, minterAccount);
		byte[] rewardSharePrivateKey = minterAccount.getRewardSharePrivateKey(recipientAccount.getPublicKey());

		return rewardSharePrivateKey;
	}

	public static List<PrivateKeyAccount> generateSponsorshipRewardShares(Repository repository, PrivateKeyAccount sponsorAccount, int accountsCount) throws DataException {
		final int sharePercent = 0;
		Random random = new Random();

		List<PrivateKeyAccount> sponsees = new ArrayList<>();
		for (int i = 0; i < accountsCount; i++) {

			// Generate random sponsee account
			byte[] randomPrivateKey = new byte[32];
			random.nextBytes(randomPrivateKey);
			PrivateKeyAccount sponseeAccount = new PrivateKeyAccount(repository, randomPrivateKey);
			sponsees.add(sponseeAccount);

			// Create reward-share
			TransactionData transactionData = AccountUtils.createRewardShare(repository, sponsorAccount, sponseeAccount, sharePercent, fee);
			TransactionUtils.signAndImportValid(repository, transactionData, sponsorAccount);
		}

		return sponsees;
	}

	public static Account createRandomAccount(Repository repository) {
		byte[] randomPublicKey = new byte[32];
		new Random().nextBytes(randomPublicKey);
		return new PublicKeyAccount(repository, randomPublicKey);
	}

	public static Transaction.ValidationResult createRandomRewardShare(Repository repository, PrivateKeyAccount account) throws DataException {
		// Bob attempts to create a reward share transaction
		byte[] randomPrivateKey = new byte[32];
		new Random().nextBytes(randomPrivateKey);
		PrivateKeyAccount sponseeAccount = new PrivateKeyAccount(repository, randomPrivateKey);
		TransactionData transactionData = createRewardShare(repository, account, sponseeAccount, 0, fee);
		return TransactionUtils.signAndImport(repository, transactionData, account);
	}

	public static List<PrivateKeyAccount> generateSelfShares(Repository repository, List<PrivateKeyAccount> accounts) throws DataException {
		final int sharePercent = 0;

		for (PrivateKeyAccount account : accounts) {
			// Create reward-share
			TransactionData transactionData = createRewardShare(repository, account, account, sharePercent, 0L);
			TransactionUtils.signAndImportValid(repository, transactionData, account);
		}

		return toRewardShares(repository, null, accounts);
	}

	public static List<PrivateKeyAccount> toRewardShares(Repository repository, PrivateKeyAccount parentAccount, List<PrivateKeyAccount> accounts) {
		List<PrivateKeyAccount> rewardShares = new ArrayList<>();

		for (PrivateKeyAccount account : accounts) {
			PrivateKeyAccount sponsor = (parentAccount != null) ? parentAccount : account;
			byte[] rewardSharePrivateKey = sponsor.getRewardSharePrivateKey(account.getPublicKey());
			PrivateKeyAccount rewardShareAccount = new PrivateKeyAccount(repository, rewardSharePrivateKey);
			rewardShares.add(rewardShareAccount);
		}

		return rewardShares;
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

	public static void assertBalanceGreaterThan(Repository repository, String accountName, long assetId, long minimumBalance) throws DataException {
		long actualBalance = getBalance(repository, accountName, assetId);
		String assetName = repository.getAssetRepository().fromAssetId(assetId).getName();

		assertTrue(String.format("%s's %s [%d] balance incorrect", accountName, assetName, assetId), actualBalance > minimumBalance);
	}


	public static int getBlocksMinted(Repository repository, String accountName) throws DataException {
		return Common.getTestAccount(repository, accountName).getBlocksMinted();
	}

	public static void assertBlocksMinted(Repository repository, String accountName, int expectedBlocksMinted) throws DataException {
		int actualBlocksMinted = getBlocksMinted(repository, accountName);

		assertEquals(String.format("%s's blocks minted incorrect", accountName), expectedBlocksMinted, actualBlocksMinted);
	}


	public static List<OnlineAccountData> generateOnlineAccounts(int numAccounts) {
		List<OnlineAccountData> onlineAccounts = new ArrayList<>();

		long timestamp = System.currentTimeMillis();
		byte[] timestampBytes = Longs.toByteArray(timestamp);

		for (int a = 0; a < numAccounts; ++a) {
			byte[] privateKey = new byte[Transformer.PUBLIC_KEY_LENGTH];
			SECURE_RANDOM.nextBytes(privateKey);

			byte[] publicKey = new byte[Transformer.PUBLIC_KEY_LENGTH];
			Qortal25519Extras.generatePublicKey(privateKey, 0, publicKey, 0);

			byte[] signature = signForAggregation(privateKey, timestampBytes);

			Integer nonce = new Random().nextInt(500000);

			onlineAccounts.add(new OnlineAccountData(timestamp, signature, publicKey, nonce));
		}

		return onlineAccounts;
	}

}
