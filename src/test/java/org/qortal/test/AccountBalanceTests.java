package org.qortal.test;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.data.account.AccountBalanceData;
import org.qortal.data.account.AccountData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.AccountRepository.BalanceOrdering;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TestAccount;

public class AccountBalanceTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	/** Tests that newer balances are returned instead of older ones. */
	@Test
	public void testNewerBalance() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			TestAccount alice = Common.getTestAccount(repository, "alice");

			testNewerBalance(repository, alice);
		}
	}

	private long testNewerBalance(Repository repository, TestAccount testAccount) throws DataException {
		// Grab initial balance
		long initialBalance = testAccount.getConfirmedBalance(Asset.QORT);

		// Mint block to cause newer balance
		BlockUtils.mintBlock(repository);

		// Grab newer balance
		long newerBalance = testAccount.getConfirmedBalance(Asset.QORT);

		// Confirm newer balance is greater than initial balance
		assertTrue("Newer balance should be greater than initial balance", newerBalance > initialBalance);

		return initialBalance;
	}

	/** Tests that orphaning reverts balance back to initial. */
	@Test
	public void testOrphanedBalance() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			TestAccount alice = Common.getTestAccount(repository, "alice");

			long initialBalance = testNewerBalance(repository, alice);

			BlockUtils.orphanLastBlock(repository);

			// Grab post-orphan balance
			long orphanedBalance = alice.getConfirmedBalance(Asset.QORT);

			// Confirm post-orphan balance is same as initial
			assertEquals("Post-orphan balance should match initial", initialBalance, orphanedBalance);
		}
	}

	/** Tests SQL query speed for account balance fetches. */
	@Test
	public void testRepositorySpeed() throws DataException, SQLException {
		Random random = new Random();
		final long MAX_QUERY_TIME = 80L; // ms

		try (final Repository repository = RepositoryManager.getRepository()) {
			System.out.println("Creating random accounts...");

			// Generate some random accounts
			List<Account> accounts = new ArrayList<>();
			for (int ai = 0; ai < 20; ++ai) {
				byte[] publicKey = new byte[32];
				random.nextBytes(publicKey);

				PublicKeyAccount account = new PublicKeyAccount(repository, publicKey);
				accounts.add(account);

				AccountData accountData = new AccountData(account.getAddress());
				repository.getAccountRepository().ensureAccount(accountData);
			}
			repository.saveChanges();

			System.out.println("Creating random balances...");

			// Fill with lots of random balances
			for (int i = 0; i < 100000; ++i) {
				Account account = accounts.get(random.nextInt(accounts.size()));
				int assetId = random.nextInt(2);
				long balance = random.nextInt(100000);

				AccountBalanceData accountBalanceData = new AccountBalanceData(account.getAddress(), assetId, balance);
				repository.getAccountRepository().save(accountBalanceData);

				// Maybe mint a block to change height
				if (i > 0 && (i % 1000) == 0)
					BlockUtils.mintBlock(repository);
			}
			repository.saveChanges();

			// Address filtering test cases
			List<String> testAddresses = accounts.stream().limit(3).map(account -> account.getAddress()).collect(Collectors.toList());
			List<List<String>> addressFilteringCases = Arrays.asList(null, testAddresses);

			// AssetID filtering test cases
			List<List<Long>> assetIdFilteringCases = Arrays.asList(null, Arrays.asList(0L, 1L, 2L));

			// Results ordering test cases
			List<BalanceOrdering> orderingCases = new ArrayList<>();
			orderingCases.add(null);
			orderingCases.addAll(Arrays.asList(BalanceOrdering.values()));

			// Zero exclusion test cases
			List<Boolean> zeroExclusionCases = Arrays.asList(null, true, false);

			// Limit test cases
			List<Integer> limitCases = Arrays.asList(null, 10);

			// Offset test cases
			List<Integer> offsetCases = Arrays.asList(null, 10);

			// Reverse results cases
			List<Boolean> reverseCases = Arrays.asList(null, true, false);

			repository.setDebug(true);

			// Test all cases
			for (List<String> addresses : addressFilteringCases)
				for (List<Long> assetIds : assetIdFilteringCases)
					for (BalanceOrdering balanceOrdering : orderingCases)
						for (Boolean excludeZero : zeroExclusionCases)
							for (Integer limit : limitCases)
								for (Integer offset : offsetCases)
									for (Boolean reverse : reverseCases) {
										repository.discardChanges();

										System.out.println(String.format("Testing query: %s addresses, %s assetIDs, %s ordering, %b zero-exclusion, %d limit, %d offset, %b reverse",
												(addresses == null ? "no" : "with"), (assetIds == null ? "no" : "with"), balanceOrdering, excludeZero, limit, offset, reverse));

										long before = System.currentTimeMillis();
										repository.getAccountRepository().getAssetBalances(addresses, assetIds, balanceOrdering, excludeZero, limit, offset, reverse);
										final long period = System.currentTimeMillis() - before;
										assertTrue(String.format("Query too slow: %dms", period), period < MAX_QUERY_TIME);
									}
		}

		// Rebuild repository to avoid orphan check
		Common.useDefaultSettings();
	}

	/** Test batch set/delete of account balances */
	@Test
	public void testBatchedBalanceChanges() throws DataException, SQLException {
		Random random = new Random();
		int ai;

		try (final Repository repository = RepositoryManager.getRepository()) {
			System.out.println("Creating random accounts...");

			// Generate some random accounts
			List<Account> accounts = new ArrayList<>();
			for (ai = 0; ai < 2000; ++ai) {
				byte[] publicKey = new byte[32];
				random.nextBytes(publicKey);

				PublicKeyAccount account = new PublicKeyAccount(repository, publicKey);
				accounts.add(account);
			}

			List<AccountBalanceData> accountBalances = new ArrayList<>();

			System.out.println("Setting random balances...");

			// Fill with lots of random balances
			for (ai = 0; ai < accounts.size(); ++ai) {
				Account account = accounts.get(ai);
				int assetId = random.nextInt(2);
				// random zero, or non-zero, balance
				long balance = random.nextBoolean() ? 0L : random.nextInt(100000);

				accountBalances.add(new AccountBalanceData(account.getAddress(), assetId, balance));
			}

			repository.getAccountRepository().setAssetBalances(accountBalances);
			repository.saveChanges();

			System.out.println("Setting new random balances...");

			// Now flip zero-ness for first half of balances
			for (ai = 0; ai < accountBalances.size() / 2; ++ai) {
				AccountBalanceData accountBalanceData = accountBalances.get(ai);

				accountBalanceData.setBalance(accountBalanceData.getBalance() != 0 ? 0L : random.nextInt(100000));
			}
			// ...and randomize the rest
			for (/*use ai from before*/; ai < accountBalances.size(); ++ai) {
				AccountBalanceData accountBalanceData = accountBalances.get(ai);

				accountBalanceData.setBalance(random.nextBoolean() ? 0L : random.nextInt(100000));
			}

			repository.getAccountRepository().setAssetBalances(accountBalances);
			repository.saveChanges();

			System.out.println("Modifying random balances...");

			// Fill with lots of random balance changes
			for (ai = 0; ai < accounts.size(); ++ai) {
				Account account = accounts.get(ai);
				int assetId = random.nextInt(2);
				// random zero, or non-zero, balance
				long balance = random.nextBoolean() ? 0L : random.nextInt(100000);

				accountBalances.add(new AccountBalanceData(account.getAddress(), assetId, balance));
			}

			repository.getAccountRepository().modifyAssetBalances(accountBalances);
			repository.saveChanges();

			System.out.println("Deleting all balances...");

			// Now simply delete all balances
			for (ai = 0; ai < accountBalances.size(); ++ai)
				accountBalances.get(ai).setBalance(0L);

			repository.getAccountRepository().setAssetBalances(accountBalances);
			repository.saveChanges();
		}
	}

}