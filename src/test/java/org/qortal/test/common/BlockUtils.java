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

public class BlockUtils {

	private static final Logger LOGGER = LogManager.getLogger(BlockUtils.class);

	/** Mints a new block using "alice-reward-share" test account. */
	public static Block mintBlock(Repository repository) throws DataException {
		PrivateKeyAccount mintingAccount = Common.getTestAccount(repository, "alice-reward-share");
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

}
