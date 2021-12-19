package org.qortal.repository.hsqldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.Controller;
import org.qortal.gui.SplashFrame;
import org.qortal.repository.BlockArchiveWriter;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.transform.TransformationException;

import java.io.IOException;

/**
 *
 * When switching to an archiving node, we need to archive most of the database contents.
 * This involves copying its data into flat files.
 * If we do this entirely as a background process, it is very slow and can interfere with syncing.
 * However, if we take the approach of doing this in bulk, before starting up the rest of the
 * processes, this makes it much faster and less invasive.
 *
 * From that point, the original background archiving process will run, but can be dialled right down
 * so not to interfere with syncing.
 *
 */


public class HSQLDBDatabaseArchiving {

    private static final Logger LOGGER = LogManager.getLogger(HSQLDBDatabaseArchiving.class);


    public static boolean buildBlockArchive(Repository repository, long fileSizeTarget) throws DataException {

        // Only build the archive if we haven't already got one that is up to date
        boolean upToDate = BlockArchiveWriter.isArchiverUpToDate(repository);
        if (upToDate) {
            // Already archived
            return false;
        }

        LOGGER.info("Building block archive - this process could take a while... (approx. 15 mins on high spec)");
        SplashFrame.getInstance().updateStatus("Building block archive (takes 60+ mins)...");

        final int maximumArchiveHeight = BlockArchiveWriter.getMaxArchiveHeight(repository);
        int startHeight = 0;

        while (!Controller.isStopping()) {
            try {
                BlockArchiveWriter writer = new BlockArchiveWriter(startHeight, maximumArchiveHeight, repository);
                writer.setFileSizeTarget(fileSizeTarget);
                BlockArchiveWriter.BlockArchiveWriteResult result = writer.write();
                switch (result) {
                    case OK:
                        // Increment block archive height
                        startHeight = writer.getLastWrittenHeight() + 1;
                        repository.getBlockArchiveRepository().setBlockArchiveHeight(startHeight);
                        repository.saveChanges();
                        break;

                    case STOPPING:
                        return false;

                    case NOT_ENOUGH_BLOCKS:
                        // We've reached the limit of the blocks we can archive
                        // Return from the whole method
                        return true;

                    case BLOCK_NOT_FOUND:
                        // We tried to archive a block that didn't exist. This is a major failure and likely means
                        // that a bootstrap or re-sync is needed. Return rom the method
                        LOGGER.info("Error: block not found when building archive. If this error persists, " +
                                "a bootstrap or re-sync may be needed.");
                        return false;
                }

            } catch (IOException | TransformationException | InterruptedException e) {
                LOGGER.info("Caught exception when creating block cache", e);
                return false;
            }
        }

        // If we got this far then something went wrong (most likely the app is stopping)
        return false;
    }

}
