package org.qortal.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PublicKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.crosschain.BitcoinACCTv1;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.AccountBalanceData;
import org.qortal.data.account.AccountData;
import org.qortal.data.chat.ChatMessage;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class RepositoryTests extends Common {

	private static final Logger LOGGER = LogManager.getLogger(RepositoryTests.class);

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testGetRepository() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			assertNotNull(repository);
		}
	}

	@Test
	public void testMultipleInstances() throws DataException {
		int n_instances = 5;
		Repository[] repositories = new Repository[n_instances];

		for (int i = 0; i < n_instances; ++i) {
			repositories[i] = RepositoryManager.getRepository();
			assertNotNull(repositories[i]);
		}

		for (int i = 0; i < n_instances; ++i) {
			repositories[i].close();
			repositories[i] = null;
		}
	}

	@Test
	public void testAccessAfterClose() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			assertNotNull(repository);

			repository.close();

			try {
				repository.discardChanges();
				fail();
			} catch (NullPointerException | DataException e) {
			}

			LOGGER.warn("Expect \"repository already closed\" complaint below");
		}
	}

	@Test
	public void testDeadlock() {
		// Open connection 1
		try (final Repository repository1 = RepositoryManager.getRepository()) {

			// Do a database 'read'
			Account account1 = Common.getTestAccount(repository1, "alice");
			account1.getLastReference();

			// Open connection 2
			try (final Repository repository2 = RepositoryManager.getRepository()) {
				// Update account in 2
				Account account2 = Common.getTestAccount(repository2, "alice");
				account2.setConfirmedBalance(Asset.QORT, 1234L);
				repository2.saveChanges();
			}

			repository1.discardChanges();

			// Update account in 1
			account1.setConfirmedBalance(Asset.QORT, 5678L);
			repository1.saveChanges();
		} catch (DataException e) {
			fail("deadlock bug");
		}
	}

	@Test
	public void testUpdateReadDeadlock() {
		// Open connection 1
		try (final Repository repository1 = RepositoryManager.getRepository()) {
			// Mint blocks so we have data (online account signatures) to work with
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository1);

			// Perform database 'update', but don't commit at this stage
			repository1.getBlockRepository().trimOldOnlineAccountsSignatures(1, 10);

			// Open connection 2
			try (final Repository repository2 = RepositoryManager.getRepository()) {
				// Perform database read on same blocks - this should not deadlock
				repository2.getBlockRepository().getTimestampFromHeight(5);
			}

			// Save updates - this should not deadlock
			repository1.saveChanges();
		} catch (DataException e) {
			fail("deadlock bug");
		}
	}

	@Test
	public void testTrimDeadlock() {
		ExecutorService executor = Executors.newCachedThreadPool();
		CountDownLatch readyLatch = new CountDownLatch(1);
		CountDownLatch updateLatch = new CountDownLatch(1);
		CountDownLatch syncLatch = new CountDownLatch(1);

		// Open connection 1
		try (final HSQLDBRepository repository1 = (HSQLDBRepository) RepositoryManager.getRepository()) {
			// Read AT states trim height
			int atTrimHeight = repository1.getATRepository().getAtTrimHeight();
			repository1.discardChanges();

			// Open connection 2
			try (final HSQLDBRepository repository2 = (HSQLDBRepository) RepositoryManager.getRepository()) {
				// Read online signatures trim height
				int onlineSignaturesTrimHeight = repository2.getBlockRepository().getOnlineAccountsSignaturesTrimHeight();
				repository2.discardChanges();

				Future<Boolean> f2 = executor.submit(() -> {
					Object trimHeightsLock = extractTrimHeightsLock(repository2);
					System.out.println(String.format("f2: repository2's trimHeightsLock object: %s", trimHeightsLock));

					// Update online signatures trim height (implicit commit)
					synchronized (trimHeightsLock) {
						try {
							System.out.println("f2: updating online signatures trim height...");
							// simulate: repository2.getBlockRepository().setOnlineAccountsSignaturesTrimHeight(onlineSignaturesTrimHeight);
							String updateSql = "UPDATE DatabaseInfo SET online_signatures_trim_height = ?";
							PreparedStatement pstmt = repository2.prepareStatement(updateSql);
							pstmt.setInt(1, onlineSignaturesTrimHeight);
							pstmt.executeUpdate();
							// But no commit/saveChanges yet to force HSQLDB error

							System.out.println("f2: readyLatch.countDown()");
							readyLatch.countDown();

							// wait for other thread to be ready to hit sync block
							System.out.println("f2: waiting for f1 syncLatch...");
							syncLatch.await();

							// hang on to trimHeightsLock to force other thread to wait (if code is correct), or to fail (if code is faulty)
							System.out.println("f2: updateLatch.await(<with timeout>)");
							if (!updateLatch.await(500L, TimeUnit.MILLISECONDS)) { // long enough for other thread to reach synchronized block
								// wait period expired suggesting no concurrent access, i.e. code is correct
								System.out.println("f2: updateLatch.await() timed out");

								System.out.println("f2: saveChanges()");
								repository2.saveChanges();

								return Boolean.TRUE;
							}

							System.out.println("f2: saveChanges()");
							repository2.saveChanges();

							// Early exit from wait period suggests concurrent access, i.e. code faulty
							return Boolean.FALSE;
						} catch (InterruptedException | SQLException e) {
							System.out.println("f2: exception: " + e.getMessage());
							return Boolean.FALSE;
						}
					}
				});

				System.out.println("waiting for f2 readyLatch...");
				readyLatch.await();
				System.out.println("launching f1...");

				Future<Boolean> f1 = executor.submit(() -> {
					Object trimHeightsLock = extractTrimHeightsLock(repository1);
					System.out.println(String.format("f1: repository1's trimHeightsLock object: %s", trimHeightsLock));

					System.out.println("f1: syncLatch.countDown()");
					syncLatch.countDown();

					// Update AT states trim height (implicit commit)
					synchronized (trimHeightsLock) {
						try {
							System.out.println("f1: updating AT trim height...");
							// simulate: repository1.getATRepository().setAtTrimHeight(atTrimHeight);
							String updateSql = "UPDATE DatabaseInfo SET AT_trim_height = ?";
							PreparedStatement pstmt = repository1.prepareStatement(updateSql);
							pstmt.setInt(1, atTrimHeight);
							pstmt.executeUpdate();
							System.out.println("f1: saveChanges()");
							repository1.saveChanges();

							System.out.println("f1: updateLatch.countDown()");
							updateLatch.countDown();

							return Boolean.TRUE;
						} catch (SQLException e) {
							System.out.println("f1: exception: " + e.getMessage());
							return Boolean.FALSE;
						}
					}
				});

				if (Boolean.TRUE != f1.get())
					fail("concurrency bug - simultaneous update of DatabaseInfo table");

				if (Boolean.TRUE != f2.get())
					fail("concurrency bug - not synchronized on same object?");
			} catch (InterruptedException e) {
				fail("concurrency bug: " + e.getMessage());
			} catch (ExecutionException e) {
				fail("concurrency bug: " + e.getMessage());
			}
		} catch (DataException e) {
			fail("database bug");
		}
	}

	private static Object extractTrimHeightsLock(HSQLDBRepository repository) {
		try {
			Field trimHeightsLockField = repository.getClass().getDeclaredField("trimHeightsLock");
			trimHeightsLockField.setAccessible(true);
			return trimHeightsLockField.get(repository);
		} catch (IllegalArgumentException | NoSuchFieldException | SecurityException | IllegalAccessException e) {
			fail();
			return null;
		}
	}

	/** Check that the <i>sub-query</i> used to fetch highest block height is optimized by HSQLDB. */
	@Test
	public void testBlockHeightSpeed() throws DataException, SQLException {
		final int mintBlockCount = 10000;

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Mint some blocks
			System.out.println(String.format("Minting %d test blocks - should take approx. 10 seconds...", mintBlockCount));

			long beforeBigMint = System.currentTimeMillis();
			for (int i = 0; i < mintBlockCount; ++i)
				BlockUtils.mintBlock(repository);

			System.out.println(String.format("Minting %d blocks actually took %d seconds", mintBlockCount, (System.currentTimeMillis() - beforeBigMint) / 1000L));

			final HSQLDBRepository hsqldb = (HSQLDBRepository) repository;

			// Too slow:
			testSql(hsqldb, "SELECT IFNULL(MAX(height), 0) + 1 FROM Blocks", false);

			// Fast but if there are no rows, then no result is returned, which causes some triggers to fail:
			testSql(hsqldb, "SELECT IFNULL(height, 0) + 1 FROM (SELECT height FROM Blocks ORDER BY height DESC LIMIT 1)", true);

			// Too slow:
			testSql(hsqldb, "SELECT COUNT(*) + 1 FROM Blocks", false);

			// 2-stage, using cached value:
			hsqldb.prepareStatement("DROP TABLE IF EXISTS TestNextBlockHeight").execute();
			hsqldb.prepareStatement("CREATE TABLE TestNextBlockHeight (height INT NOT NULL)").execute();
			hsqldb.prepareStatement("INSERT INTO TestNextBlockHeight VALUES (SELECT IFNULL(MAX(height), 0) + 1 FROM Blocks)").execute();

			// 1: Check fetching cached next block height is fast:
			testSql(hsqldb, "SELECT height from TestNextBlockHeight", true);

			// 2: Check updating NextBlockHeight (typically called via trigger) is fast:
			testSql(hsqldb, "UPDATE TestNextBlockHeight SET height = (SELECT height FROM Blocks ORDER BY height DESC LIMIT 1)", true);
		}
	}

	/** Test proper action of interrupt inside an HSQLDB statement. */
	@Test
	public void testInterrupt() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			final Thread testThread = Thread.currentThread();
			System.out.println(String.format("Thread ID: %s", testThread.getId()));

			// Queue interrupt
			ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
			executor.schedule(() -> testThread.interrupt(), 1000L, TimeUnit.MILLISECONDS);

			// Set rollback on interrupt
			@SuppressWarnings("resource")
			final HSQLDBRepository hsqldb = (HSQLDBRepository) repository;
			hsqldb.prepareStatement("SET DATABASE TRANSACTION ROLLBACK ON INTERRUPT TRUE").execute();

			// Create SQL procedure that calls hsqldbSleep() to block HSQLDB so we can interrupt()
			hsqldb.prepareStatement("CREATE PROCEDURE sleep(IN millis INT) LANGUAGE JAVA DETERMINISTIC NO SQL EXTERNAL NAME 'CLASSPATH:org.qortal.test.RepositoryTests.hsqldbSleep'").execute();

			// Execute long-running statement
			hsqldb.prepareStatement("CALL sleep(2000)").execute();

			if (!testThread.isInterrupted())
				// We should not reach here
				fail("Interrupt was swallowed");
		} catch (DataException | SQLException e) {
			fail("DataException during blocked statement");
		}
	}

	/**
	 * Test HSQLDB bug-fix for INSERT INTO...ON DUPLICATE KEY UPDATE... bug
	 * <p>
	 * @see <A HREF="https://sourceforge.net/p/hsqldb/discussion/73674/thread/d8d35adb5d/">Behaviour of 'ON DUPLICATE KEY UPDATE'</A> SourceForge discussion
	 */
	@Test
	public void testOnDuplicateKeyUpdateBugFix() throws SQLException, DataException {
		ResultSet resultSet;

		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {
			hsqldb.prepareStatement("DROP TABLE IF EXISTS bugtest").execute();
			hsqldb.prepareStatement("CREATE TABLE bugtest (id INT NOT NULL, counter INT NOT NULL, PRIMARY KEY(id))").execute();

			// No existing row, so new row's "counter" is set to value from VALUES clause, i.e. 1
			hsqldb.prepareStatement("INSERT INTO bugtest (id, counter) VALUES (1, 1) ON DUPLICATE KEY UPDATE counter = counter + 1").execute();
			resultSet = hsqldb.checkedExecute("SELECT counter FROM bugtest WHERE id = 1");
			assertNotNull(resultSet);
			assertEquals(1, resultSet.getInt(1));

			// Prior to bug-fix, "counter = counter + 1" would always use the 100 from VALUES, instead of existing row's value, for "counter"
			hsqldb.prepareStatement("INSERT INTO bugtest (id, counter) VALUES (1, 100) ON DUPLICATE KEY UPDATE counter = counter + 1").execute();
			resultSet = hsqldb.checkedExecute("SELECT counter FROM bugtest WHERE id = 1");
			assertNotNull(resultSet);
			// Prior to bug-fix, this would be 100 + 1 = 101
			assertEquals(2, resultSet.getInt(1));
		}
	}

	/**
	 * Test HSQLDB bug-fix for "General Error" in non-fully-qualified columns inside LATERAL()
	 * <p>
	 * @see <A HREF="https://sourceforge.net/p/hsqldb/bugs/1580/">#1580 General error with LATERAL and transitive join column</A> SourceForge ticket
	 */
	@Test
	public void testOnLateralGeneralError() {
		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {
			hsqldb.prepareStatement("DROP TABLE IF EXISTS tableA").execute();
			hsqldb.prepareStatement("DROP TABLE IF EXISTS tableB").execute();
			hsqldb.prepareStatement("DROP TABLE IF EXISTS tableC").execute();

			hsqldb.prepareStatement("CREATE TABLE tableA (col1 INT)").execute();
			hsqldb.prepareStatement("CREATE TABLE tableB (col1 INT)").execute();
			hsqldb.prepareStatement("CREATE TABLE tableC (col2 INT, PRIMARY KEY (col2))").execute();

			// Prior to bug-fix #1580 this would throw a General Error SQL Exception
			hsqldb.prepareStatement("SELECT col3 FROM tableA JOIN tableB USING (col1) CROSS JOIN LATERAL(SELECT col2 FROM tableC WHERE col2 = col1) AS tableC (col3)").execute();
		} catch (SQLException | DataException e) {
			fail("HSQLDB bug #1580");
		}
	}

	/** Specifically test LATERAL() usage in Asset repository */
	@Test
	public void testAssetLateral() {
		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {
			List<Long> assetIds = Collections.emptyList();
			List<Long> otherAssetIds = Collections.emptyList();
			Integer limit = null;
			Integer offset = null;
			Boolean reverse = null;

			hsqldb.getAssetRepository().getRecentTrades(assetIds, otherAssetIds, limit, offset, reverse);
		} catch (DataException e) {
			fail("HSQLDB bug #1580");
		}
	}

	/** Specifically test LATERAL() usage in AT repository */
	@Test
	public void testAtLateral() {
		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {
			byte[] codeHash = BitcoinACCTv1.CODE_BYTES_HASH;
			Boolean isFinished = null;
			Integer dataByteOffset = null;
			Long expectedValue = null;
			Integer minimumFinalHeight = 2;
			Integer limit = null;
			Integer offset = null;
			Boolean reverse = null;

			hsqldb.getATRepository().getMatchingFinalATStates(codeHash,null, null, isFinished, dataByteOffset, expectedValue, minimumFinalHeight, limit, offset, reverse);
		} catch (DataException e) {
			fail("HSQLDB bug #1580");
		}
	}

	/** Specifically test LATERAL() usage in Chat repository with hasChatReference */
	@Test
	public void testChatLateral() {
		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {
			String address = Crypto.toAddress(new byte[32]);

			// Test without hasChatReference
			hsqldb.getChatRepository().getActiveChats(address, ChatMessage.Encoding.BASE58, null);

			// Test with hasChatReference = true
			hsqldb.getChatRepository().getActiveChats(address, ChatMessage.Encoding.BASE58, true);

			// Test with hasChatReference = false
			hsqldb.getChatRepository().getActiveChats(address, ChatMessage.Encoding.BASE58, false);
		} catch (DataException e) {
			fail("HSQLDB bug #1580");
		}
	}

	/** Test batched DELETE */
	@Test
	public void testBatchedDelete() {
		// Generate test data
		List<Object[]> batchedObjects = new ArrayList<>();
		for (int i = 0; i < 100; ++i)
			batchedObjects.add(new Object[] { String.valueOf(i), 1L });

		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {
			hsqldb.deleteBatch("AccountBalances", "account = ? AND asset_id = ?", batchedObjects);
		} catch (DataException | SQLException e) {
			fail("Batched delete failed: " + e.getMessage());
		}
	}

	@Test
	public void testDefrag() throws DataException, TimeoutException {
		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {

			this.populateWithRandomData(hsqldb);

			hsqldb.performPeriodicMaintenance(10 * 1000L);

		}
	}

	@Test
	public void testDefragOnDisk() throws DataException, TimeoutException {
		Common.useSettingsAndDb(testSettingsFilename, false);

		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {

			this.populateWithRandomData(hsqldb);

			hsqldb.performPeriodicMaintenance(10 * 1000L);

		}
	}

	@Test
	public void testMultipleDefrags() throws DataException, TimeoutException {
		// Mint some more blocks to populate the database
		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {

			this.populateWithRandomData(hsqldb);

			for (int i = 0; i < 10; i++) {
				hsqldb.performPeriodicMaintenance(10 * 1000L);
			}
		}
	}

	@Test
	public void testMultipleDefragsOnDisk() throws DataException, TimeoutException {
		Common.useSettingsAndDb(testSettingsFilename, false);

		// Mint some more blocks to populate the database
		try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {

			this.populateWithRandomData(hsqldb);

			for (int i = 0; i < 10; i++) {
				hsqldb.performPeriodicMaintenance(10 * 1000L);
			}
		}
	}

	@Test
	public void testMultipleDefragsWithDifferentData() throws DataException, TimeoutException {
		for (int i=0; i<10; i++) {
			try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {

				this.populateWithRandomData(hsqldb);
				hsqldb.performPeriodicMaintenance(10 * 1000L);
			}
		}
	}

	@Test
	public void testMultipleDefragsOnDiskWithDifferentData() throws DataException, TimeoutException {
		Common.useSettingsAndDb(testSettingsFilename, false);

		for (int i=0; i<10; i++) {
			try (final HSQLDBRepository hsqldb = (HSQLDBRepository) RepositoryManager.getRepository()) {

				this.populateWithRandomData(hsqldb);
				hsqldb.performPeriodicMaintenance(10 * 1000L);
			}
		}
	}

	private void populateWithRandomData(HSQLDBRepository repository) throws DataException {
		Random random = new Random();

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
	}

	public static void hsqldbSleep(int millis) throws SQLException {
		System.out.println(String.format("HSQLDB sleep() thread ID: %s", Thread.currentThread().getId()));

		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void testSql(HSQLDBRepository hsqldb, String sql, boolean isFast) throws DataException, SQLException {
		// Execute query to prime caches
		hsqldb.prepareStatement(sql).execute();

		// Execute again for a slightly more accurate timing
		final long start = System.currentTimeMillis();
		hsqldb.prepareStatement(sql).execute();

		final long executionTime = System.currentTimeMillis() - start;
		System.out.println(String.format("%s: [%d ms] SQL: %s", (isFast ? "fast": "slow"), executionTime, sql));

		final long threshold = 3; // ms
		assertTrue( !isFast || executionTime < threshold);
	}

}
