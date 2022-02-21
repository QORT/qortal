package org.qortal.repository.hsqldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.Controller;
import org.qortal.data.block.BlockData;
import org.qortal.gui.SplashFrame;
import org.qortal.repository.BlockArchiveWriter;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.settings.Settings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

/**
 *
 * When switching from a full node to a pruning node, we need to delete most of the database contents.
 * If we do this entirely as a background process, it is very slow and can interfere with syncing.
 * However, if we take the approach of transferring only the necessary rows to a new table and then
 * deleting the original table, this makes the process much faster. It was taking several days to
 * delete the AT states in the background, but only a couple of minutes to copy them to a new table.
 *
 * The trade off is that we have to go through a form of "reshape" when starting the app for the first
 * time after enabling pruning mode. But given that this is an opt-in mode, I don't think it will be
 * a problem.
 *
 * Once the pruning is complete, it automatically performs a CHECKPOINT DEFRAG in order to
 * shrink the database file size down to a fraction of what it was before.
 *
 * From this point, the original background process will run, but can be dialled right down so not
 * to interfere with syncing.
 *
 */


public class HSQLDBDatabasePruning {

    private static final Logger LOGGER = LogManager.getLogger(HSQLDBDatabasePruning.class);


    public static boolean pruneATStates(HSQLDBRepository repository) throws SQLException, DataException {

        // Only bulk prune AT states if we have never done so before
        int pruneHeight = repository.getATRepository().getAtPruneHeight();
        if (pruneHeight > 0) {
            // Already pruned AT states
            return false;
        }

        if (Settings.getInstance().isArchiveEnabled()) {
            // Only proceed if we can see that the archiver has already finished
            // This way, if the archiver failed for any reason, we can prune once it has had
            // some opportunities to try again
            boolean upToDate = BlockArchiveWriter.isArchiverUpToDate(repository);
            if (!upToDate) {
                return false;
            }
        }

        LOGGER.info("Starting bulk prune of AT states - this process could take a while... " +
                "(approx. 2 mins on high spec, or upwards of 30 mins in some cases)");
        SplashFrame.getInstance().updateStatus("Pruning database (takes up to 30 mins)...");

        // Create new AT-states table to hold smaller dataset
        repository.executeCheckedUpdate("DROP TABLE IF EXISTS ATStatesNew");
        repository.executeCheckedUpdate("CREATE TABLE ATStatesNew ("
                + "AT_address QortalAddress, height INTEGER NOT NULL, state_hash ATStateHash NOT NULL, "
                + "fees QortalAmount NOT NULL, is_initial BOOLEAN NOT NULL, sleep_until_message_timestamp BIGINT, "
                + "PRIMARY KEY (AT_address, height), "
                + "FOREIGN KEY (AT_address) REFERENCES ATs (AT_address) ON DELETE CASCADE)");
        repository.executeCheckedUpdate("SET TABLE ATStatesNew NEW SPACE");
        repository.executeCheckedUpdate("CHECKPOINT");

        // Add a height index
        LOGGER.info("Adding index to AT states table...");
        repository.executeCheckedUpdate("CREATE INDEX IF NOT EXISTS ATStatesNewHeightIndex ON ATStatesNew (height)");
        repository.executeCheckedUpdate("CHECKPOINT");


        // Find our latest block
        BlockData latestBlock = repository.getBlockRepository().getLastBlock();
        if (latestBlock == null) {
            LOGGER.info("Unable to determine blockchain height, necessary for bulk block pruning");
            return false;
        }

        // Calculate some constants for later use
        final int blockchainHeight = latestBlock.getHeight();
        int maximumBlockToTrim = blockchainHeight - Settings.getInstance().getPruneBlockLimit();
        if (Settings.getInstance().isArchiveEnabled()) {
            // Archive mode - don't prune anything that hasn't been archived yet
            maximumBlockToTrim = Math.min(maximumBlockToTrim, repository.getBlockArchiveRepository().getBlockArchiveHeight() - 1);
        }
        final int endHeight = blockchainHeight;
        final int blockStep = 10000;


        // It's essential that we rebuild the latest AT states here, as we are using this data in the next query.
        // Failing to do this will result in important AT states being deleted, rendering the database unusable.
        repository.getATRepository().rebuildLatestAtStates();


        // Loop through all the LatestATStates and copy them to the new table
        LOGGER.info("Copying AT states...");
        for (int height = 0; height < endHeight; height += blockStep) {
            final int batchEndHeight = height + blockStep - 1;
            //LOGGER.info(String.format("Copying AT states between %d and %d...", height, batchEndHeight));

            String sql = "SELECT height, AT_address FROM LatestATStates WHERE height BETWEEN ? AND ?";
            try (ResultSet latestAtStatesResultSet = repository.checkedExecute(sql, height, batchEndHeight)) {
                if (latestAtStatesResultSet != null) {
                    do {
                        int latestAtHeight = latestAtStatesResultSet.getInt(1);
                        String latestAtAddress = latestAtStatesResultSet.getString(2);

                        // Copy this latest ATState to the new table
                        //LOGGER.info(String.format("Copying AT %s at height %d...", latestAtAddress, latestAtHeight));
                        try {
                            String updateSql = "INSERT INTO ATStatesNew ("
                                    + "SELECT AT_address, height, state_hash, fees, is_initial, sleep_until_message_timestamp "
                                    + "FROM ATStates "
                                    + "WHERE height = ? AND AT_address = ?)";
                            repository.executeCheckedUpdate(updateSql, latestAtHeight, latestAtAddress);
                        } catch (SQLException e) {
                            repository.examineException(e);
                            throw new DataException("Unable to copy ATStates", e);
                        }

                        // If this batch includes blocks after the maximum block to trim, we will need to copy
                        // each of its AT states above maximumBlockToTrim as they are considered "recent". We
                        // need to do this for _all_ AT states in these blocks, regardless of their latest state.
                        if (batchEndHeight >= maximumBlockToTrim) {
                            // Now copy this AT's states for each recent block they are present in
                            for (int i = maximumBlockToTrim; i < endHeight; i++) {
                                if (latestAtHeight < i) {
                                    // This AT finished before this block so there is nothing to copy
                                    continue;
                                }

                                //LOGGER.info(String.format("Copying recent AT %s at height %d...", latestAtAddress, i));
                                try {
                                    // Copy each LatestATState to the new table
                                    String updateSql = "INSERT IGNORE INTO ATStatesNew ("
                                            + "SELECT AT_address, height, state_hash, fees, is_initial, sleep_until_message_timestamp "
                                            + "FROM ATStates "
                                            + "WHERE height = ? AND AT_address = ?)";
                                    repository.executeCheckedUpdate(updateSql, i, latestAtAddress);
                                } catch (SQLException e) {
                                    repository.examineException(e);
                                    throw new DataException("Unable to copy ATStates", e);
                                }
                            }
                        }
                        repository.saveChanges();

                    } while (latestAtStatesResultSet.next());
                }
            } catch (SQLException e) {
                throw new DataException("Unable to copy AT states", e);
            }
        }


        // Finally, drop the original table and rename
        LOGGER.info("Deleting old AT states...");
        repository.executeCheckedUpdate("DROP TABLE ATStates");
        repository.executeCheckedUpdate("ALTER TABLE ATStatesNew RENAME TO ATStates");
        repository.executeCheckedUpdate("ALTER INDEX ATStatesNewHeightIndex RENAME TO ATStatesHeightIndex");
        repository.executeCheckedUpdate("CHECKPOINT");

        // Update the prune height
        int nextPruneHeight = maximumBlockToTrim + 1;
        repository.getATRepository().setAtPruneHeight(nextPruneHeight);
        repository.saveChanges();

        repository.executeCheckedUpdate("CHECKPOINT");

        // Now prune/trim the ATStatesData, as this currently goes back over a month
        return HSQLDBDatabasePruning.pruneATStateData(repository);
    }

    /*
     * Bulk prune ATStatesData to catch up with the now pruned ATStates table
     * This uses the existing AT States trimming code but with a much higher end block
     */
    private static boolean pruneATStateData(Repository repository) throws DataException {

        if (Settings.getInstance().isArchiveEnabled()) {
            // Don't prune ATStatesData in archive mode
            return true;
        }

        BlockData latestBlock = repository.getBlockRepository().getLastBlock();
        if (latestBlock == null) {
            LOGGER.info("Unable to determine blockchain height, necessary for bulk ATStatesData pruning");
            return false;
        }
        final int blockchainHeight = latestBlock.getHeight();
        int upperPrunableHeight = blockchainHeight - Settings.getInstance().getPruneBlockLimit();
        // ATStateData is already trimmed - so carry on from where we left off in the past
        int pruneStartHeight = repository.getATRepository().getAtTrimHeight();

        LOGGER.info("Starting bulk prune of AT states data - this process could take a while... (approx. 3 mins on high spec)");

        while (pruneStartHeight < upperPrunableHeight) {
            // Prune all AT state data up until our latest minus pruneBlockLimit (or our archive height)

            if (Controller.isStopping()) {
                return false;
            }

            // Override batch size in the settings because this is a one-off process
            final int batchSize = 1000;
            final int rowLimitPerBatch = 50000;
            int upperBatchHeight = pruneStartHeight + batchSize;
            int upperPruneHeight = Math.min(upperBatchHeight, upperPrunableHeight);

            LOGGER.trace(String.format("Pruning AT states data between %d and %d...", pruneStartHeight, upperPruneHeight));

            int numATStatesPruned = repository.getATRepository().trimAtStates(pruneStartHeight, upperPruneHeight, rowLimitPerBatch);
            repository.saveChanges();

            if (numATStatesPruned > 0) {
                LOGGER.trace(String.format("Pruned %d AT states data rows between blocks %d and %d",
                        numATStatesPruned, pruneStartHeight, upperPruneHeight));
            } else {
                repository.getATRepository().setAtTrimHeight(upperBatchHeight);
                // No need to rebuild the latest AT states as we aren't currently synchronizing
                repository.saveChanges();
                LOGGER.debug(String.format("Bumping AT states trim height to %d", upperBatchHeight));

                // Can we move onto next batch?
                if (upperPrunableHeight > upperBatchHeight) {
                    pruneStartHeight = upperBatchHeight;
                }
                else {
                    // We've finished pruning
                    break;
                }
            }
        }

        return true;
    }

    public static boolean pruneBlocks(Repository repository) throws SQLException, DataException {

        // Only bulk prune AT states if we have never done so before
        int pruneHeight = repository.getBlockRepository().getBlockPruneHeight();
        if (pruneHeight > 0) {
            // Already pruned blocks
            return false;
        }

        if (Settings.getInstance().isArchiveEnabled()) {
            // Only proceed if we can see that the archiver has already finished
            // This way, if the archiver failed for any reason, we can prune once it has had
            // some opportunities to try again
            boolean upToDate = BlockArchiveWriter.isArchiverUpToDate(repository);
            if (!upToDate) {
                return false;
            }
        }

        BlockData latestBlock = repository.getBlockRepository().getLastBlock();
        if (latestBlock == null) {
            LOGGER.info("Unable to determine blockchain height, necessary for bulk block pruning");
            return false;
        }
        final int blockchainHeight = latestBlock.getHeight();
        int upperPrunableHeight = blockchainHeight - Settings.getInstance().getPruneBlockLimit();
        int pruneStartHeight = 0;

        if (Settings.getInstance().isArchiveEnabled()) {
            // Archive mode - don't prune anything that hasn't been archived yet
            upperPrunableHeight = Math.min(upperPrunableHeight, repository.getBlockArchiveRepository().getBlockArchiveHeight() - 1);
        }

        LOGGER.info("Starting bulk prune of blocks - this process could take a while... (approx. 5 mins on high spec)");

        while (pruneStartHeight < upperPrunableHeight) {
            // Prune all blocks up until our latest minus pruneBlockLimit

            int upperBatchHeight = pruneStartHeight + Settings.getInstance().getBlockPruneBatchSize();
            int upperPruneHeight = Math.min(upperBatchHeight, upperPrunableHeight);

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
                LOGGER.debug(String.format("Bumping block base prune height to %d", nextPruneHeight));

                // Can we move onto next batch?
                if (upperPrunableHeight > nextPruneHeight) {
                    pruneStartHeight = nextPruneHeight;
                }
                else {
                    // We've finished pruning
                    break;
                }
            }
        }

        return true;
    }

    public static void performMaintenance(Repository repository) throws SQLException, DataException {
        try {
            SplashFrame.getInstance().updateStatus("Performing maintenance...");

            // Timeout if the database isn't ready for backing up after 5 minutes
            // Nothing else should be using the db at this point, so a timeout shouldn't happen
            long timeout = 5 * 60 * 1000L;
            repository.performPeriodicMaintenance(timeout);

        } catch (TimeoutException e) {
            LOGGER.info("Attempt to perform maintenance failed due to timeout: {}", e.getMessage());
        }
    }

}
