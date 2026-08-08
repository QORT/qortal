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

import static java.lang.Thread.MIN_PRIORITY;

public class AtStatesTrimmer implements Runnable {

	private static final Logger LOGGER = LogManager.getLogger(AtStatesTrimmer.class);

	@Override
	public void run() {
		Thread.currentThread().setName("AT States trimmer");

		if (Settings.getInstance().isLite()) {
			// Nothing to trim in lite mode
			return;
		}

		int trimStartHeight;
		int maxLatestAtStatesHeight;

		try (final Repository repository = RepositoryManager.getRepository()) {
			trimStartHeight = repository.getATRepository().getAtTrimHeight();
			maxLatestAtStatesHeight = PruneManager.getMaxHeightForLatestAtStates(repository);

			repository.discardChanges();
			repository.getATRepository().rebuildLatestAtStates(maxLatestAtStatesHeight);
			repository.saveChanges();
		} catch (Exception e) {
			LOGGER.error("AT States Trimming is not working! Not trying again. Restart ASAP. Report this error immediately to the developers.", e);
			return;
		}

		while (!Controller.isStopping()) {
			try (final Repository repository = RepositoryManager.getRepository()) {
				try {
					repository.discardChanges();

					Thread.sleep(Settings.getInstance().getAtStatesTrimInterval());

					BlockData chainTip = Controller.getInstance().getChainTip();
					if (chainTip == null || NTP.getTime() == null)
						continue;

					// Don't even attempt if we're mid-sync as our repository requests will be delayed for ages
					if (Synchronizer.getInstance().isSynchronizing())
						continue;

					long currentTrimmableTimestamp = NTP.getTime() - Settings.getInstance().getAtStatesMaxLifetime();
					// We want to keep AT states near the tip of our copy of blockchain so we can process/orphan nearby blocks
					long chainTrimmableTimestamp = chainTip.getTimestamp() - Settings.getInstance().getAtStatesMaxLifetime();

					long upperTrimmableTimestamp = Math.min(currentTrimmableTimestamp, chainTrimmableTimestamp);
					int upperTrimmableHeight = repository.getBlockRepository().getHeightFromTimestamp(upperTrimmableTimestamp);

					int upperBatchHeight = trimStartHeight + Settings.getInstance().getAtStatesTrimBatchSize();
					int upperTrimHeight = Math.min(upperBatchHeight, upperTrimmableHeight);

					if (trimStartHeight >= upperTrimHeight)
						continue;

					int numAtStatesTrimmed = repository.getATRepository().trimAtStates(trimStartHeight, upperTrimHeight, Settings.getInstance().getAtStatesTrimLimit());
					repository.saveChanges();

					if (numAtStatesTrimmed > 0) {
						final int finalTrimStartHeight = trimStartHeight;
						LOGGER.info(() -> String.format("Trimmed %d AT state%s between blocks %d and %d",
								numAtStatesTrimmed, (numAtStatesTrimmed != 1 ? "s" : ""),
								finalTrimStartHeight, upperTrimHeight));
					} else {
						// Can we move onto next batch?
						if (upperTrimmableHeight > upperBatchHeight) {
							trimStartHeight = upperBatchHeight;
							repository.getATRepository().setAtTrimHeight(trimStartHeight);
							maxLatestAtStatesHeight = PruneManager.getMaxHeightForLatestAtStates(repository);
							repository.getATRepository().rebuildLatestAtStates(maxLatestAtStatesHeight);
							repository.saveChanges();

							final int finalTrimStartHeight = trimStartHeight;
							LOGGER.info(() -> String.format("Bumping AT state base trim height to %d", finalTrimStartHeight));
						}
					}
				} catch (InterruptedException e) {
					if(Controller.isStopping()) {
						LOGGER.info("AT States Trimming Shutting Down");
					}
					else {
						LOGGER.warn("AT States Trimming interrupted. Trying again. Report this error immediately to the developers.", e);
					}
				} catch (Exception e) {
					LOGGER.warn("AT States Trimming stopped working. Trying again. Report this error immediately to the developers.", e);
				}
			} catch (Exception e) {
				LOGGER.error("AT States Trimming is not working! Not trying again. Restart ASAP. Report this error immediately to the developers.", e);
			}
		}
	}

}
