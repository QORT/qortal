package org.qortal.test.minting;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.controller.BlockMinter;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.PaymentTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.test.common.*;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.transform.TransformationException;
import org.qortal.transform.block.BlockTransformer;
import org.qortal.utils.NTP;

import java.util.*;

import static org.junit.Assert.*;

public class BatchRewardTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useSettings("test-settings-v2-reward-levels.json");
		NTP.setFixedOffset(Settings.getInstance().getTestNtpOffset());
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	@Test
	public void testBatchReward() throws DataException, IllegalAccessException {
		// Set reward batching to every 10 blocks, starting at block 20, looking back the last 3 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 20, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 10, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT);

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");

			Long blockReward = BlockUtils.getNextBlockReward(repository);

			// Deploy an AT so we have transaction fees in each block
			// This also mints block 2
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, Common.getTestAccount(repository, "bob"), AtUtils.buildSimpleAT(), 1_00000000L);
			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 2);

			long expectedBalance = initialBalances.get("alice").get(Asset.QORT) + blockReward + deployAtTransaction.getTransactionData().getFee();
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, expectedBalance);
			long aliceCurrentBalance = expectedBalance;

			AccountUtils.assertBlocksMinted(repository, "alice", 1);

			// Mint blocks 3-20
			Block block;
			for (int i=3; i<=20; i++) {
				expectedBalance = aliceCurrentBalance + BlockUtils.getNextBlockReward(repository);
				block = BlockUtils.mintBlockWithReorgs(repository, 10);
				expectedBalance += block.getBlockData().getTotalFees();
				assertFalse(block.isBatchRewardDistributionActive());
				assertTrue(block.isRewardDistributionBlock());
				AccountUtils.assertBalance(repository, "alice", Asset.QORT, expectedBalance);
				aliceCurrentBalance = expectedBalance;
			}
			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 20);

			AccountUtils.assertBlocksMinted(repository, "alice", 19);

			// Mint blocks 21-29
			long expectedFees = 0L;
			for (int i=21; i<=29; i++) {

				// Create payment transaction so that an additional fee is added to the next block
				Account recipient = AccountUtils.createRandomAccount(repository);
				TransactionData paymentTransactionData = new PaymentTransactionData(TestTransaction.generateBase(bob), recipient.getAddress(), 100000L);
				TransactionUtils.signAndImportValid(repository, paymentTransactionData, bob);

				block = BlockUtils.mintBlockWithReorgs(repository, 8);
				expectedFees += block.getBlockData().getTotalFees();

				// Batch distribution now active
				assertTrue(block.isBatchRewardDistributionActive());

				// It's not a distribution block because we haven't reached the batch size yet
				assertFalse(block.isRewardDistributionBlock());
			}
			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 29);

			AccountUtils.assertBlocksMinted(repository, "alice", 19);

			// No payouts since block 20 due to batching (to be paid at block 30)
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, expectedBalance);

			// Block reward to be used for next batch payout
			blockReward = BlockUtils.getNextBlockReward(repository);

			// Mint block 30
			block = BlockUtils.mintBlockWithReorgs(repository, 9);
			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 30);

			expectedFees += block.getBlockData().getTotalFees();
			assertTrue(expectedFees > 0);

			AccountUtils.assertBlocksMinted(repository, "alice", 29);

			// Batch distribution still active
			assertTrue(block.isBatchRewardDistributionActive());

			// It's a distribution block
			assertTrue(block.isRewardDistributionBlock());

			// Balance should increase by the block reward multiplied by the batch size
			expectedBalance = aliceCurrentBalance + (blockReward * BlockChain.getInstance().getBlockRewardBatchSize()) + expectedFees;
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, expectedBalance);

			// Mint blocks 31-39
			for (int i=31; i<=39; i++) {
				block = BlockUtils.mintBlockWithReorgs(repository, 13);

				// Batch distribution still active
				assertTrue(block.isBatchRewardDistributionActive());

				// It's not a distribution block because we haven't reached the batch size yet
				assertFalse(block.isRewardDistributionBlock());
			}
			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 39);

			AccountUtils.assertBlocksMinted(repository, "alice", 29);

			// No payouts since block 30 due to batching (to be paid at block 40)
			AccountUtils.assertBalance(repository, "alice", Asset.QORT, expectedBalance);

			// Batch distribution still active
			assertTrue(block.isBatchRewardDistributionActive());

			// It's not a distribution block
			assertFalse(block.isRewardDistributionBlock());
		}
	}

	@Test
	public void testBatchRewardOnlineAccounts() throws DataException, IllegalAccessException {
		// Set reward batching to every 10 blocks, starting at block 0, looking back the last 3 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 10, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			PrivateKeyAccount bobSelfShare = Common.getTestAccount(repository, "bob-reward-share");
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");
			PrivateKeyAccount dilbertSelfShare = Common.getTestAccount(repository, "dilbert-reward-share");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Mint blocks 2-6
			for (int i=2; i<=6; i++) {
				Block block = BlockUtils.mintBlockWithReorgs(repository, 5);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
			}

			// Mint block 7
			List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare);
			Block block7 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(2, block7.getBlockData().getOnlineAccountsCount());

			// Mint block 8
			onlineAccounts = Arrays.asList(aliceSelfShare, chloeSelfShare);
			Block block8 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(2, block8.getBlockData().getOnlineAccountsCount());

			// Mint block 9
			onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, dilbertSelfShare);
			Block block9 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(3, block9.getBlockData().getOnlineAccountsCount());

			// Mint block 10
			Block block10 = BlockUtils.mintBlockWithReorgs(repository, 11);

			// Online accounts should be included from block 8
			assertEquals(3, block10.getBlockData().getOnlineAccountsCount());

			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 10);

			// It's a distribution block
			assertTrue(block10.isBatchRewardDistributionBlock());
		}
	}

	@Test
	public void testBatchReward1000Blocks() throws DataException, IllegalAccessException {
		// Set reward batching to every 1000 blocks, starting at block 1000, looking back the last 25 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 1000, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 1000, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 25, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			PrivateKeyAccount bobSelfShare = Common.getTestAccount(repository, "bob-reward-share");
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Mint blocks 2-1000 - these should be regular non-batched reward distribution blocks
			for (int i=2; i<=1000; i++) {
				Block block = BlockUtils.mintBlockWithReorgs(repository, 2);
				assertFalse(block.isBatchRewardDistributionActive());
				assertTrue(block.isRewardDistributionBlock());
				assertFalse(block.isBatchRewardDistributionBlock());
				assertTrue(block.isOnlineAccountsBlock());
			}

			// Mint blocks 1001-1974 - these should have no online accounts or rewards
			for (int i=1001; i<=1974; i++) {
				Block block = BlockUtils.mintBlockWithReorgs(repository, 2);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
				assertFalse(block.isBatchRewardDistributionBlock());
				assertFalse(block.isOnlineAccountsBlock());
				assertEquals(0, block.getBlockData().getOnlineAccountsCount());
			}

			// Mint blocks 1975-1999 - these should have online accounts but no rewards
			for (int i=1975; i<=1998; i++) {
				List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare);
				Block block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
				assertFalse(block.isBatchRewardDistributionBlock());
				assertTrue(block.isOnlineAccountsBlock());
				assertEquals(2, block.getBlockData().getOnlineAccountsCount());
			}

			// Mint block 1999 - same as above, but with more online accounts
			List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, chloeSelfShare);
			Block block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertTrue(block.isBatchRewardDistributionActive());
			assertFalse(block.isRewardDistributionBlock());
			assertFalse(block.isBatchRewardDistributionBlock());
			assertTrue(block.isOnlineAccountsBlock());
			assertEquals(3, block.getBlockData().getOnlineAccountsCount());

			// Mint block 2000
			Block block2000 = BlockUtils.mintBlockWithReorgs(repository, 12);

			// Online accounts should be included from block 1999
			assertEquals(3, block2000.getBlockData().getOnlineAccountsCount());

			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 2000);

			// It's a distribution block (which is technically also an online accounts block)
			assertTrue(block2000.isBatchRewardDistributionBlock());
			assertTrue(block2000.isRewardDistributionBlock());
			assertTrue(block2000.isBatchRewardDistributionActive());
			assertTrue(block2000.isOnlineAccountsBlock());
		}
	}

	@Test
	public void testBatchRewardHighestOnlineAccountsCount() throws DataException, IllegalAccessException {
		// Set reward batching to every 10 blocks, starting at block 0, looking back the last 3 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 10, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			PrivateKeyAccount bobSelfShare = Common.getTestAccount(repository, "bob-reward-share");
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");
			PrivateKeyAccount dilbertSelfShare = Common.getTestAccount(repository, "dilbert-reward-share");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Mint blocks 2-6
			for (int i=2; i<=6; i++) {
				Block block = BlockUtils.mintBlockWithReorgs(repository, 3);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
			}

			// Capture initial balances now that the online accounts test is ready to begin
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT);

			// Mint block 7
			List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare);
			Block block7 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(2, block7.getBlockData().getOnlineAccountsCount());

			// Mint block 8
			onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, chloeSelfShare);
			Block block8 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(3, block8.getBlockData().getOnlineAccountsCount());

			// Mint block 9
			onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, dilbertSelfShare);
			Block block9 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(3, block9.getBlockData().getOnlineAccountsCount());

			// Mint block 10
			Block block10 = BlockUtils.mintBlockWithReorgs(repository, 7);

			// Online accounts should be included from block 8
			assertEquals(3, block10.getBlockData().getOnlineAccountsCount());

			// Dilbert's balance should remain the same as he wasn't included in block 8
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, initialBalances.get("dilbert").get(Asset.QORT));

			// Alice, Bob, and Chloe's balances should have increased, as they were all included in block 8 (and therefore block 10)
			AccountUtils.assertBalanceGreaterThan(repository, "alice", Asset.QORT, initialBalances.get("alice").get(Asset.QORT));
			AccountUtils.assertBalanceGreaterThan(repository, "bob", Asset.QORT, initialBalances.get("bob").get(Asset.QORT));
			AccountUtils.assertBalanceGreaterThan(repository, "chloe", Asset.QORT, initialBalances.get("chloe").get(Asset.QORT));

			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 10);

			// It's a distribution block
			assertTrue(block10.isBatchRewardDistributionBlock());
		}
	}

	@Test
	public void testBatchRewardNoOnlineAccounts() throws DataException, IllegalAccessException {
		// Set reward batching to every 10 blocks, starting at block 0, looking back the last 3 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 10, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");

			// Mint blocks 2-6 with no online accounts
			for (int i=2; i<=6; i++) {
				Block block = BlockMinter.mintTestingBlockUnvalidatedWithoutOnlineAccounts(repository, aliceSelfShare);
				assertNotNull("Minted block must not be null", block);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
			}

			// Mint block 7 with no online accounts
			Block block7 = BlockMinter.mintTestingBlockUnvalidatedWithoutOnlineAccounts(repository, aliceSelfShare);
			assertNull("Minted block must be null", block7);

			// Mint block 7, this time with an online account
			List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare);
			block7 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertNotNull("Minted block must not be null", block7);
			assertEquals(1, block7.getBlockData().getOnlineAccountsCount());

			// Mint block 8 with no online accounts
			Block block8 = BlockMinter.mintTestingBlockUnvalidatedWithoutOnlineAccounts(repository, aliceSelfShare);
			assertNull("Minted block must be null", block8);

			// Mint block 8, this time with an online account
			block8 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertNotNull("Minted block must not be null", block8);
			assertEquals(1, block8.getBlockData().getOnlineAccountsCount());

			// Mint block 9 with no online accounts
			Block block9 = BlockMinter.mintTestingBlockUnvalidatedWithoutOnlineAccounts(repository, aliceSelfShare);
			assertNull("Minted block must be null", block9);

			// Mint block 9, this time with an online account
			block9 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertNotNull("Minted block must not be null", block9);
			assertEquals(1, block9.getBlockData().getOnlineAccountsCount());

			// Mint block 10
			Block block10 = BlockUtils.mintBlockWithReorgs(repository, 8);
			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 10);

			// It's a distribution block
			assertTrue(block10.isBatchRewardDistributionBlock());
		}
	}

	@Test
	public void testMissingOnlineAccountsInDistributionBlock() throws DataException, IllegalAccessException {
		// Set reward batching to every 10 blocks, starting at block 0, looking back the last 3 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 10, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			PrivateKeyAccount bobSelfShare = Common.getTestAccount(repository, "bob-reward-share");
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Mint blocks 2-6
			for (int i=2; i<=6; i++) {
				Block block = BlockUtils.mintBlockWithReorgs(repository, 9);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
			}

			// Mint blocks 7-9
			for (int i=7; i<=9; i++) {
				List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, chloeSelfShare);
				Block block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
				assertEquals(3, block.getBlockData().getOnlineAccountsCount());
			}

			// Mint block 10
			Block block10 = Block.mint(repository, repository.getBlockRepository().getLastBlock(), aliceSelfShare);
			assertNotNull(block10);

			// Remove online accounts (incorrect as there should be 3)
			block10.getBlockData().setEncodedOnlineAccounts(new byte[0]);

			block10.sign();
			block10.clearOnlineAccountsValidationCache();

			// Must be invalid because online accounts don't match
			assertEquals(Block.ValidationResult.ONLINE_ACCOUNTS_INVALID, block10.isValid());
		}
	}

	@Test
	public void testSignaturesIncludedInDistributionBlock() throws DataException, IllegalAccessException {
		// Set reward batching to every 10 blocks, starting at block 0, looking back the last 3 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 10, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			PrivateKeyAccount bobSelfShare = Common.getTestAccount(repository, "bob-reward-share");
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Mint blocks 2-6
			for (int i=2; i<=6; i++) {
				Block block = BlockUtils.mintBlockWithReorgs(repository, 4);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
			}

			// Mint blocks 7-9
			for (int i=7; i<=9; i++) {
				List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, chloeSelfShare);
				Block block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
				assertEquals(3, block.getBlockData().getOnlineAccountsCount());
			}

			// Mint block 10
			BlockData previousBlock = repository.getBlockRepository().getLastBlock();
			Block block10 = Block.mint(repository, previousBlock, aliceSelfShare);
			assertNotNull(block10);

			// Include online accounts signatures
			block10.getBlockData().setOnlineAccountsSignatures(previousBlock.getOnlineAccountsSignatures());

			block10.sign();
			block10.clearOnlineAccountsValidationCache();

			// Must be invalid because signatures aren't allowed to be included
			assertEquals(Block.ValidationResult.ONLINE_ACCOUNTS_INVALID, block10.isValid());
		}
	}

	@Test
	public void testOnlineAccountsTimestampIncludedInDistributionBlock() throws DataException, IllegalAccessException {
		// Set reward batching to every 10 blocks, starting at block 0, looking back the last 3 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 10, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			PrivateKeyAccount bobSelfShare = Common.getTestAccount(repository, "bob-reward-share");
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Mint blocks 2-6
			for (int i=2; i<=6; i++) {
				Block block = BlockUtils.mintBlockWithReorgs(repository, 6);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
			}

			// Mint blocks 7-9
			for (int i=7; i<=9; i++) {
				List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, chloeSelfShare);
				Block block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
				assertEquals(3, block.getBlockData().getOnlineAccountsCount());
			}

			// Mint block 10
			BlockData previousBlock = repository.getBlockRepository().getLastBlock();
			Block block10 = Block.mint(repository, previousBlock, aliceSelfShare);
			assertNotNull(block10);

			// Include online accounts timestamp
			block10.getBlockData().setOnlineAccountsTimestamp(previousBlock.getOnlineAccountsTimestamp());

			block10.sign();
			block10.clearOnlineAccountsValidationCache();

			// Must be invalid because timestamp isn't allowed to be included
			assertEquals(Block.ValidationResult.ONLINE_ACCOUNTS_INVALID, block10.isValid());
		}
	}

	@Test
	public void testIncorrectOnlineAccountsCountInDistributionBlock() throws DataException, IllegalAccessException {
		// Set reward batching to every 10 blocks, starting at block 0, looking back the last 3 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 10, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			PrivateKeyAccount bobSelfShare = Common.getTestAccount(repository, "bob-reward-share");
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Mint blocks 2-6
			for (int i=2; i<=6; i++) {
				Block block = BlockUtils.mintBlockWithReorgs(repository, 5);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
			}

			// Mint blocks 7-9
			for (int i=7; i<=9; i++) {
				List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, chloeSelfShare);
				Block block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
				assertEquals(3, block.getBlockData().getOnlineAccountsCount());
			}

			// Mint block 10
			BlockData previousBlock = repository.getBlockRepository().getLastBlock();
			Block block10 = Block.mint(repository, previousBlock, aliceSelfShare);
			assertNotNull(block10);

			// Update online accounts count so that it is incorrect
			block10.getBlockData().setOnlineAccountsCount(10);

			block10.sign();
			block10.clearOnlineAccountsValidationCache();

			// Must be invalid because online accounts count is incorrect
			assertEquals(Block.ValidationResult.ONLINE_ACCOUNTS_INVALID, block10.isValid());
		}
	}

	@Test
	public void testBatchRewardBlockSerialization() throws DataException, IllegalAccessException, TransformationException {
		// Set reward batching to every 10 blocks, starting at block 0, looking back the last 3 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 10, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 3, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			PrivateKeyAccount bobSelfShare = Common.getTestAccount(repository, "bob-reward-share");
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");
			PrivateKeyAccount dilbertSelfShare = Common.getTestAccount(repository, "dilbert-reward-share");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Mint blocks 2-6
			Block block = null;
			for (int i=2; i<=6; i++) {
				block = BlockUtils.mintBlockWithReorgs(repository, 7);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
			}

			// Test serialising and deserializing a block with no online accounts
			BlockData block6Data = block.getBlockData();
			byte[] block6Bytes = BlockTransformer.toBytes(block);
			BlockData block6DataDeserialized = BlockTransformer.fromBytes(block6Bytes).getBlockData();
			BlockUtils.assertEqual(block6Data, block6DataDeserialized);

			// Capture initial balances now that the online accounts test is ready to begin
			Map<String, Map<Long, Long>> initialBalances = AccountUtils.getBalances(repository, Asset.QORT);

			// Mint block 7
			List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare);
			Block block7 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(2, block7.getBlockData().getOnlineAccountsCount());

			// Mint block 8
			onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, chloeSelfShare);
			Block block8 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(3, block8.getBlockData().getOnlineAccountsCount());

			// Mint block 9
			onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, dilbertSelfShare);
			Block block9 = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(3, block9.getBlockData().getOnlineAccountsCount());

			// Mint block 10
			Block block10 = BlockUtils.mintBlockWithReorgs(repository, 15);

			// Online accounts should be included from block 8
			assertEquals(3, block10.getBlockData().getOnlineAccountsCount());

			// Dilbert's balance should remain the same as he wasn't included in block 8
			AccountUtils.assertBalance(repository, "dilbert", Asset.QORT, initialBalances.get("dilbert").get(Asset.QORT));

			// Alice, Bob, and Chloe's balances should have increased, as they were all included in block 8 (and therefore block 10)
			AccountUtils.assertBalanceGreaterThan(repository, "alice", Asset.QORT, initialBalances.get("alice").get(Asset.QORT));
			AccountUtils.assertBalanceGreaterThan(repository, "bob", Asset.QORT, initialBalances.get("bob").get(Asset.QORT));
			AccountUtils.assertBalanceGreaterThan(repository, "chloe", Asset.QORT, initialBalances.get("chloe").get(Asset.QORT));

			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 10);

			// It's a distribution block
			assertTrue(block10.isBatchRewardDistributionBlock());
		}
	}

	@Test
	public void testUnconfirmableRewardShares() throws DataException, IllegalAccessException {
		// test-settings-v2-reward-scaling.json has unconfirmable reward share feature trigger enabled from block 500
		Common.useSettings("test-settings-v2-reward-scaling.json");

		// Set reward batching to every 1000 blocks, starting at block 0, looking back the last 25 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 1000, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 25, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			PrivateKeyAccount bobSelfShare = Common.getTestAccount(repository, "bob-reward-share");
			PrivateKeyAccount chloeSelfShare = Common.getTestAccount(repository, "chloe-reward-share");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Mint blocks 1-974 - these should have no online accounts or rewards
			for (int i=1; i<974; i++) {
				Block block = BlockUtils.mintBlockWithReorgs(repository, 2);
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
				assertFalse(block.isBatchRewardDistributionBlock());
				assertFalse(block.isOnlineAccountsBlock());
				assertEquals(0, block.getBlockData().getOnlineAccountsCount());
			}

			// Mint blocks 975-998 - these should have online accounts but no rewards
			for (int i=975; i<=998; i++) {
				List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, chloeSelfShare);
				Block block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
				assertTrue(block.isBatchRewardDistributionActive());
				assertFalse(block.isRewardDistributionBlock());
				assertFalse(block.isBatchRewardDistributionBlock());
				assertTrue(block.isOnlineAccountsBlock());
				assertEquals(3, block.getBlockData().getOnlineAccountsCount());
			}

			// Cancel Chloe's reward share
			TransactionData transactionData = AccountUtils.createRewardShare(repository, chloe, chloe, -100, 10000000L);
			TransactionUtils.signAndImportValid(repository, transactionData, chloe);

			// Mint block 999 - Chloe's account should still be included as the reward share cancellation is delayed
			List<PrivateKeyAccount> onlineAccounts = Arrays.asList(aliceSelfShare, bobSelfShare, chloeSelfShare);
			Block block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertTrue(block.isBatchRewardDistributionActive());
			assertFalse(block.isRewardDistributionBlock());
			assertFalse(block.isBatchRewardDistributionBlock());
			assertTrue(block.isOnlineAccountsBlock());
			assertEquals(3, block.getBlockData().getOnlineAccountsCount());

			// Mint block 1000
			Block block1000 = BlockUtils.mintBlockWithReorgs(repository, 12);

			// Online accounts should be included from block 999
			assertEquals(3, block1000.getBlockData().getOnlineAccountsCount());

			assertEquals(repository.getBlockRepository().getBlockchainHeight(), 1000);

			// It's a distribution block (which is technically also an online accounts block)
			assertTrue(block1000.isBatchRewardDistributionBlock());
			assertTrue(block1000.isRewardDistributionBlock());
			assertTrue(block1000.isBatchRewardDistributionActive());
			assertTrue(block1000.isOnlineAccountsBlock());
		}
	}

	@Test
	public void testUnconfirmableRewardShareBlocks() throws DataException, IllegalAccessException {
		// test-settings-v2-reward-scaling.json has unconfirmable reward share feature trigger enabled from block 500
		Common.useSettings("test-settings-v2-reward-scaling.json");

		// Set reward batching to every 1000 blocks, starting at block 0, looking back the last 25 blocks for online accounts
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchStartHeight", 0, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchSize", 1000, true);
		FieldUtils.writeField(BlockChain.getInstance(), "blockRewardBatchAccountsBlockCount", 25, true);

		try (final Repository repository = RepositoryManager.getRepository()) {

			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			// Create self shares for bob, chloe and dilbert
			AccountUtils.generateSelfShares(repository, List.of(bob, chloe, dilbert));

			// Create transaction to cancel chloe's reward share
			TransactionData rewardShareTransactionData = AccountUtils.createRewardShare(repository, chloe, chloe, -100, 10000000L);
			Transaction rewardShareTransaction = Transaction.fromData(repository, rewardShareTransactionData);

			// Mint a block
			BlockUtils.mintBlock(repository);

			// Check block heights up to 974 - transaction should be confirmable
			for (int height=2; height<974; height++) {
				assertEquals(true, rewardShareTransaction.isConfirmableAtHeight(height));
			}

			// Check block heights 975-1000 - transaction should not be confirmable
			for (int height=975; height<1000; height++) {
				assertEquals(false, rewardShareTransaction.isConfirmableAtHeight(height));
			}

			// Check block heights 1001-1974 - transaction should be confirmable again
			for (int height=1001; height<1974; height++) {
				assertEquals(true, rewardShareTransaction.isConfirmableAtHeight(height));
			}
		}
	}

}
