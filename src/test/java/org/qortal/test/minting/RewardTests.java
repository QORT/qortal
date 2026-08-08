package org.qortal.test.minting;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.block.BlockChain.RewardByHeight;
import org.qortal.controller.BlockMinter;
import org.qortal.data.account.AccountBalanceData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AccountUtils;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TestAccount;
import org.qortal.utils.Amounts;
import org.qortal.utils.Base58;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RewardTests extends Common {
	private static final Logger LOGGER = LogManager.getLogger(RewardTests.class);
	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testSimpleReward() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT);

			Long blockReward = BlockUtils.getNextBlockReward(repository);

			BlockUtils.mintBlock(repository);

			long expectedBalance = initialBalances.get("alice").get(Asset.QORT) + blockReward;
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, expectedBalance);
		}
	}

	@Test
	public void testRewards() throws DataException {
		List<RewardByHeight> rewardsByHeight = BlockChain.getInstance().getBlockRewardsByHeight();

		try (final Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT);

			int rewardIndex = rewardsByHeight.size() - 1;

			RewardByHeight rewardInfo = rewardsByHeight.get(rewardIndex);
			Long expectedBalance = initialBalances.get("alice").get(Asset.QORT);

			for (int height = rewardInfo.height; height > 1; --height) {
				if (height < rewardInfo.height) {
					--rewardIndex;
					rewardInfo = rewardsByHeight.get(rewardIndex);
				}

				BlockUtils.mintBlock(repository);

				expectedBalance += rewardInfo.reward;
			}

			AccountUtils.assertBalance(repository, "alice", Asset.QORT, expectedBalance);
		}
	}

	@Test
	public void testRewardSharing() throws DataException {
		final int share = 12_80; // 12.80%

		try (final Repository repository = RepositoryManager.getRepository()) {
			byte[] rewardSharePrivateKey = AccountUtils.rewardShare(repository, "alice", "bob", share);
			PrivateKeyAccount rewardShareAccount = new PrivateKeyAccount(repository, rewardSharePrivateKey);

			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT);
			Long blockReward = BlockUtils.getNextBlockReward(repository);

			BlockMinter.mintTestingBlock(repository, rewardShareAccount);

			// We're expecting reward * 12.8% to Bob, the rest to Alice

			long bobShare = (blockReward * share) / 100L / 100L;
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, initialBalances.get("bob").get(Asset.QORT) + bobShare);

			long aliceShare = blockReward - bobShare;
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, initialBalances.get("alice").get(Asset.QORT) + aliceShare);
		}
	}


	@Test
	public void testLegacyQoraReward() throws DataException {
		Common.useSettings("test-settings-v2-qora-holder-extremes.json");

		long qoraHoldersShare = BlockChain.getInstance().getQoraHoldersShareAtHeight(1);
		BigInteger qoraHoldersShareBI = BigInteger.valueOf(qoraHoldersShare);

		long qoraPerQort = BlockChain.getInstance().getQoraPerQortReward();
		BigInteger qoraPerQortBI = BigInteger.valueOf(qoraPerQort);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);

			Long blockReward = BlockUtils.getNextBlockReward(repository);
			BigInteger blockRewardBI = BigInteger.valueOf(blockReward);

			// Fetch all legacy QORA holder balances
			List<AccountBalanceData> qoraHolders = repository.getAccountRepository().getAssetBalances(Asset.LEGACY_QORA, true);
			long totalQoraHeld = 0L;
			for (AccountBalanceData accountBalanceData : qoraHolders)
				totalQoraHeld += accountBalanceData.getBalance();
			BigInteger totalQoraHeldBI = BigInteger.valueOf(totalQoraHeld);

			BlockUtils.mintBlock(repository);

			/*
			 * Example:
			 *
			 * Block reward is 100 QORT, QORA-holders' share is 0.20 (20%) = 20 QORT
			 *
			 * We hold 100 QORA
			 * Someone else holds 28 QORA
			 * Total QORA held: 128 QORA
			 *
			 * Our portion of that is 100 QORA / 128 QORA * 20 QORT = 15.625 QORT
			 *
			 * QORA holders earn at most 1 QORT per 250 QORA held.
			 *
			 * So we can earn at most 100 QORA / 250 QORAperQORT = 0.4 QORT
			 *
			 * Thus our block earning should be capped to 0.4 QORT.
			 */

			// Expected reward
			long qoraHoldersReward = blockRewardBI.multiply(qoraHoldersShareBI).divide(Amounts.MULTIPLIER_BI).longValue();
			assertTrue("QORA-holders share of block reward should be less than total block reward", qoraHoldersReward < blockReward);
			assertFalse("QORA-holders share of block reward should not be negative!", qoraHoldersReward < 0);
			BigInteger qoraHoldersRewardBI = BigInteger.valueOf(qoraHoldersReward);

			long ourQoraHeld = initialBalances.get("chloe").get(Asset.LEGACY_QORA);
			BigInteger ourQoraHeldBI = BigInteger.valueOf(ourQoraHeld);
			long ourQoraReward = qoraHoldersRewardBI.multiply(ourQoraHeldBI).divide(totalQoraHeldBI).longValue();
			assertTrue("Our QORA-related reward should be less than total QORA-holders share of block reward", ourQoraReward < qoraHoldersReward);
			assertFalse("Our QORA-related reward should not be negative!", ourQoraReward < 0);

			long ourQortFromQoraCap = Amounts.scaledDivide(ourQoraHeldBI, qoraPerQortBI);
			assertTrue("Our QORT-from-QORA cap should be greater than zero", ourQortFromQoraCap > 0);

			long expectedReward = Math.min(ourQoraReward, ourQortFromQoraCap);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, initialBalances.get("chloe").get(Asset.QORT) + expectedReward);

			AccountUtils.assertBalance(repository, "chloe", Asset.QORT_FROM_QORA, initialBalances.get("chloe").get(Asset.QORT_FROM_QORA) + expectedReward);
		}
	}

	@Test
	public void testMaxLegacyQoraReward() throws DataException {
		Common.useSettings("test-settings-v2-qora-holder.json");

		long qoraPerQort = BlockChain.getInstance().getQoraPerQortReward();

		try (final Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);

			// Mint lots of blocks
			for (int i = 0; i < 100; ++i)
				BlockUtils.mintBlock(repository);

			// Expected balances to be limited by Dilbert's legacy QORA amount
			long expectedBalance = Amounts.scaledDivide(initialBalances.get("dilbert").get(Asset.LEGACY_QORA), qoraPerQort);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, initialBalances.get("dilbert").get(Asset.QORT) + expectedBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT_FROM_QORA, initialBalances.get("dilbert").get(Asset.QORT_FROM_QORA) + expectedBalance);
		}
	}

	@Test
	public void testLegacyQoraRewardReduction() throws DataException {
		Common.useSettings("test-settings-v2-qora-holder-reduction.json");

		// Make sure that the QORA share reduces between blocks 4 and 5
		assertTrue(BlockChain.getInstance().getQoraHoldersShareAtHeight(5) < BlockChain.getInstance().getQoraHoldersShareAtHeight(4));

		// Keep track of balance deltas at each height
		Map<Integer, Long> chloeQortBalanceDeltaAtEachHeight = new HashMap<>();

		try (final Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			long chloeLastQortBalance = initialBalances.get("chloe").get(Asset.QORT);

			for (int i=2; i<=10; i++) {

				Block block = BlockUtils.mintBlock(repository);

				// Add to map of balance deltas at each height
				long chloeNewQortBalance = AccountUtils.getBalance(repository, "chloe", Asset.QORT);
				chloeQortBalanceDeltaAtEachHeight.put(block.getBlockData().getHeight(), chloeNewQortBalance - chloeLastQortBalance);
				chloeLastQortBalance = chloeNewQortBalance;
			}

			// Ensure blocks 2-4 paid out the same rewards to Chloe
			assertEquals(chloeQortBalanceDeltaAtEachHeight.get(2), chloeQortBalanceDeltaAtEachHeight.get(4));

			// Ensure block 5 paid a lower reward
			assertTrue(chloeQortBalanceDeltaAtEachHeight.get(5) < chloeQortBalanceDeltaAtEachHeight.get(4));

			// Check that the reward was 20x lower
			assertTrue(chloeQortBalanceDeltaAtEachHeight.get(5) == chloeQortBalanceDeltaAtEachHeight.get(4) / 20);

			// Orphan to block 4 and ensure that Chloe's balance hasn't been incorrectly affected by the reward reduction
			BlockUtils.orphanToBlock(repository, 4);
			long expectedChloeQortBalance = initialBalances.get("chloe").get(Asset.QORT) + chloeQortBalanceDeltaAtEachHeight.get(2) +
					chloeQortBalanceDeltaAtEachHeight.get(3) + chloeQortBalanceDeltaAtEachHeight.get(4);
			assertEquals(expectedChloeQortBalance, AccountUtils.getBalance(repository, "chloe", Asset.QORT));
		}
	}

	/** Use Alice-Chloe reward-share to bump Chloe from level 0 to level 1, then check orphaning works as expected. */
	@Test
	public void testLevel1() throws DataException {
		List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();

		try (final Repository repository = RepositoryManager.getRepository()) {
			TestAccount chloe = Common.getTestAccount(repository, "chloe");

			assertEquals(0, (int) chloe.getLevel());

			// Alice needs to mint block containing REWARD_SHARE BEFORE Alice loses minting privs
			byte[] aliceChloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "alice", "chloe", 0); // Block minted by Alice
			PrivateKeyAccount aliceChloeRewardShareAccount = new PrivateKeyAccount(repository, aliceChloeRewardSharePrivateKey);

			final int minterBlocksNeeded = cumulativeBlocksByLevel.get(1);
			// Mint enough blocks to bump testAccount level
			for (int bc = 0; bc < minterBlocksNeeded; ++bc)
				BlockMinter.mintTestingBlock(repository, aliceChloeRewardShareAccount);

			assertEquals(1, (int) chloe.getLevel());

			// Orphan back to genesis block
			BlockUtils.orphanToBlock(repository, 1);

			assertEquals(0, (int) chloe.getLevel());
		}
	}

	/** Test rewards to founders, one in reward-share, the other is self-share. */
	@Test
	public void testFounderRewards() throws DataException {
		Common.useSettings("test-settings-v2-founder-rewards.json");

		try (final Repository repository = RepositoryManager.getRepository()) {
			Long blockReward = BlockUtils.getNextBlockReward(repository);

			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice to mint, therefore online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);

			// Bob self-share NOT online

			// Chloe self-share and reward-share with Dilbert both online
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");
			mintingAndOnlineAccounts.add(chloeSelfShare);

			PrivateKeyAccount chloeDilbertRewardShare = new PrivateKeyAccount(repository, Base58.decode("HuiyqLipUN1V9p1HZfLhyEwmEA6BTaT2qEfjgkwPViV4"));
			mintingAndOnlineAccounts.add(chloeDilbertRewardShare);

			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// 2 founders online so blockReward divided by 2
			int founderCount = 2;
			long perFounderReward = blockReward / founderCount;

			// Alice simple self-share so her reward is perFounderReward
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, perFounderReward);

			// Bob not online so his reward is zero
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, 0L);

			// Chloe has two reward-shares, so her reward is divided by 2
			int chloeSharesCount = 2;
			long chloePerShareReward = perFounderReward / chloeSharesCount;

			// Her self-share gets chloePerShareReward
			long chloeExpectedBalance = chloePerShareReward;

			// Her reward-share with Dilbert: 25% goes to Dilbert
			int dilbertSharePercent = 25;
			long dilbertExpectedBalance = (chloePerShareReward * dilbertSharePercent) / 100L;

			// The remaining 75% goes to Chloe
			long rewardShareRemaining = chloePerShareReward - dilbertExpectedBalance;
			chloeExpectedBalance += rewardShareRemaining;
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeExpectedBalance);
		}
	}

	/** Check account-level-based reward scaling when no founders are online. */
	@Test
	public void testNoFounderRewardScaling() throws DataException {
		Common.useSettings("test-settings-v2-reward-scaling.json");

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Dilbert needs to create a self-share
			byte[] dilbertSelfSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0); // Block minted by Alice
			PrivateKeyAccount dilbertSelfShareAccount = new PrivateKeyAccount(repository, dilbertSelfSharePrivateKey);

			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);

			long blockReward = BlockUtils.getNextBlockReward(repository);

			BlockMinter.mintTestingBlock(repository, dilbertSelfShareAccount);

			/*
			 * Dilbert is only account 'online'.
			 * No founders online.
			 * Some legacy QORA holders.
			 *
			 * So Dilbert should receive 100% - legacy QORA holder's share.
			 */

			final long qoraHoldersShare = BlockChain.getInstance().getQoraHoldersShareAtHeight(1);
			final long remainingShare = 1_00000000 - qoraHoldersShare;

			long dilbertExpectedBalance = initialBalances.get("dilbert").get(Asset.QORT);
			dilbertExpectedBalance += Amounts.roundDownScaledMultiply(blockReward, remainingShare);

			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertExpectedBalance);

			// After several blocks, the legacy QORA holder should be maxxed out
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			// Now Dilbert should be receiving 100% of block reward
			blockReward = BlockUtils.getNextBlockReward(repository);

			BlockMinter.mintTestingBlock(repository, dilbertSelfShareAccount);

			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertExpectedBalance + blockReward);
		}
	}

	/** Check leftover legacy QORA reward goes to online founders. */
	@Test
	public void testLeftoverReward() throws DataException {
		Common.useSettings("test-settings-v2-leftover-reward.json");

		try (final Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);

			long blockReward = BlockUtils.getNextBlockReward(repository);

			BlockUtils.mintBlock(repository); // Block minted by Alice self-share

			// Chloe maxxes out her legacy QORA reward so some is leftover to reward to Alice.

			TestAccount chloe = Common.getTestAccount(repository, "chloe");
			final long chloeQortFromQora = chloe.getConfirmedBalance(Asset.QORT_FROM_QORA);

			long expectedBalance = initialBalances.get("alice").get(Asset.QORT) + blockReward - chloeQortFromQora;
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, expectedBalance);
		}
	}

	/** Test rewards for level 1 and 2 accounts both pre and post the shareBinFix, including orphaning back through the feature trigger block */
	@Test
	public void testLevel1And2Rewards() throws DataException {
		Common.useSettings("test-settings-v2-reward-levels.json");

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);
			byte[] chloeRewardSharePrivateKey;
			// Bob self-share NOT online

			// Chloe self share online
			try {
				chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			} catch (IllegalArgumentException ex) {
				LOGGER.error("FAILED {}", ex.getLocalizedMessage(), ex);
				throw ex;
			}
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint a couple of blocks so that we are able to orphan them later
			for (int i=0; i<2; i++)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that the levels are as we expect
			assertEquals(1, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(2, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Ensure that only Alice is a founder
			assertEquals(1, getFlags(repository, "alice"));
			assertEquals(0, getFlags(repository, "bob"));
			assertEquals(0, getFlags(repository, "chloe"));
			assertEquals(0, getFlags(repository, "dilbert"));

			// Now that everyone is at level 1 or 2, we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			final long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are at the correct height and block reward value
			assertEquals(6, (int) repository.getBlockRepository().getLastBlock().getHeight());
			assertEquals(10000000000L, blockReward);

			/*
			 * Alice, Chloe, and Dilbert are 'online'. Bob is offline.
			 * Chloe is level 1, Dilbert is level 2.
			 * One founder online (Alice, who is also level 1).
			 * No legacy QORA holders.
			 *
			 * Chloe and Dilbert should receive equal shares of the 5% block reward for Level 1 and 2
			 * Alice should receive the remainder (95%)
			 */

			// We are after the shareBinFix feature trigger, so we expect level 1 and 2 to share the same reward (5%)
			final int level1And2SharePercent = 5_00; // 5%
			final long level1And2ShareAmount = (blockReward * level1And2SharePercent) / 100L / 100L;
			final long expectedReward = level1And2ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderReward = blockReward - level1And2ShareAmount; // Alice should receive the remainder

			// Validate the balances to ensure that the correct post-shareBinFix distribution is being applied
			assertEquals(500000000, level1And2ShareAmount);
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderReward);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance+expectedReward);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedReward);

			// Now orphan the latest block. This brings us to the threshold of the shareBinFix feature trigger.
			BlockUtils.orphanBlocks(repository, 1);
			assertEquals(5, (int) repository.getBlockRepository().getLastBlock().getHeight());

			// Ensure the latest post-fix block rewards have been subtracted and they have returned to their initial values
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance);

			// Orphan another block. This time, the block that was orphaned was prior to the shareBinFix feature trigger.
			BlockUtils.orphanBlocks(repository, 1);
			assertEquals(4, (int) repository.getBlockRepository().getLastBlock().getHeight());

			// Prior to the fix, the levels were incorrectly grouped
			// Chloe should receive 100% of the level 1 reward, and Dilbert should receive 100% of the level 2+3 reward
			final int level1SharePercent = 5_00; // 5%
			final int level2And3SharePercent = 10_00; // 10%
			final long level1ShareAmountBeforeFix = (blockReward * level1SharePercent) / 100L / 100L;
			final long level2And3ShareAmountBeforeFix = (blockReward * level2And3SharePercent) / 100L / 100L;
			final long expectedFounderRewardBeforeFix = blockReward - level1ShareAmountBeforeFix - level2And3ShareAmountBeforeFix; // Alice should receive the remainder

			// Validate the share amounts and balances
			assertEquals(500000000, level1ShareAmountBeforeFix);
			assertEquals(1000000000, level2And3ShareAmountBeforeFix);
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance-expectedFounderRewardBeforeFix);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance-level1ShareAmountBeforeFix);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance-level2And3ShareAmountBeforeFix);

			// Orphan the latest block one last time
			BlockUtils.orphanBlocks(repository, 1);
			assertEquals(3, (int) repository.getBlockRepository().getLastBlock().getHeight());

			// Validate balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance-(expectedFounderRewardBeforeFix*2));
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance-(level1ShareAmountBeforeFix*2));
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance-(level2And3ShareAmountBeforeFix*2));

		}
	}

	/** Test rewards for level 3 and 4 accounts */
	@Test
	public void testLevel3And4Rewards() throws DataException {
		Common.useSettings("test-settings-v2-reward-levels.json");

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);

			// Bob self-share online
			byte[] bobRewardSharePrivateKey = AccountUtils.rewardShare(repository, "bob", "bob", 0);
			PrivateKeyAccount bobRewardShareAccount = new PrivateKeyAccount(repository, bobRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(bobRewardShareAccount);

			// Chloe self share online
			byte[] chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint enough blocks to bump testAccount levels to 3 and 4
			final int minterBlocksNeeded = cumulativeBlocksByLevel.get(4) - 20; // 20 blocks before level 4, so that the test accounts reach the correct levels
			for (int bc = 0; bc < minterBlocksNeeded; ++bc)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that the levels are as we expect
			assertEquals(3, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(3, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(3, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(4, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Now that everyone is at level 3 or 4, we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			final long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are using the correct block reward value
			assertEquals(100000000L, blockReward);

			/*
			 * Alice, Bob, Chloe, and Dilbert are 'online'.
			 * Bob and Chloe are level 3; Dilbert is level 4.
			 * One founder online (Alice, who is also level 3).
			 * No legacy QORA holders.
			 *
			 * Chloe, Bob and Dilbert should receive equal shares of the 10% block reward for level 3 and 4
			 * Alice should receive the remainder (90%)
			 */

			// We are after the shareBinFix feature trigger, so we expect level 3 and 4 to share the same reward (10%)
			final int level3And4SharePercent = 10_00; // 10%
			final long level3And4ShareAmount = (blockReward * level3And4SharePercent) / 100L / 100L;
			final long expectedReward = level3And4ShareAmount / 3; // The reward is split between Bob, Chloe, and Dilbert
			final long expectedFounderReward = blockReward - level3And4ShareAmount; // Alice should receive the remainder

			// Validate the balances to ensure that the correct post-shareBinFix distribution is being applied
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderReward);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance+expectedReward);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance+expectedReward);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedReward);

		}
	}

	/** Test rewards for level 5 and 6 accounts */
	@Test
	public void testLevel5And6Rewards() throws DataException {
		Common.useSettings("test-settings-v2-reward-levels.json");

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);

			// Bob self-share not initially online

			// Chloe self share online
			byte[] chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint enough blocks to bump testAccount levels to 5 and 6
			final int minterBlocksNeeded = cumulativeBlocksByLevel.get(6) - 20; // 20 blocks before level 6, so that the test accounts reach the correct levels
			for (int bc = 0; bc < minterBlocksNeeded; ++bc)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Bob self-share now comes online
			byte[] bobRewardSharePrivateKey = AccountUtils.rewardShare(repository, "bob", "bob", 0);
			PrivateKeyAccount bobRewardShareAccount = new PrivateKeyAccount(repository, bobRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(bobRewardShareAccount);

			// Ensure that the levels are as we expect
			assertEquals(5, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(5, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(6, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Now that everyone is at level 5 or 6 (except Bob who has only just started minting, so is at level 1), we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			final long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are using the correct block reward value
			assertEquals(100000000L, blockReward);

			/*
			 * Alice, Bob, Chloe, and Dilbert are 'online'.
			 * Bob is level 1; Chloe is level 5; Dilbert is level 6.
			 * One founder online (Alice, who is also level 5).
			 * No legacy QORA holders.
			 *
			 * Chloe and Dilbert should receive equal shares of the 15% block reward for level 5 and 6
			 * Bob should receive all of the level 1 and 2 reward (5%)
			 * Alice should receive the remainder (80%)
			 */

			// We are after the shareBinFix feature trigger, so we expect level 5 and 6 to share the same reward (15%)
			final int level1And2SharePercent = 5_00; // 5%
			final int level5And6SharePercent = 15_00; // 10%
			final long level1And2ShareAmount = (blockReward * level1And2SharePercent) / 100L / 100L;
			final long level5And6ShareAmount = (blockReward * level5And6SharePercent) / 100L / 100L;
			final long expectedLevel1And2Reward = level1And2ShareAmount; // The reward is given entirely to Bob
			final long expectedLevel5And6Reward = level5And6ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderReward = blockReward - level1And2ShareAmount - level5And6ShareAmount; // Alice should receive the remainder

			// Validate the balances to ensure that the correct post-shareBinFix distribution is being applied
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderReward);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance+expectedLevel1And2Reward);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance+expectedLevel5And6Reward);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedLevel5And6Reward);

		}
	}

	/** Test rewards for level 7 and 8 accounts */
	@Test
	public void testLevel7And8Rewards() throws DataException {
		Common.useSettings("test-settings-v2-reward-levels.json");

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);

			// Bob self-share NOT online

			// Chloe self share online
			byte[] chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint enough blocks to bump testAccount levels to 7 and 8
			final int minterBlocksNeeded = cumulativeBlocksByLevel.get(8) - 20; // 20 blocks before level 8, so that the test accounts reach the correct levels
			for (int bc = 0; bc < minterBlocksNeeded; ++bc)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that the levels are as we expect
			assertEquals(7, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(7, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(8, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Now that everyone is at level 7 or 8 (except Bob who has only just started minting, so is at level 1), we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			final long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are using the correct block reward value
			assertEquals(100000000L, blockReward);

			/*
			 * Alice, Chloe, and Dilbert are 'online'.
			 * Chloe is level 7; Dilbert is level 8.
			 * One founder online (Alice, who is also level 7).
			 * No legacy QORA holders.
			 *
			 * Chloe and Dilbert should receive equal shares of the 20% block reward for level 7 and 8
			 * Alice should receive the remainder (80%)
			 */

			// We are after the shareBinFix feature trigger, so we expect level 7 and 8 to share the same reward (20%)
			final int level7And8SharePercent = 20_00; // 20%
			final long level7And8ShareAmount = (blockReward * level7And8SharePercent) / 100L / 100L;
			final long expectedLevel7And8Reward = level7And8ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderReward = blockReward - level7And8ShareAmount; // Alice should receive the remainder

			// Validate the balances to ensure that the correct post-shareBinFix distribution is being applied
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderReward);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance+expectedLevel7And8Reward);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedLevel7And8Reward);

			// Orphan and ensure balances return to their previous values
			BlockUtils.orphanBlocks(repository, 1);

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance);

		}
	}

	/** Test rewards for level 9 and 10 accounts */
	@Test
	public void testLevel9And10Rewards() throws DataException {
		Common.useSettings("test-settings-v2-reward-levels.json");

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);

			// Bob self-share not initially online

			// Chloe self share online
			byte[] chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint enough blocks to bump testAccount levels to 9 and 10
			final int minterBlocksNeeded = cumulativeBlocksByLevel.get(10) - 20; // 20 blocks before level 10, so that the test accounts reach the correct levels
			for (int bc = 0; bc < minterBlocksNeeded; ++bc)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Bob self-share now comes online
			byte[] bobRewardSharePrivateKey = AccountUtils.rewardShare(repository, "bob", "bob", 0);
			PrivateKeyAccount bobRewardShareAccount = new PrivateKeyAccount(repository, bobRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(bobRewardShareAccount);

			// Ensure that the levels are as we expect
			assertEquals(9, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(9, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(10, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Now that everyone is at level 7 or 8 (except Bob who has only just started minting, so is at level 1), we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			final long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are using the correct block reward value
			assertEquals(100000000L, blockReward);

			/*
			 * Alice, Bob, Chloe, and Dilbert are 'online'.
			 * Bob is level 1; Chloe is level 9; Dilbert is level 10.
			 * One founder online (Alice, who is also level 9).
			 * No legacy QORA holders.
			 *
			 * Chloe and Dilbert should receive equal shares of the 25% block reward for level 9 and 10
			 * Bob should receive all of the level 1 and 2 reward (5%)
			 * Alice should receive the remainder (70%)
			 */

			// We are after the shareBinFix feature trigger, so we expect level 9 and 10 to share the same reward (25%)
			final int level1And2SharePercent = 5_00; // 5%
			final int level9And10SharePercent = 25_00; // 25%
			final long level1And2ShareAmount = (blockReward * level1And2SharePercent) / 100L / 100L;
			final long level9And10ShareAmount = (blockReward * level9And10SharePercent) / 100L / 100L;
			final long expectedLevel1And2Reward = level1And2ShareAmount; // The reward is given entirely to Bob
			final long expectedLevel9And10Reward = level9And10ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderReward = blockReward - level1And2ShareAmount - level9And10ShareAmount; // Alice should receive the remainder

			// Validate the balances to ensure that the correct post-shareBinFix distribution is being applied
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderReward);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance+expectedLevel1And2Reward);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance+expectedLevel9And10Reward);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedLevel9And10Reward);

			// Orphan and ensure balances return to their previous values
			BlockUtils.orphanBlocks(repository, 1);

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance);

		}
	}

	/** Test rewards for level 7 and 8 accounts, when the tier doesn't yet have enough minters in it */
	@Test
	public void testLevel7And8RewardsPreActivation() throws DataException, IllegalAccessException {
		Common.useSettings("test-settings-v2-reward-levels.json");

		// Set minAccountsToActivateShareBin to 3 so that share bins 7-8 and 9-10 are considered inactive
		FieldUtils.writeField(BlockChain.getInstance(), "minAccountsToActivateShareBin", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);

			// Bob self-share NOT online

			// Chloe self share online
			byte[] chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint enough blocks to bump testAccount levels to 7 and 8
			final int minterBlocksNeeded = cumulativeBlocksByLevel.get(8) - 20; // 20 blocks before level 8, so that the test accounts reach the correct levels
			for (int bc = 0; bc < minterBlocksNeeded; ++bc)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that the levels are as we expect
			assertEquals(7, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(7, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(8, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Now that everyone is at level 7 or 8 (except Bob who has only just started minting, so is at level 1), we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			final long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are using the correct block reward value
			assertEquals(100000000L, blockReward);

			/*
			 * Alice, Chloe, and Dilbert are 'online'.
			 * Chloe is level 7; Dilbert is level 8.
			 * One founder online (Alice, who is also level 7).
			 * No legacy QORA holders.
			 *
			 * Level 7 and 8 is not yet activated, so its rewards are added to the level 5 and 6 share bin.
			 * There are no level 5 and 6 online.
			 * Chloe and Dilbert should receive equal shares of the 35% block reward for levels 5 to 8.
			 * Alice should receive the remainder (65%).
			 */

			final int level5To8SharePercent = 35_00; // 35% (combined 15% and 20%)
			final long level5To8ShareAmount = (blockReward * level5To8SharePercent) / 100L / 100L;
			final long expectedLevel5To8Reward = level5To8ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderReward = blockReward - level5To8ShareAmount; // Alice should receive the remainder

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderReward);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance+expectedLevel5To8Reward);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedLevel5To8Reward);

			// Orphan and ensure balances return to their previous values
			BlockUtils.orphanBlocks(repository, 1);

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance);

		}
	}

	/** Test rewards for level 9 and 10 accounts, when the tier doesn't yet have enough minters in it.
	 * Tier 7-8 isn't activated either, so the rewards and minters are all moved to tier 5-6. */
	@Test
	public void testLevel9And10RewardsPreActivation() throws DataException, IllegalAccessException {
		Common.useSettings("test-settings-v2-reward-levels.json");

		// Set minAccountsToActivateShareBin to 3 so that share bins 7-8 and 9-10 are considered inactive
		FieldUtils.writeField(BlockChain.getInstance(), "minAccountsToActivateShareBin", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);

			// Bob self-share not initially online

			// Chloe self share online
			byte[] chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint enough blocks to bump testAccount levels to 9 and 10
			final int minterBlocksNeeded = cumulativeBlocksByLevel.get(10) - 20; // 20 blocks before level 10, so that the test accounts reach the correct levels
			for (int bc = 0; bc < minterBlocksNeeded; ++bc)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Bob self-share now comes online
			byte[] bobRewardSharePrivateKey = AccountUtils.rewardShare(repository, "bob", "bob", 0);
			PrivateKeyAccount bobRewardShareAccount = new PrivateKeyAccount(repository, bobRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(bobRewardShareAccount);

			// Ensure that the levels are as we expect
			assertEquals(9, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(9, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(10, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Now that everyone is at level 7 or 8 (except Bob who has only just started minting, so is at level 1), we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			final long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are using the correct block reward value
			assertEquals(100000000L, blockReward);

			/*
			 * Alice, Bob, Chloe, and Dilbert are 'online'.
			 * Bob is level 1; Chloe is level 9; Dilbert is level 10.
			 * One founder online (Alice, who is also level 9).
			 * No legacy QORA holders.
			 *
			 * Levels 7+8, and 9+10 are not yet activated, so their rewards are added to the level 5 and 6 share bin.
			 * There are no levels 5-8 online.
			 * Chloe and Dilbert should receive equal shares of the 60% block reward for levels 5 to 10.
			 * Alice should receive the remainder (40%).
			 */

			final int level1And2SharePercent = 5_00; // 5%
			final int level5To10SharePercent = 60_00; // 60% (combined 15%, 20%, and 25%)
			final long level1And2ShareAmount = (blockReward * level1And2SharePercent) / 100L / 100L;
			final long level5To10ShareAmount = (blockReward * level5To10SharePercent) / 100L / 100L;
			final long expectedLevel1And2Reward = level1And2ShareAmount; // The reward is given entirely to Bob
			final long expectedLevel5To10Reward = level5To10ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderReward = blockReward - level1And2ShareAmount - level5To10ShareAmount; // Alice should receive the remainder

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderReward);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance+expectedLevel1And2Reward);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance+expectedLevel5To10Reward);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedLevel5To10Reward);

			// Orphan and ensure balances return to their previous values
			BlockUtils.orphanBlocks(repository, 1);

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance);

		}
	}

	/** Test rewards for level 7 and 8 accounts, when the tier reaches the minimum number of accounts */
	@Test
	public void testLevel7And8RewardsPreAndPostActivation() throws DataException, IllegalAccessException {
		Common.useSettings("test-settings-v2-reward-levels.json");

		// Set minAccountsToActivateShareBin to 2 so that share bins 7-8 and 9-10 are considered inactive at first
		FieldUtils.writeField(BlockChain.getInstance(), "minAccountsToActivateShareBin", 2, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);

			// Bob self-share NOT online

			// Chloe self share online
			byte[] chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint enough blocks to bump two of the testAccount levels to 7
			final int minterBlocksNeeded = cumulativeBlocksByLevel.get(7) - 12; // 12 blocks before level 7, so that dilbert and alice have reached level 7, but chloe will reach it in the next 2 blocks
			for (int bc = 0; bc < minterBlocksNeeded; ++bc)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that the levels are as we expect
			assertEquals(7, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(6, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(7, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Now that dilbert has reached level 7, we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are using the correct block reward value
			assertEquals(100000000L, blockReward);

			/*
			 * Alice, Chloe, and Dilbert are 'online'.
			 * Chloe is level 6; Dilbert is level 7.
			 * One founder online (Alice, who is also level 7).
			 * No legacy QORA holders.
			 *
			 * Level 7 and 8 is not yet activated, so its rewards are added to the level 5 and 6 share bin.
			 * There are no level 5 and 6 online.
			 * Chloe and Dilbert should receive equal shares of the 35% block reward for levels 5 to 8.
			 * Alice should receive the remainder (65%).
			 */

			final int level5To8SharePercent = 35_00; // 35% (combined 15% and 20%)
			final long level5To8ShareAmount = (blockReward * level5To8SharePercent) / 100L / 100L;
			final long expectedLevel5To8Reward = level5To8ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderReward = blockReward - level5To8ShareAmount; // Alice should receive the remainder

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderReward);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance+expectedLevel5To8Reward);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedLevel5To8Reward);

			// Ensure that the levels are as we expect
			assertEquals(7, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(6, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(7, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Capture pre-activation balances
			Map<String, Map<Long, Long>> preActivationBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long alicePreActivationBalance = preActivationBalances.get("alice").get(Asset.QORT);
			final long bobPreActivationBalance = preActivationBalances.get("bob").get(Asset.QORT);
			final long chloePreActivationBalance = preActivationBalances.get("chloe").get(Asset.QORT);
			final long dilbertPreActivationBalance = preActivationBalances.get("dilbert").get(Asset.QORT);

			// Mint another block
			blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that the levels are as we expect (chloe has now increased to level 7; level 7-8 is now activated)
			assertEquals(7, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(7, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(7, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			/*
			 * Alice, Chloe, and Dilbert are 'online'.
			 * Chloe and Dilbert are level 7.
			 * One founder online (Alice, who is also level 7).
			 * No legacy QORA holders.
			 *
			 * Level 7 and 8 is now activated, so its rewards are paid out in the normal way.
			 * There are no level 5 and 6 online.
			 * Chloe and Dilbert should receive equal shares of the 20% block reward for levels 7 to 8.
			 * Alice should receive the remainder (80%).
			 */

			final int level7To8SharePercent = 20_00; // 20%
			final long level7To8ShareAmount = (blockReward * level7To8SharePercent) / 100L / 100L;
			final long expectedLevel7To8Reward = level7To8ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long newExpectedFounderReward = blockReward - level7To8ShareAmount; // Alice should receive the remainder

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, alicePreActivationBalance+newExpectedFounderReward);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobPreActivationBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloePreActivationBalance+expectedLevel7To8Reward);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertPreActivationBalance+expectedLevel7To8Reward);


			// Orphan and ensure balances return to their pre-activation values
			BlockUtils.orphanBlocks(repository, 1);

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, alicePreActivationBalance);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobPreActivationBalance);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloePreActivationBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertPreActivationBalance);


			// Orphan again and ensure balances return to their initial values
			BlockUtils.orphanBlocks(repository, 1);

			// Validate the balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance);
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance);

		}
	}

	/** Test rewards for level 1 and 2 accounts with V2 share-bin layout (post QORA reduction) */
	@Test
	public void testLevel1And2RewardsShareBinsV2() throws DataException {
		Common.useSettings("test-settings-v2-reward-levels.json");

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);
			byte[] chloeRewardSharePrivateKey;
			// Bob self-share NOT online

			// Mint some blocks, to get us close to the V2 activation, but with some room for Chloe and Dilbert to start minting some blocks
			for (int i=0; i<990; i++)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Chloe self share comes online
			try {
				chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			} catch (IllegalArgumentException ex) {
				LOGGER.error("FAILED {}", ex.getLocalizedMessage(), ex);
				throw ex;
			}
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share comes online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint 6 more blocks, so that V2 share bins are nearly activated
			for (int i=0; i<6; i++)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that the levels are as we expect
			assertEquals(10, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(2, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Ensure that only Alice is a founder
			assertEquals(1, getFlags(repository, "alice"));
			assertEquals(0, getFlags(repository, "bob"));
			assertEquals(0, getFlags(repository, "chloe"));
			assertEquals(0, getFlags(repository, "dilbert"));

			// Now that everyone is at level 1 or 2, we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			final long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are at the correct height and block reward value
			assertEquals(1000, (int) repository.getBlockRepository().getLastBlock().getHeight());
			assertEquals(100000000L, blockReward);

			// We are past the sharesByLevelV2Height feature trigger, so we expect level 1 and 2 to share the increased reward (6%)
			final int level1And2SharePercent = 6_00; // 6%
			final long level1And2ShareAmount = (blockReward * level1And2SharePercent) / 100L / 100L;
			final long expectedLevel1And2RewardV2 = level1And2ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderRewardV2 = blockReward - level1And2ShareAmount; // Alice should receive the remainder

			// Validate the balances
			assertEquals(6000000, level1And2ShareAmount);
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderRewardV2);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance+expectedLevel1And2RewardV2);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedLevel1And2RewardV2);

			// Now orphan the latest block. This brings us to the threshold of the sharesByLevelV2Height feature trigger
			BlockUtils.orphanBlocks(repository, 1);
			assertEquals(999, (int) repository.getBlockRepository().getLastBlock().getHeight());

			// Ensure the latest block rewards have been subtracted and they have returned to their initial values
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance);

			// Orphan another block. This time, the block that was orphaned was prior to the sharesByLevelV2Height feature trigger.
			BlockUtils.orphanBlocks(repository, 1);
			assertEquals(998, (int) repository.getBlockRepository().getLastBlock().getHeight());

			// In V1 of share-bins, level 1-2 pays out 5% instead of 6%
			final int level1And2SharePercentV1 = 5_00; // 5%
			final long level1And2ShareAmountV1 = (blockReward * level1And2SharePercentV1) / 100L / 100L;
			final long expectedLevel1And2RewardV1 = level1And2ShareAmountV1 / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderRewardV1 = blockReward - level1And2ShareAmountV1; // Alice should receive the remainder

			// Validate the share amounts and balances
			assertEquals(5000000, level1And2ShareAmountV1);
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance-expectedFounderRewardV1);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance-expectedLevel1And2RewardV1);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance-expectedLevel1And2RewardV1);

			// Orphan the latest block one last time
			BlockUtils.orphanBlocks(repository, 1);
			assertEquals(997, (int) repository.getBlockRepository().getLastBlock().getHeight());

			// Validate balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance-(expectedFounderRewardV1*2));
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance-(expectedLevel1And2RewardV1*2));
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance-(expectedLevel1And2RewardV1*2));

		}
	}

	/** Test rewards for level 1 and 2 accounts with V2 share-bin layout (post QORA reduction)
	 * plus some legacy QORA holders */
	@Test
	public void testLevel1And2RewardsShareBinsV2WithQoraHolders() throws DataException {
		Common.useSettings("test-settings-v2-qora-holder-extremes.json");

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();

			// Some legacy QORA holders exist (Bob and Chloe)

			// Alice self share online
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			mintingAndOnlineAccounts.add(aliceSelfShare);
			byte[] chloeRewardSharePrivateKey;
			// Bob self-share NOT online

			// Mint some blocks, to get us close to the V2 activation, but with some room for Chloe and Dilbert to start minting some blocks
			for (int i=0; i<990; i++)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Chloe self share comes online
			try {
				chloeRewardSharePrivateKey = AccountUtils.rewardShare(repository, "chloe", "chloe", 0);
			} catch (IllegalArgumentException ex) {
				LOGGER.error("FAILED {}", ex.getLocalizedMessage(), ex);
				throw ex;
			}
			PrivateKeyAccount chloeRewardShareAccount = new PrivateKeyAccount(repository, chloeRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(chloeRewardShareAccount);

			// Dilbert self share comes online
			byte[] dilbertRewardSharePrivateKey = AccountUtils.rewardShare(repository, "dilbert", "dilbert", 0);
			PrivateKeyAccount dilbertRewardShareAccount = new PrivateKeyAccount(repository, dilbertRewardSharePrivateKey);
			mintingAndOnlineAccounts.add(dilbertRewardShareAccount);

			// Mint 6 more blocks, so that V2 share bins are nearly activated
			for (int i=0; i<6; i++)
				BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that the levels are as we expect
			assertEquals(10, (int) Common.getTestAccount(repository, "alice").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "bob").getLevel());
			assertEquals(1, (int) Common.getTestAccount(repository, "chloe").getLevel());
			assertEquals(2, (int) Common.getTestAccount(repository, "dilbert").getLevel());

			// Ensure that only Alice is a founder
			assertEquals(1, getFlags(repository, "alice"));
			assertEquals(0, getFlags(repository, "bob"));
			assertEquals(0, getFlags(repository, "chloe"));
			assertEquals(0, getFlags(repository, "dilbert"));

			// Now that everyone is at level 1 or 2, we can capture initial balances
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT, Asset.LEGACY_QORA, Asset.QORT_FROM_QORA);
			final long aliceInitialBalance = initialBalances.get("alice").get(Asset.QORT);
			final long bobInitialBalance = initialBalances.get("bob").get(Asset.QORT);
			final long chloeInitialBalance = initialBalances.get("chloe").get(Asset.QORT);
			final long dilbertInitialBalance = initialBalances.get("dilbert").get(Asset.QORT);

			// Mint a block
			final long blockReward = BlockUtils.getNextBlockReward(repository);
			BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure we are at the correct height and block reward value
			assertEquals(1000, (int) repository.getBlockRepository().getLastBlock().getHeight());
			assertEquals(100000000L, blockReward);

			// We are past the sharesByLevelV2Height feature trigger, so we expect level 1 and 2 to share the increased reward (6%)
			// and the QORA share will be 1%
			final int level1And2SharePercent = 6_00; // 6%
			final int qoraSharePercentV2 = 1_00; // 1%
			final long qoraShareAmountV2 = (blockReward * qoraSharePercentV2) / 100L / 100L;
			final long level1And2ShareAmount = (blockReward * level1And2SharePercent) / 100L / 100L;
			final long expectedLevel1And2RewardV2 = level1And2ShareAmount / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderRewardV2 = blockReward - level1And2ShareAmount - qoraShareAmountV2; // Alice should receive the remainder

			// Validate the balances
			assertEquals(6000000, level1And2ShareAmount);
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance+expectedFounderRewardV2);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			// Chloe is a QORA holder and will receive additional QORT, so it's not easy to pre-calculate her balance
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance+expectedLevel1And2RewardV2);

			// Now orphan the latest block. This brings us to the threshold of the sharesByLevelV2Height feature trigger
			BlockUtils.orphanBlocks(repository, 1);
			assertEquals(999, (int) repository.getBlockRepository().getLastBlock().getHeight());

			// Ensure the latest block rewards have been subtracted and they have returned to their initial values
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			AccountUtils.assertBalance(repository, "chloe", Asset.QORT, chloeInitialBalance);
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance);

			// Orphan another block. This time, the block that was orphaned was prior to the sharesByLevelV2Height feature trigger.
			BlockUtils.orphanBlocks(repository, 1);
			assertEquals(998, (int) repository.getBlockRepository().getLastBlock().getHeight());

			// In V1 of share-bins, level 1-2 pays out 5% instead of 6%, and the QORA share is higher at 20%
			final int level1And2SharePercentV1 = 5_00; // 5%
			final int qoraSharePercentV1 = 20_00; // 20%
			final long qoraShareAmountV1 = (blockReward * qoraSharePercentV1) / 100L / 100L;
			final long level1And2ShareAmountV1 = (blockReward * level1And2SharePercentV1) / 100L / 100L;
			final long expectedLevel1And2RewardV1 = level1And2ShareAmountV1 / 2; // The reward is split between Chloe and Dilbert
			final long expectedFounderRewardV1 = blockReward - level1And2ShareAmountV1 - qoraShareAmountV1; // Alice should receive the remainder

			// Validate the share amounts and balances
			assertEquals(5000000, level1And2ShareAmountV1);
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance-expectedFounderRewardV1);
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			// Chloe is a QORA holder and will receive additional QORT, so it's not easy to pre-calculate her balance
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance-expectedLevel1And2RewardV1);

			// Orphan the latest block one last time
			BlockUtils.orphanBlocks(repository, 1);
			assertEquals(997, (int) repository.getBlockRepository().getLastBlock().getHeight());

			// Validate balances
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, aliceInitialBalance-(expectedFounderRewardV1*2));
			AccountUtils.assertBalance(repository, "bob", Asset.QORT, bobInitialBalance); // Bob not online so his balance remains the same
			// Chloe is a QORA holder and will receive additional QORT, so it's not easy to pre-calculate her balance
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, dilbertInitialBalance-(expectedLevel1And2RewardV1*2));

		}
	}


	private int getFlags(Repository repository, String name) throws DataException {
		TestAccount testAccount = Common.getTestAccount(repository, name);
		return repository.getAccountRepository().getAccount(testAccount.getAddress()).getFlags();
	}

}
