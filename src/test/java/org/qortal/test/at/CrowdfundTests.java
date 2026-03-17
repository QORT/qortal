package org.qortal.test.at;

import org.ciyam.at.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.Block;
import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.*;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.*;
import org.qortal.transaction.*;
import org.qortal.utils.*;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class CrowdfundTests extends Common {

    /*
	"QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v", "amount": "1000000000"
	"QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK", "amount": "1000000"
	"QaUpHNhT3Ygx6avRiKobuLdusppR5biXjL", "amount": "1000000"
	"Qci5m9k4rcwe4ruKrZZQKka4FzUUMut3er", "amount": "1000000"
	 */

	private static final String aliceAddress = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v";
	private static final String bobAddress = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK";
	private static final String chloeAddress = "QaUpHNhT3Ygx6avRiKobuLdusppR5biXjL";
	private static final String dilbertAddress = "Qci5m9k4rcwe4ruKrZZQKka4FzUUMut3er";

	// Creation bytes from: java -cp 'target/qrowdfund-1.0.0.jar:target/dependency/*' org.qortal.at.qrowdfund.Qrowdfund 50 12385 Qci5m9k4rcwe4ruKrZZQKka4FzUUMut3er
	private static final String creationBytes58 = "1Pub6o13xyqfCZj8BMzmXsREVJR6h4xxpS2VPV1R2QwjP78r2ozxsNuvb28GWrT8FoTTQMGnVP7pNii6auUqYr2uunWfcxwhERbDgFdsJqtrJMpQNGB9GerAXYyiFiij35cP6eHw7BmALb3viT6VzqaXX9YB25iztekV5cTreJg7o2hRpFc9Rv8Z9dFXcD1Mm4WCaMaknUgchDi7qDnHA7JX8bn9EFD4WMG5nZHMsrmeqBHirURXr2dMxFprTBo187zztmw7izbv5KzMFP8aRP9uEqdTMhZJmvKqhapMK9UJkxMve3KnsxKn5yyaAeiZ4i9GNfrkjpz5T1VGomUaDmeatNti1bjQ2pwtcgZfFFbrnBFMU2kvcPx1UR53dArtRS7pFbNr3EFwnw2Yiu2xS3Z";
	private static final byte[] creationBytes = Base58.decode(creationBytes58);
	private static final long fundingAmount = 2_00000000L;
	private static final long SLEEP_PERIOD = 50L;

	private Repository repository = null;
	private PrivateKeyAccount deployer;
	private DeployAtTransaction deployAtTransaction;
	private Account atAccount;
	private String atAddress;
	private byte[] rawLastTxnTimestamp = new byte[8];
	private Transaction transaction;

	@Before
	public void before() throws DataException {
		Common.useDefaultSettings();

		this.repository = RepositoryManager.getRepository();
		this.deployer = Common.getTestAccount(repository, "alice");

		this.deployAtTransaction = doDeploy(repository, deployer, creationBytes, fundingAmount);
		this.atAccount = deployAtTransaction.getATAccount();
		this.atAddress = deployAtTransaction.getATAccount().getAddress();
	}

	@After
	public void after() throws DataException {
		if (this.repository != null)
			this.repository.close();

		this.repository = null;
	}

	@Test
	public void testDeploy() throws DataException {
		// Confirm initial value is zero
		extractLastTxTimestamp(repository, atAddress, rawLastTxnTimestamp);
		assertArrayEquals(new byte[8], rawLastTxnTimestamp);
	}

	@Test
	public void testThresholdNotMet() throws DataException {
		// AT deployment in block 2

		// Mint block to allow AT to initialize and call SLEEP_UNTIL_MESSAGE_OR_HEIGHT
		BlockUtils.mintBlock(repository); // height now 3

		// Fetch AT's balance for this height
		long preMintBalance = atAccount.getConfirmedBalance(Asset.QORT);

		// Fetch AT's initial lastTxnTimestamp
		byte[] creationTimestamp = new byte[8];
		extractLastTxTimestamp(repository, atAddress, creationTimestamp);

		// Mint several blocks
		int i = repository.getBlockRepository().getBlockchainHeight();
		long WAKE_HEIGHT = i + SLEEP_PERIOD;
		for (; i < WAKE_HEIGHT; ++i)
			BlockUtils.mintBlock(repository);

		// We should now be at WAKE_HEIGHT
		long height = repository.getBlockRepository().getBlockchainHeight();
		assertEquals(WAKE_HEIGHT, height);

		// AT should have woken and run at this height so balance should have changed

		// Fetch new AT balance
		long postMintBalance = atAccount.getConfirmedBalance(Asset.QORT);

		assertNotSame(preMintBalance, postMintBalance);

		// Confirm AT has found no payments
		extractLastTxTimestamp(repository, atAddress, rawLastTxnTimestamp);
		assertArrayEquals(creationTimestamp, rawLastTxnTimestamp);

		// AT should have finished
		ATData atData = repository.getATRepository().fromATAddress(atAddress);
		assertTrue(atData.getIsFinished());

		// AT should have sent balance back to creator
		BlockData blockData = repository.getBlockRepository().getLastBlock();
		Block block = new Block(repository, blockData);
		List<Transaction> transactions = block.getTransactions();

		assertEquals(1, transactions.size());

		Transaction transaction = transactions.get(0);
		AtTransaction atTransaction = (AtTransaction) transaction;
		assertEquals(aliceAddress, atTransaction.getRecipient().getAddress());
	}

	@Test
	public void testThresholdNotMetWithOrphanage() throws DataException {
		// AT deployment in block 2

		// Mint block to allow AT to initialize and call SLEEP_UNTIL_MESSAGE_OR_HEIGHT
		BlockUtils.mintBlock(repository); // height now 3

		// Fetch AT's initial lastTxnTimestamp
		byte[] creationTimestamp = new byte[8];
		extractLastTxTimestamp(repository, atAddress, creationTimestamp);

		// Mint several blocks
		int i = repository.getBlockRepository().getBlockchainHeight();
		long WAKE_HEIGHT = i + SLEEP_PERIOD;
		for (; i < WAKE_HEIGHT; ++i)
			BlockUtils.mintBlock(repository);

		// AT should have finished
		ATData atData = repository.getATRepository().fromATAddress(atAddress);
		assertTrue(atData.getIsFinished());

		// Orphan
		BlockUtils.orphanBlocks(repository, 3);

		// Mint several blocks
		for (i = 0; i < 3; ++i)
			BlockUtils.mintBlock(repository);

		// Confirm AT has found no payments
		extractLastTxTimestamp(repository, atAddress, rawLastTxnTimestamp);
		assertArrayEquals(creationTimestamp, rawLastTxnTimestamp);

		// AT should have finished
		atData = repository.getATRepository().fromATAddress(atAddress);
		assertTrue(atData.getIsFinished());

		// AT should have sent balance back to creator
		BlockData blockData = repository.getBlockRepository().getLastBlock();
		Block block = new Block(repository, blockData);
		List<Transaction> transactions = block.getTransactions();

		assertEquals(1, transactions.size());

		Transaction transaction = transactions.get(0);
		AtTransaction atTransaction = (AtTransaction) transaction;
		assertEquals(aliceAddress, atTransaction.getRecipient().getAddress());
	}

	@Test
	public void testThresholdNotMetWithPaymentsAndRefunds() throws DataException {
		// AT deployment in block 2

		// Mint block to allow AT to initialize and call SLEEP_UNTIL_MESSAGE_OR_HEIGHT
		BlockUtils.mintBlock(repository); // height now 3

		// Fetch AT's balance for this height
		long preMintBalance = atAccount.getConfirmedBalance(Asset.QORT);

		// Fetch AT's initial lastTxnTimestamp
		byte[] creationTimestamp = new byte[8];
		extractLastTxTimestamp(repository, atAddress, creationTimestamp);

		int i = repository.getBlockRepository().getBlockchainHeight();
		long WAKE_HEIGHT = i + SLEEP_PERIOD;

		// Create some test accounts, based on donations
		List<Pair<String,Long>> donations = List.of(
				new Pair<>("QRt11DVBnLaSDxr2KHvx92LdPrjhbhJtkj", 500L),
				new Pair<>("QRv7tHnaEpRtfovbTJqkJFmtnoahJrbPGg", 250L),
				new Pair<>("QRv7tHnaEpRtfovbTJqkJFmtnoahJrbPGg", 250L),
				new Pair<>("QczG8GXU5vPQLTZsJBASQd3fAKJzKwnubv", 250L),
				new Pair<>("QNuYHyW4HJn7v3dYUxoTLiyS5tpGQAguMJ", 20L),
				new Pair<>("QgVqcSZZ6HRhBvdUmpTvEonaQaH2oWfe58", 500L),
				new Pair<>("QfDaxmD8jKi3TovWA1NA8RL5rWYXRC12uX", 10L),
				new Pair<>("QSohMWUphRwtEuwAZKqoy8UGS13tk1bBDm", 15L),
				new Pair<>("QiNKXRfnX9mTodSed1yRQexhL1HA42RHHo", 420L),
				new Pair<>("Qgfh143pRJyxpS92JoazjXNMH1uZueQBZ2", 100L),
				new Pair<>("Qgfh143pRJyxpS92JoazjXNMH1uZueQBZ2", 100L)
		);
		Map<String, TestAccount> donors = donations.stream()
				.map(donation -> donation.getA())
				.distinct()
				.collect(Collectors.toMap(name -> name, name -> generateTestAccount(repository, name)));

		// Give donors some QORT so they can donate
		donors.values()
				.stream()
				.forEach(donorAccount -> {
					try {
						AccountUtils.pay(repository, Common.getTestAccount(repository, "alice"), donorAccount.getAddress(), 2000_00000000L);
					} catch (DataException e) {
						fail(e.getMessage());
					}
				});

		// Record balances
		Map<String, Long> initialDonorBalances = donors.values()
				.stream()
				.collect(Collectors.toMap(account -> account.getAddress(), account -> {
					try {
						return account.getConfirmedBalance(Asset.QORT);
					} catch (DataException e) {
						fail(e.getMessage());
						return null;
					}
				}));

		// Now make donations
		donations.stream()
				.forEach(donation -> {
					TestAccount donorAccount = donors.get(donation.getA());
					try {
						AccountUtils.pay(repository, donorAccount, atAddress, donation.getB() * 1_00000000L);
						System.out.printf("AT balance at height %d is %s\n", repository.getBlockRepository().getBlockchainHeight(), Amounts.prettyAmount(atAccount.getConfirmedBalance(Asset.QORT)));
					} catch (DataException e) {
						fail(e.getMessage());
					}
				});

		// Mint several blocks
		i = repository.getBlockRepository().getBlockchainHeight();
		for (; i < WAKE_HEIGHT; ++i) {
			BlockUtils.mintBlock(repository);
			System.out.printf("AT balance at height %d is %s\n", repository.getBlockRepository().getBlockchainHeight(), Amounts.prettyAmount(atAccount.getConfirmedBalance(Asset.QORT)));
		}

		// We should now be at WAKE_HEIGHT
		long height = repository.getBlockRepository().getBlockchainHeight();
		assertEquals(WAKE_HEIGHT, height);

		// AT should have woken and run at this height so balance should have changed
		System.out.printf("AT balance at height %d is %s\n", repository.getBlockRepository().getBlockchainHeight(), Amounts.prettyAmount(atAccount.getConfirmedBalance(Asset.QORT)));

		// Fetch new AT balance
		long postMintBalance = atAccount.getConfirmedBalance(Asset.QORT);

		assertNotSame(preMintBalance, postMintBalance);


		// Payments might happen over multiple blocks!
		Map<String, Long> expectedBalances = new HashMap<>(initialDonorBalances);

		ATData atData;
		do {
			// Confirm AT has found payments
			extractLastTxTimestamp(repository, atAddress, rawLastTxnTimestamp);
			assertNotSame(ByteArray.wrap(creationTimestamp), ByteArray.copyOf(rawLastTxnTimestamp));

			// AT should have sent refunds
			BlockData blockData = repository.getBlockRepository().getLastBlock();
			Block block = new Block(repository, blockData);
			List<Transaction> transactions = block.getTransactions();

			assertNotSame(0, transactions.size());

			// Compute expected balances
			for (var transaction : transactions) {
				AtTransaction atTransaction = (AtTransaction) transaction;
				ATTransactionData atTransactionData = (ATTransactionData) atTransaction.getTransactionData();
				String recipient = atTransactionData.getRecipient();

				// Skip if this is a refund to AT deployer
				if (recipient.equals(aliceAddress))
					continue;

				String donorName = donors.entrySet()
								.stream()
										.filter(donor -> donor.getValue().getAddress().equals(recipient))
												.findFirst()
														.get()
																.getKey();
				System.out.printf("AT paid %s to %s\n", Amounts.prettyAmount(atTransactionData.getAmount()), donorName);

				expectedBalances.compute(atTransactionData.getRecipient(), (key, balance) -> balance - AccountUtils.fee);
			}

			// AT should have finished
			atData = repository.getATRepository().fromATAddress(atAddress);

			// Mint new block in case we need to loop round again
			BlockUtils.mintBlock(repository);
			System.out.printf("AT balance at height %d is %s\n", repository.getBlockRepository().getBlockchainHeight(), Amounts.prettyAmount(atAccount.getConfirmedBalance(Asset.QORT)));
		} while (!atData.getIsFinished());

		// Compare expected balances
		donors.entrySet()
				.forEach(donor -> {
					String donorName = donor.getKey();
					TestAccount donorAccount = donor.getValue();

					Long expectedBalance = expectedBalances.get(donorAccount.getAddress());
					Long actualBalance = null;
					try {
						actualBalance = donorAccount.getConfirmedBalance(Asset.QORT);
					} catch (DataException e) {
						fail(e.getMessage());
					}

					assertEquals(expectedBalance, actualBalance);
				});
	}


	private DeployAtTransaction doDeploy(Repository repository, PrivateKeyAccount deployer, byte[] creationBytes, long fundingAmount) throws DataException {
		long txTimestamp = System.currentTimeMillis();
		byte[] lastReference = deployer.getLastReference();

		if (lastReference == null) {
			System.err.println(String.format("Qortal account %s has no last reference", deployer.getAddress()));
			System.exit(2);
		}

		Long fee = null;
		String name = "Test AT";
		String description = "Test AT";
		String atType = "Test";
		String tags = "TEST";

		BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, deployer.getPublicKey(), fee, null);
		TransactionData deployAtTransactionData = new DeployAtTransactionData(baseTransactionData, name, description, atType, tags, creationBytes, fundingAmount, Asset.QORT);

		DeployAtTransaction deployAtTransaction = new DeployAtTransaction(repository, deployAtTransactionData);

		fee = deployAtTransaction.calcRecommendedFee();
		deployAtTransactionData.setFee(fee);

		TransactionUtils.signAndMint(repository, deployAtTransactionData, deployer);

		return deployAtTransaction;
	}

	private void extractLastTxTimestamp(Repository repository, String atAddress, byte[] rawLastTxnTimestamp) throws DataException {
		// Check AT result
		ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);
		byte[] stateData = atStateData.getStateData();

		byte[] dataBytes = MachineState.extractDataBytes(stateData);

		System.arraycopy(dataBytes, 5 * MachineState.VALUE_SIZE, rawLastTxnTimestamp, 0, rawLastTxnTimestamp.length);
	}

	private void assertTimestamp(Repository repository, String atAddress, Transaction transaction) throws DataException {
		int height = transaction.getHeight();
		byte[] transactionSignature = transaction.getTransactionData().getSignature();

		BlockData blockData = repository.getBlockRepository().fromHeight(height);
		assertNotNull(blockData);

		Block block = new Block(repository, blockData);

		List<Transaction> blockTransactions = block.getTransactions();
		int sequence;
		for (sequence = blockTransactions.size() - 1; sequence >= 0; --sequence)
			if (Arrays.equals(blockTransactions.get(sequence).getTransactionData().getSignature(), transactionSignature))
				break;

		assertNotSame(-1, sequence);

		byte[] rawLastTxTimestamp = new byte[8];
		extractLastTxTimestamp(repository, atAddress, rawLastTxTimestamp);

		Timestamp expectedTimestamp = new Timestamp(height, sequence);
		Timestamp actualTimestamp = new Timestamp(BitTwiddling.longFromBEBytes(rawLastTxTimestamp, 0));

		assertEquals(String.format("Expected height %d, seq %d but was height %d, seq %d",
						height, sequence,
						actualTimestamp.blockHeight, actualTimestamp.transactionSequence
				),
				expectedTimestamp.longValue(),
				actualTimestamp.longValue());

		byte[] expectedPartialSignature = new byte[24];
		System.arraycopy(transactionSignature, 8, expectedPartialSignature, 0, expectedPartialSignature.length);

		byte[] actualPartialSignature = new byte[24];
		System.arraycopy(rawLastTxTimestamp, 8, actualPartialSignature, 0, actualPartialSignature.length);

		assertArrayEquals(expectedPartialSignature, actualPartialSignature);
	}

	private static TestAccount generateTestAccount(Repository repository, String accountName) {
		byte[] seed = new byte[32];
		new SecureRandom().nextBytes(seed);
		return new TestAccount(repository, accountName, Base58.encode(seed), false);
	}

}
