package org.qortal.controller.repository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.Controller;
import org.qortal.controller.Synchronizer;
import org.qortal.data.block.BlockData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.utils.NTP;

import static java.lang.Thread.NORM_PRIORITY;

public class BlockPruner implements Runnable {

	private static final Logger LOGGER = LogManager.getLogger(BlockPruner.class);

	@Override
	public void run() {
		Thread.currentThread().setName("Block pruner");

		if (Settings.getInstance().isLite()) {
			// Nothing to prune in lite mode
			return;
		}

		boolean archiveMode = false;
		if (!Settings.getInstance().isTopOnly()) {
			// Top-only mode isn't enabled, but we might want to prune for the purposes of archiving
			if (!Settings.getInstance().isArchiveEnabled()) {
				// No pruning or archiving, so we must not prune anything
				return;
			}
			else {
				// We're allowed to prune blocks that have already been archived
				archiveMode = true;
			}
		}

		int pruneStartHeight;

		try (final Repository repository = RepositoryManager.getRepository()) {
			pruneStartHeight = repository.getBlockRepository().getBlockPruneHeight();

			// Don't attempt to prune if we have no ATStatesHeightIndex, as it will be too slow
			boolean hasAtStatesHeightIndex = repository.getATRepository().hasAtStatesHeightIndex();
			if (!hasAtStatesHeightIndex) {
				LOGGER.info("Unable to start block pruner due to missing ATStatesHeightIndex. Bootstrapping is recommended.");
				return;
			}
		} catch (Exception e) {
			LOGGER.error("Block Pruning is not working! Not trying again. Restart ASAP. Report this error immediately to the developers.", e);
			return;
		}

		while (!Controller.isStopping()) {

			try (final Repository repository = RepositoryManager.getRepository()) {

				try {
					repository.discardChanges();

					Thread.sleep(Settings.getInstance().getBlockPruneInterval());

					BlockData chainTip = Controller.getInstance().getChainTip();
					if (chainTip == null || NTP.getTime() == null)
						continue;

					// Don't even attempt if we're mid-sync as our repository requests will be delayed for ages
					if (Synchronizer.getInstance().isSynchronizing()) {
						continue;
					}

					// Don't attempt to prune if we're not synced yet
					final Long minLatestBlockTimestamp = Controller.getMinimumLatestBlockTimestamp();
					if (minLatestBlockTimestamp == null || chainTip.getTimestamp() < minLatestBlockTimestamp) {
						continue;
					}

					// Prune all blocks up until our latest minus pruneBlockLimit
					final int ourLatestHeight = chainTip.getHeight();
					int upperPrunableHeight = ourLatestHeight - Settings.getInstance().getPruneBlockLimit();

					// In archive mode we are only allowed to trim blocks that have already been archived
					if (archiveMode) {
						upperPrunableHeight = repository.getBlockArchiveRepository().getBlockArchiveHeight() - 1;
					}

					int upperBatchHeight = pruneStartHeight + Settings.getInstance().getBlockPruneBatchSize();
					int upperPruneHeight = Math.min(upperBatchHeight, upperPrunableHeight);

					if (pruneStartHeight >= upperPruneHeight) {
						continue;
					}

					LOGGER.info(String.format("Pruning blocks between %d and %d...", pruneStartHeight, upperPruneHeight));

					int numBlocksPruned = repository.getBlockRepository().pruneBlocks(pruneStartHeight, upperPruneHeight);
					repository.saveChanges();

					if (numBlocksPruned > 0) {
						LOGGER.info(String.format("Pruned %d block%s between %d and %d",
								numBlocksPruned, (numBlocksPruned != 1 ? "s" : ""),
								pruneStartHeight, upperPruneHeight));
					} else {
						final int nextPruneHeight = upperPruneHeight + 1;
						repository.getBlockRepository().setBlockPruneHeight(nextPruneHeight);
						repository.saveChanges();
						LOGGER.info(String.format("Bumping block base prune height to %d", pruneStartHeight));

						// Can we move onto next batch?
						if (upperPrunableHeight > nextPruneHeight) {
							pruneStartHeight = nextPruneHeight;
						}
						else {
							// We've pruned up to the upper prunable height
							// Back off for a while to save CPU for syncing
							repository.discardChanges();
							Thread.sleep(10*60*1000L);
						}
					}
				} catch (InterruptedException e) {
					if(Controller.isStopping()) {
						LOGGER.info("Block Pruning Shutting Down");
					}
					else {
						LOGGER.warn("Block Pruning interrupted. Trying again. Report this error immediately to the developers.", e);
					}
				} catch (Exception e) {
					LOGGER.warn("Block Pruning stopped working. Trying again. Report this error immediately to the developers.", e);
				}
			} catch(Exception e){
				LOGGER.error("Block Pruning is not working! Not trying again. Restart ASAP. Report this error immediately to the developers.", e);
			}
		}
	}
}
