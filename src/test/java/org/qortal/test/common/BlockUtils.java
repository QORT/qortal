package org.qortal.test.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.controller.BlockMinter;
import org.qortal.data.block.BlockData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import static org.junit.Assert.*;

public class BlockUtils {

	private static final Logger LOGGER = LogManager.getLogger(BlockUtils.class);

	/** Mints a new block using "alice-reward-share" test account. */
	public static Block mintBlock(Repository repository) throws DataException {
		PrivateKeyAccount mintingAccount = Common.getTestAccount(repository, "alice-reward-share");
		return BlockMinter.mintTestingBlock(repository, mintingAccount);
	}

	/** Mints multiple blocks using "alice-reward-share" test account, and returns the final block. */
	public static Block mintBlocks(Repository repository, int count) throws DataException {
		Block block = null;
		for (int i=0; i<count; i++) {
			block = BlockUtils.mintBlock(repository);
		}
		return block;
	}

	/** Mints a new block using "alice-reward-share" test account, via multiple re-orgs. */
	public static Block mintBlockWithReorgs(Repository repository, int reorgCount) throws DataException {
		PrivateKeyAccount mintingAccount = Common.getTestAccount(repository, "alice-reward-share");
		Block block;

		for (int i=0; i<reorgCount; i++) {
			block = BlockMinter.mintTestingBlock(repository, mintingAccount);
			assertNotNull(block);
			BlockUtils.orphanLastBlock(repository);
		}

		return BlockMinter.mintTestingBlock(repository, mintingAccount);
	}

	public static Long getNextBlockReward(Repository repository) throws DataException {
		int currentHeight = repository.getBlockRepository().getBlockchainHeight();

		return BlockChain.getInstance().getRewardAtHeight(currentHeight + 1);
	}

	public static void orphanLastBlock(Repository repository) throws DataException {
		BlockData blockData = repository.getBlockRepository().getLastBlock();

		final int height = blockData.getHeight();

		Block block = new Block(repository, blockData);
		block.orphan();

		LOGGER.info(String.format("Orphaned block: %d", height));

		repository.saveChanges();
	}

	public static void orphanBlocks(Repository repository, int count) throws DataException {
		for (int i = 0; i < count; ++i)
			orphanLastBlock(repository);
	}

	public static void orphanToBlock(Repository repository, int targetHeight) throws DataException {
		do {
			BlockData blockData = repository.getBlockRepository().getLastBlock();
			final int height = blockData.getHeight();

			if (height <= targetHeight)
				return;

			Block block = new Block(repository, blockData);
			block.orphan();

			LOGGER.info(String.format("Orphaned block: %d", height));

			repository.saveChanges();
		} while (true);
	}

	public static void assertEqual(BlockData block1, BlockData block2) {
		assertArrayEquals(block1.getSignature(), block2.getSignature());
		assertEquals(block1.getVersion(), block2.getVersion());
		assertArrayEquals(block1.getReference(), block2.getReference());
		assertEquals(block1.getTransactionCount(), block2.getTransactionCount());
		assertEquals(block1.getTotalFees(), block2.getTotalFees());
		assertArrayEquals(block1.getTransactionsSignature(), block2.getTransactionsSignature());
		// assertEquals(block1.getHeight(), block2.getHeight()); // Height not automatically included after deserialization
		assertEquals(block1.getTimestamp(), block2.getTimestamp());
		assertArrayEquals(block1.getMinterPublicKey(), block2.getMinterPublicKey());
		assertArrayEquals(block1.getMinterSignature(), block2.getMinterSignature());
		assertEquals(block1.getATCount(), block2.getATCount());
		assertEquals(block1.getATFees(), block2.getATFees());
		assertArrayEquals(block1.getEncodedOnlineAccounts(), block2.getEncodedOnlineAccounts());
		assertEquals(block1.getOnlineAccountsCount(), block2.getOnlineAccountsCount());
		assertEquals(block1.getOnlineAccountsTimestamp(), block2.getOnlineAccountsTimestamp());
		assertArrayEquals(block1.getOnlineAccountsSignatures(), block2.getOnlineAccountsSignatures());
	}

}
