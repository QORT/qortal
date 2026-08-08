package org.qortal.repository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.block.Block;
import org.qortal.block.GenesisBlock;
import org.qortal.controller.Controller;
import org.qortal.data.block.BlockArchiveData;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.settings.Settings;
import org.qortal.transaction.Transaction;
import org.qortal.transform.block.BlockTransformation;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

import java.util.concurrent.TimeoutException;

public class ReindexManager {

    private static final Logger LOGGER = LogManager.getLogger(ReindexManager.class);

    private Repository repository;

    private final int pruneAndTrimBlockInterval = 2000;
    private final int maintenanceBlockInterval = 50000;

    private boolean resume = false;

    public ReindexManager() {

    }

    public void reindex() throws DataException {
        try {
            this.runPreChecks();
            this.rebuildRepository();

            try (final Repository repository = RepositoryManager.getRepository()) {
                this.repository = repository;
                this.requestCheckpoint();
                this.processGenesisBlock();
                this.processBlocks();
            }

        } catch (InterruptedException e) {
            throw new DataException("Interrupted before complete");
        }
    }

    private void runPreChecks() throws DataException, InterruptedException {
        LOGGER.info("Running pre-checks...");
        if (Settings.getInstance().isTopOnly()) {
            throw new DataException("Reindexing not supported in top-only mode. Please bootstrap or resync from genesis.");
        }
        if (Settings.getInstance().isLite()) {
            throw new DataException("Reindexing not supported in lite mode.");
        }

        while (NTP.getTime() == null) {
            LOGGER.info("Waiting for NTP...");
            Thread.sleep(5000L);
        }
    }

    private void rebuildRepository() throws DataException {
        if (resume) {
            return;
        }

        LOGGER.info("Rebuilding repository...");
        RepositoryManager.rebuild();
    }

    private void requestCheckpoint() {
        RepositoryManager.setRequestedCheckpoint(Boolean.TRUE);
    }

    private void processGenesisBlock() throws DataException, InterruptedException {
        if (resume) {
            return;
        }

        LOGGER.info("Processing genesis block...");

        GenesisBlock genesisBlock = GenesisBlock.getInstance(repository);

        // Add Genesis Block to blockchain
        genesisBlock.process();

        this.repository.saveChanges();
    }

    private void processBlocks() throws DataException {
        LOGGER.info("Processing blocks...");

        int height = this.repository.getBlockRepository().getBlockchainHeight();
        while (true) {
            height++;

            boolean processed = this.processBlock(height);
            if (!processed) {
                LOGGER.info("Block {} couldn't be processed. If this is the last archived block, then the process is complete.", height);
                break; // TODO: check if complete
            }

            // Prune and trim regularly, leaving a buffer
            if (height >= pruneAndTrimBlockInterval*2 && height % pruneAndTrimBlockInterval == 0) {
                int startHeight = Math.max(height - pruneAndTrimBlockInterval*2, 2);
                int endHeight = height - pruneAndTrimBlockInterval;
                LOGGER.info("Pruning and trimming blocks {} to {}...", startHeight, endHeight);
                this.repository.getATRepository().rebuildLatestAtStates(height - 250);
                this.repository.saveChanges();
                this.prune(startHeight, endHeight);
                this.trim(startHeight, endHeight);
            }

            // Run repository maintenance regularly, to keep blockchain.data size down
            if (height % maintenanceBlockInterval == 0) {
                this.runRepositoryMaintenance();
            }
        }
    }

    private boolean processBlock(int height) throws DataException {
        Block block = this.fetchBlock(height);
        if (block == null) {
            return false;
        }

        // Transactions are stored without approval status so determine that now
        for (Transaction transaction : block.getTransactions())
            transaction.setInitialApprovalStatus();

        // It's best not to run preProcess() until there is a reason to
        // block.preProcess();

        Block.ValidationResult validationResult = block.isValid();
        if (validationResult != Block.ValidationResult.OK) {
            throw new DataException(String.format("Invalid block at height %d: %s", height, validationResult));
        }

        // Save transactions attached to this block
        for (Transaction transaction : block.getTransactions()) {
            TransactionData transactionData = transaction.getTransactionData();
            this.repository.getTransactionRepository().save(transactionData);
        }

        block.process();

        LOGGER.info(String.format("Reindexed block height %d, sig %.8s", block.getBlockData().getHeight(), Base58.encode(block.getBlockData().getSignature())));

        // Add to block archive table, since this originated from the archive but the chainstate has to be rebuilt
        this.addToBlockArchive(block.getBlockData());

        this.repository.saveChanges();

        Controller.getInstance().onNewBlock(block.getBlockData());

        return true;
    }

    private Block fetchBlock(int height) {
        BlockTransformation b = BlockArchiveReader.getInstance().fetchBlockAtHeight(height);
        if (b != null) {
            if (b.getAtStatesHash() != null) {
                return new Block(this.repository, b.getBlockData(), b.getTransactions(), b.getAtStatesHash());
            }
            else {
                return new Block(this.repository, b.getBlockData(), b.getTransactions(), b.getAtStates());
            }
        }

        return null;
    }

    private void addToBlockArchive(BlockData blockData) throws DataException {
        // Write the signature and height into the BlockArchive table
        BlockArchiveData blockArchiveData = new BlockArchiveData(blockData);
        this.repository.getBlockArchiveRepository().save(blockArchiveData);
        this.repository.getBlockArchiveRepository().setBlockArchiveHeight(blockData.getHeight()+1);
        this.repository.saveChanges();
    }

    private void prune(int startHeight, int endHeight) throws DataException {
        this.repository.getBlockRepository().pruneBlocks(startHeight, endHeight);
        this.repository.getATRepository().pruneAtStates(startHeight, endHeight);
        this.repository.getATRepository().setAtPruneHeight(endHeight+1);
        this.repository.saveChanges();
    }

    private void trim(int startHeight, int endHeight) throws DataException {
        this.repository.getBlockRepository().trimOldOnlineAccountsSignatures(startHeight, endHeight);

        int count = 1; // Any number greater than 0
        while (count > 0) {
            count = this.repository.getATRepository().trimAtStates(startHeight, endHeight, Settings.getInstance().getAtStatesTrimLimit());
        }

        this.repository.getBlockRepository().setBlockPruneHeight(endHeight+1);
        this.repository.getATRepository().setAtTrimHeight(endHeight+1);
        this.repository.saveChanges();
    }

    private void runRepositoryMaintenance() throws DataException {
        try {
            this.repository.performPeriodicMaintenance(1000L);
        } catch (TimeoutException e) {
            LOGGER.info("Timed out waiting for repository before running maintenance");
        }
    }

}
