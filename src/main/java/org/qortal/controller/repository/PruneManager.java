package org.qortal.controller.repository;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.Controller;
import org.qortal.data.block.BlockData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.settings.Settings;
import org.qortal.utils.DaemonThreadFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PruneManager {

    private static final Logger LOGGER = LogManager.getLogger(PruneManager.class);

    private static PruneManager instance;

    private boolean isTopOnly = Settings.getInstance().isTopOnly();
    private int pruneBlockLimit = Settings.getInstance().getPruneBlockLimit();

    private ExecutorService executorService;

    private PruneManager() {

    }

    public static synchronized PruneManager getInstance() {
        if (instance == null)
            instance = new PruneManager();

        return instance;
    }

    public void start() {
        this.executorService = Executors.newCachedThreadPool(new DaemonThreadFactory(Settings.getInstance().getPruningThreadPriority()));

        if (Settings.getInstance().isTopOnly()) {
            // Top-only-sync
            this.startTopOnlySyncMode();
        }
        else if (Settings.getInstance().isArchiveEnabled()) {
            // Full node with block archive
            this.startFullNodeWithBlockArchive();
        }
        else {
            // Full node with full SQL support
            this.startFullSQLNode();
        }
    }

    /**
     * Top-only-sync
     * In this mode, we delete (prune) all blocks except
     * a small number of recent ones. There is no need for
     * trimming or archiving, because all relevant blocks
     * are deleted.
     */
    private void startTopOnlySyncMode() {
        this.startPruning();

        // We don't need the block archive in top-only mode
        this.deleteArchive();
    }

    /**
     * Full node with block archive
     * In this mode we archive trimmed blocks, and then
     * prune archived blocks to keep the database small
     */
    private void startFullNodeWithBlockArchive() {
        this.startTrimming();
        this.startArchiving();
        this.startPruning();
    }

    /**
     * Full node with full SQL support
     * In this mode we trim the database but don't prune
     * or archive any data, because we want to maintain
     * full SQL support of old blocks. This mode will not
     * be actively maintained but can be used by those who
     * need to perform SQL analysis on older blocks.
     */
    private void startFullSQLNode() {
        this.startTrimming();
    }


    private void startPruning() {
        this.executorService.execute(new AtStatesPruner());
        this.executorService.execute(new BlockPruner());
    }

    private void startTrimming() {
        this.executorService.execute(new AtStatesTrimmer());
        this.executorService.execute(new OnlineAccountsSignaturesTrimmer());
    }

    private void startArchiving() {
        this.executorService.execute(new BlockArchiver());
    }

    private void deleteArchive() {
        if (!Settings.getInstance().isTopOnly()) {
            LOGGER.error("Refusing to delete archive when not in top-only mode");
        }

        try {
            Path archivePath = Paths.get(Settings.getInstance().getRepositoryPath(), "archive");
            if (archivePath.toFile().exists()) {
                LOGGER.info("Deleting block archive because we are in top-only mode...");
                FileUtils.deleteDirectory(archivePath.toFile());
            }

        } catch (IOException e) {
            LOGGER.info("Couldn't delete archive: {}", e.getMessage());
        }
    }

    public void stop() {
        this.executorService.shutdownNow();

        try {
            this.executorService.awaitTermination(2L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // We tried...
        }
    }

    public boolean isBlockPruned(int height) throws DataException {
        if (!this.isTopOnly) {
            return false;
        }

        BlockData chainTip = Controller.getInstance().getChainTip();
        if (chainTip == null) {
            throw new DataException("Unable to determine chain tip when checking if a block is pruned");
        }

        if (height == 1) {
            // We don't prune the genesis block
            return false;
        }

        final int ourLatestHeight = chainTip.getHeight();
        final int latestUnprunedHeight = ourLatestHeight - this.pruneBlockLimit;

        return (height < latestUnprunedHeight);
    }

    /**
     * When rebuilding the latest AT states, we need to specify a maxHeight, so that we aren't tracking
     * very recent AT states that could potentially be orphaned. This method ensures that AT states
     * are given a sufficient number of blocks to confirm before being tracked as a latest AT state.
     */
    public static int getMaxHeightForLatestAtStates(Repository repository) throws DataException {
        // Get current chain height, and subtract a certain number of "confirmation" blocks
        // This is to ensure we are basing our latest AT states data on confirmed blocks -
        // ones that won't be orphaned in any normal circumstances
        final int confirmationBlocks = 250;
        final int chainHeight = repository.getBlockRepository().getBlockchainHeight();
        return chainHeight - confirmationBlocks;
    }

}
