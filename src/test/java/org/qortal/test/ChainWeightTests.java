package org.qortal.test;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.Common;
import org.qortal.test.common.TestAccount;
import org.qortal.transform.Transformer;
import org.qortal.transform.block.BlockTransformer;
import org.qortal.utils.NTP;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class ChainWeightTests extends Common {

	private static final Random RANDOM = new Random();
	private static final NumberFormat FORMATTER = new DecimalFormat("0.###E0");

	@BeforeClass
	public static void beforeClass() {
		// We need this so that NTP.getTime() in Block.calcChainWeight() doesn't return null, causing NPE
		NTP.setFixedOffset(0L);
	}

	@Before
	public void beforeTest() throws DataException {
		Common.useSettings("test-settings-v2-minting.json");
	}

	private static BlockSummaryData genBlockSummary(Repository repository, int height) {
		TestAccount testAccount = Common.getRandomTestAccount(repository, true);
		byte[] minterPublicKey = testAccount.getPublicKey();

		byte[] signature = new byte[BlockTransformer.BLOCK_SIGNATURE_LENGTH];
		RANDOM.nextBytes(signature);

		int onlineAccountsCount = RANDOM.nextInt(1000);

		return new BlockSummaryData(height, signature, minterPublicKey, onlineAccountsCount);
	}

	private static List<BlockSummaryData> genBlockSummaries(Repository repository, int count, BlockSummaryData commonBlockSummary) {
		List<BlockSummaryData> blockSummaries = new ArrayList<>();
		blockSummaries.add(commonBlockSummary);

		final int commonBlockHeight = commonBlockSummary.getHeight();

		for (int i = 1; i <= count; ++i)
			blockSummaries.add(genBlockSummary(repository, commonBlockHeight + i));

		return blockSummaries;
	}

	// Check that more online accounts beats a better key
	@Test
	public void testMoreAccountsBlock() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			final int parentHeight = 1;
			final byte[] parentMinterKey = new byte[Transformer.PUBLIC_KEY_LENGTH];

			int betterAccountsCount = 100;
			int worseAccountsCount = 20;

			TestAccount betterAccount = Common.getTestAccount(repository, "bob-reward-share");
			byte[] betterKey = betterAccount.getPublicKey();
			int betterMinterLevel = Account.getRewardShareEffectiveMintingLevel(repository, betterKey);

			TestAccount worseAccount = Common.getTestAccount(repository, "dilbert-reward-share");
			byte[] worseKey = worseAccount.getPublicKey();
			int worseMinterLevel = Account.getRewardShareEffectiveMintingLevel(repository, worseKey);

			// This is to check that the hard-coded keys ARE actually better/worse as expected, before moving on testing more online accounts
			BigInteger betterKeyDistance = Block.calcKeyDistance(parentHeight, parentMinterKey, betterKey, betterMinterLevel);
			BigInteger worseKeyDistance = Block.calcKeyDistance(parentHeight, parentMinterKey, worseKey, worseMinterLevel);
			assertEquals("hard-coded keys are wrong", 1, betterKeyDistance.compareTo(worseKeyDistance));

			BlockSummaryData betterBlockSummary = new BlockSummaryData(parentHeight + 1, null, worseKey, betterAccountsCount);
			BlockSummaryData worseBlockSummary = new BlockSummaryData(parentHeight + 1, null, betterKey, worseAccountsCount);

			populateBlockSummaryMinterLevel(repository, betterBlockSummary);
			populateBlockSummaryMinterLevel(repository, worseBlockSummary);

			BigInteger betterBlockWeight = Block.calcBlockWeight(parentHeight, parentMinterKey, betterBlockSummary);
			BigInteger worseBlockWeight = Block.calcBlockWeight(parentHeight, parentMinterKey, worseBlockSummary);

			assertEquals("block weights are wrong", 1, betterBlockWeight.compareTo(worseBlockWeight));
		}
	}

	// Demonstrates that typical key distance ranges from roughly 1E75 to 1E77
	@Test
	public void testKeyDistances() {
		byte[] parentMinterKey = new byte[Transformer.PUBLIC_KEY_LENGTH];
		byte[] testKey = new byte[Transformer.PUBLIC_KEY_LENGTH];

		for (int i = 0; i < 50; ++i) {
			int parentHeight = RANDOM.nextInt(50000);
			RANDOM.nextBytes(parentMinterKey);
			RANDOM.nextBytes(testKey);
			int minterLevel = RANDOM.nextInt(10) + 1;

			BigInteger keyDistance = Block.calcKeyDistance(parentHeight, parentMinterKey, testKey, minterLevel);

			System.out.println(String.format("Parent height: %d, minter level: %d, distance: %s",
					parentHeight,
					minterLevel,
					FORMATTER.format(keyDistance)));
		}
	}

	// If typical key distance ranges from 1E75 to 1E77
	// then we want lots of online accounts to push a 1E75 distance
	// towards 1E77 so that it competes with a 1E77 key that has hardly any online accounts
	// 1E75 is approx. 2**249 so maybe that's a good value for Block.ACCOUNTS_COUNT_SHIFT
	@Test
	public void testMoreAccountsVersusKeyDistance() throws DataException {
		BigInteger minimumBetterKeyDistance = BigInteger.TEN.pow(77);
		BigInteger maximumWorseKeyDistance = BigInteger.TEN.pow(75);

		try (final Repository repository = RepositoryManager.getRepository()) {
			final byte[] parentMinterKey = new byte[Transformer.PUBLIC_KEY_LENGTH];

			TestAccount betterAccount = Common.getTestAccount(repository, "bob-reward-share");
			byte[] betterKey = betterAccount.getPublicKey();
			int betterMinterLevel = Account.getRewardShareEffectiveMintingLevel(repository, betterKey);

			TestAccount worseAccount = Common.getTestAccount(repository, "dilbert-reward-share");
			byte[] worseKey = worseAccount.getPublicKey();
			int worseMinterLevel = Account.getRewardShareEffectiveMintingLevel(repository, worseKey);

			// This is to check that the hard-coded keys ARE actually better/worse as expected, before moving on testing more online accounts
			BigInteger betterKeyDistance;
			BigInteger worseKeyDistance;

			int parentHeight = 0;
			do {
				++parentHeight;
				betterKeyDistance = Block.calcKeyDistance(parentHeight, parentMinterKey, betterKey, betterMinterLevel);
				worseKeyDistance = Block.calcKeyDistance(parentHeight, parentMinterKey, worseKey, worseMinterLevel);
			} while (betterKeyDistance.compareTo(minimumBetterKeyDistance) < 0 || worseKeyDistance.compareTo(maximumWorseKeyDistance) > 0);

			System.out.println(String.format("Parent height: %d, better key distance: %s, worse key distance: %s",
					parentHeight,
					FORMATTER.format(betterKeyDistance),
					FORMATTER.format(worseKeyDistance)));

			for (int accountsCountShift = 244; accountsCountShift <= 256; accountsCountShift += 2) {
				for (int worseAccountsCount = 1; worseAccountsCount <= 101; worseAccountsCount += 25) {
					for (int betterAccountsCount = 1; betterAccountsCount <= 1001; betterAccountsCount += 250) {
						BlockSummaryData worseKeyBlockSummary = new BlockSummaryData(parentHeight + 1, null, worseKey, betterAccountsCount);
						BlockSummaryData betterKeyBlockSummary = new BlockSummaryData(parentHeight + 1, null, betterKey, worseAccountsCount);

						populateBlockSummaryMinterLevel(repository, worseKeyBlockSummary);
						populateBlockSummaryMinterLevel(repository, betterKeyBlockSummary);

						BigInteger worseKeyBlockWeight = calcBlockWeight(parentHeight, parentMinterKey, worseKeyBlockSummary, accountsCountShift);
						BigInteger betterKeyBlockWeight = calcBlockWeight(parentHeight, parentMinterKey, betterKeyBlockSummary, accountsCountShift);

						System.out.println(String.format("Shift: %d, worse key: %d accounts, %s diff; better key: %d accounts: %s diff; winner: %s",
								accountsCountShift,
								betterAccountsCount, // used with worseKey
								FORMATTER.format(worseKeyBlockWeight),
								worseAccountsCount, // used with betterKey
								FORMATTER.format(betterKeyBlockWeight),
								worseKeyBlockWeight.compareTo(betterKeyBlockWeight) > 0 ? "worse key/better accounts" : "better key/worse accounts"
								));
					}
				}

				System.out.println();
			}
		}
	}

	private static BigInteger calcBlockWeight(int parentHeight, byte[] parentBlockSignature, BlockSummaryData blockSummaryData, int accountsCountShift) {
		BigInteger keyDistance = Block.calcKeyDistance(parentHeight, parentBlockSignature, blockSummaryData.getMinterPublicKey(), blockSummaryData.getMinterLevel());
		return BigInteger.valueOf(blockSummaryData.getOnlineAccountsCount()).shiftLeft(accountsCountShift).add(keyDistance);
	}

	// Check that a longer chain has same weight as shorter/truncated chain
	@Test
	public void testLongerChain() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			final int commonBlockHeight = 1;
			BlockSummaryData commonBlockSummary = genBlockSummary(repository, commonBlockHeight);
			byte[] commonBlockGeneratorKey = commonBlockSummary.getMinterPublicKey();

			List<BlockSummaryData> longerChain = genBlockSummaries(repository, 6, commonBlockSummary);
			populateBlockSummariesMinterLevels(repository, longerChain);

			List<BlockSummaryData> shorterChain = longerChain.subList(0, longerChain.size() / 2);

			final int mutualHeight = commonBlockHeight - 1 + Math.min(shorterChain.size(), longerChain.size());

			BigInteger shorterChainWeight = Block.calcChainWeight(commonBlockHeight, commonBlockGeneratorKey, shorterChain, mutualHeight);
			BigInteger longerChainWeight = Block.calcChainWeight(commonBlockHeight, commonBlockGeneratorKey, longerChain, mutualHeight);

			if (NTP.getTime() >= BlockChain.getInstance().getCalcChainWeightTimestamp())
				assertEquals("longer chain should have same weight", 0, longerChainWeight.compareTo(shorterChainWeight));
			else
				assertEquals("longer chain should have greater weight", 1, longerChainWeight.compareTo(shorterChainWeight));
		}
	}

	// Check that a higher level account wins more blocks
	@Test
	public void testMinterLevel() throws DataException {
		testMinterLevels("chloe-reward-share", "bob-reward-share");
	}

	private void testMinterLevels(String betterMinterName, String worseMinterName) throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			TestAccount betterAccount = Common.getTestAccount(repository, betterMinterName);
			byte[] betterKey = betterAccount.getPublicKey();
			int betterMinterLevel = Account.getRewardShareEffectiveMintingLevel(repository, betterKey);

			TestAccount worseAccount = Common.getTestAccount(repository, worseMinterName);
			byte[] worseKey = worseAccount.getPublicKey();
			int worseMinterLevel = Account.getRewardShareEffectiveMintingLevel(repository, worseKey);

			// Check hard-coded accounts have expected better/worse levels
			assertTrue("hard-coded accounts have wrong relative minting levels", betterMinterLevel > worseMinterLevel);

			Random random = new Random();
			final int onlineAccountsCount = 100;
			int betterAccountWins = 0;
			int worseAccountWins = 0;
			byte[] parentSignature = new byte[64];
			random.nextBytes(parentSignature);

			for (int parentHeight = 1; parentHeight < 1000; ++parentHeight) {
				byte[] blockSignature = new byte[64];
				random.nextBytes(blockSignature);

				BlockSummaryData betterBlockSummary = new BlockSummaryData(parentHeight + 1, blockSignature, worseKey, onlineAccountsCount);
				BlockSummaryData worseBlockSummary = new BlockSummaryData(parentHeight + 1, blockSignature, betterKey, onlineAccountsCount);

				populateBlockSummaryMinterLevel(repository, betterBlockSummary);
				populateBlockSummaryMinterLevel(repository, worseBlockSummary);

				BigInteger betterBlockWeight = Block.calcBlockWeight(parentHeight, parentSignature, betterBlockSummary);
				BigInteger worseBlockWeight = Block.calcBlockWeight(parentHeight, parentSignature, worseBlockSummary);

				if (betterBlockWeight.compareTo(worseBlockWeight) >= 0)
					++betterAccountWins;
				else
					++worseAccountWins;

				parentSignature = blockSignature;
			}

			assertTrue("Account with better minting level didn't win more blocks", betterAccountWins > worseAccountWins);
		}
	}

	// Check that a higher level account wins more blocks
	@Test
	public void testFounderMinterLevel() throws DataException {
		testMinterLevels("alice-reward-share", "dilbert-reward-share");
	}

	private void populateBlockSummariesMinterLevels(Repository repository, List<BlockSummaryData> blockSummaries) throws DataException {
		for (int i = 0; i < blockSummaries.size(); ++i) {
			BlockSummaryData blockSummary = blockSummaries.get(i);

			populateBlockSummaryMinterLevel(repository, blockSummary);
		}
	}

	private void populateBlockSummaryMinterLevel(Repository repository, BlockSummaryData blockSummary) throws DataException {
		int minterLevel = Account.getRewardShareEffectiveMintingLevel(repository, blockSummary.getMinterPublicKey());
		assertNotSame("effective minter level should not be zero", 0, minterLevel);

		blockSummary.setMinterLevel(minterLevel);
	}

}
