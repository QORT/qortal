package org.qortal.controller.repository;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.Controller;
import org.qortal.controller.Synchronizer;
import org.qortal.repository.*;
import org.qortal.settings.Settings;
import org.qortal.transform.TransformationException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;


public class BlockArchiveRebuilder {

    private static final Logger LOGGER = LogManager.getLogger(BlockArchiveRebuilder.class);

    private final int serializationVersion;

    public BlockArchiveRebuilder(int serializationVersion) {
        this.serializationVersion = serializationVersion;
    }

    public void start() throws DataException, IOException {
        if (!Settings.getInstance().isArchiveEnabled() || Settings.getInstance().isLite()) {
            return;
        }

        // New archive path is in a different location from original archive path, to avoid conflicts.
        // It will be moved later, once the process is complete.
        final Path newArchivePath = Paths.get(Settings.getInstance().getRepositoryPath(), "archive-rebuild");
        final Path originalArchivePath = Paths.get(Settings.getInstance().getRepositoryPath(), "archive");

        // Delete archive-rebuild if it exists from a previous attempt
        FileUtils.deleteDirectory(newArchivePath.toFile());

        try (final Repository repository = RepositoryManager.getRepository()) {
            int startHeight = 1; // We need to rebuild the entire archive

            LOGGER.info("Rebuilding block archive from height {}...", startHeight);

            while (!Controller.isStopping()) {
                repository.discardChanges();

                Thread.sleep(1000L);

                // Don't even attempt if we're mid-sync as our repository requests will be delayed for ages
                if (Synchronizer.getInstance().isSynchronizing()) {
                    continue;
                }

                // Rebuild archive
                try {
                    final int maximumArchiveHeight = BlockArchiveReader.getInstance().getHeightOfLastArchivedBlock();
                    if (startHeight >= maximumArchiveHeight) {
                        // We've finished.
                        // Delete existing archive and move the newly built one into its place
                        FileUtils.deleteDirectory(originalArchivePath.toFile());
                        FileUtils.moveDirectory(newArchivePath.toFile(), originalArchivePath.toFile());
                        BlockArchiveReader.getInstance().invalidateFileListCache();
                        LOGGER.info("Block archive successfully rebuilt");
                        return;
                    }

                    BlockArchiveWriter writer = new BlockArchiveWriter(startHeight, maximumArchiveHeight, serializationVersion, newArchivePath, repository);

                    // Set data source to BLOCK_ARCHIVE as we are rebuilding
                    writer.setDataSource(BlockArchiveWriter.BlockArchiveDataSource.BLOCK_ARCHIVE);

                    // We can't enforce the 100MB file size target, as the final file needs to contain all blocks
                    // that exist in the current archive. Otherwise, the final blocks in the archive will be lost.
                    writer.setShouldEnforceFileSizeTarget(false);

                    // We want to log the rebuild progress
                    writer.setShouldLogProgress(true);

                    BlockArchiveWriter.BlockArchiveWriteResult result = writer.write();
                    switch (result) {
                        case OK:
                            // Increment block archive height
                            startHeight += writer.getWrittenCount();
                            repository.saveChanges();
                            break;

                        case STOPPING:
                            return;

                        // We've reached the limit of the blocks we can archive
                        // Sleep for a while to allow more to become available
                        case NOT_ENOUGH_BLOCKS:
                            // This shouldn't happen, as we're not enforcing minimum file sizes
                            repository.discardChanges();
                            throw new DataException("Unable to rebuild archive due to unexpected NOT_ENOUGH_BLOCKS response.");

                        case BLOCK_NOT_FOUND:
                            // We tried to archive a block that didn't exist. This is a major failure and likely means
                            // that a bootstrap or re-sync is needed. Try again every minute until then.
                            LOGGER.info("Error: block not found when rebuilding archive. If this error persists, " +
                                    "a bootstrap or re-sync may be needed.");
                            repository.discardChanges();
                            throw new DataException("Unable to rebuild archive because a block is missing.");
                    }

                } catch (IOException | TransformationException e) {
                    LOGGER.info("Caught exception when rebuilding block archive", e);
                    throw new DataException("Unable to rebuild block archive");
                }

            }
        } catch (InterruptedException e) {
            // Do nothing
        } finally {
            // Delete archive-rebuild if it still exists, as that means something went wrong
            FileUtils.deleteDirectory(newArchivePath.toFile());
        }
    }

}
