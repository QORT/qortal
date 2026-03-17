package org.qortal.arbitrary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.arbitrary.metadata.ArbitraryDataMetadataPatch;
import org.qortal.repository.DataException;
import org.qortal.settings.Settings;
import org.qortal.utils.FilesystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class ArbitraryDataCreatePatch {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataCreatePatch.class);

    private final Path pathBefore;
    private Path pathAfter;
    private final byte[] previousSignature;

    private Path finalPath;
    private int totalFileCount;
    private int fileDifferencesCount;
    private ArbitraryDataMetadataPatch metadata;

    private Path workingPath;
    private String identifier;

    public ArbitraryDataCreatePatch(Path pathBefore, Path pathAfter, byte[] previousSignature) {
        this.pathBefore = pathBefore;
        this.pathAfter = pathAfter;
        this.previousSignature = previousSignature;
    }

    public void create() throws DataException, IOException {
        try {
            this.preExecute();
            this.copyFiles();
            this.process();

        } catch (Exception e) {
            this.cleanupOnFailure();
            throw e;

        } finally {
            this.postExecute();
        }
    }

    private void preExecute() throws DataException {
        if (this.pathBefore == null || this.pathAfter == null) {
            throw new DataException("No paths available to build patch");
        }
        if (!Files.exists(this.pathBefore) || !Files.exists(this.pathAfter)) {
            throw new DataException("Unable to create patch because at least one path doesn't exist");
        }

        this.createRandomIdentifier();
        this.createWorkingDirectory();
    }

    private void postExecute() {
        this.cleanupWorkingPath();
    }

    private void cleanupWorkingPath() {
        try {
            FilesystemUtils.safeDeleteDirectory(this.workingPath, true);
        } catch (IOException e) {
            LOGGER.debug("Unable to cleanup working directory");
        }
    }

    private void cleanupOnFailure() {
        try {
            FilesystemUtils.safeDeleteDirectory(this.finalPath, true);
        } catch (IOException e) {
            LOGGER.debug("Unable to cleanup diff directory on failure");
        }
    }

    private void createRandomIdentifier() {
        this.identifier = UUID.randomUUID().toString();
    }

    private void createWorkingDirectory() throws DataException {
        // Use the user-specified temp dir, as it is deterministic, and is more likely to be located on reusable storage hardware
        String baseDir = Settings.getInstance().getTempDataPath();
        Path tempDir = Paths.get(baseDir, "patch", this.identifier);
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new DataException("Unable to create temp directory");
        }
        this.workingPath = tempDir;
    }

    private void copyFiles() throws IOException {
        // When dealing with single files, we need to copy them to a container directory
        // in order for the structure to align with the previous revision and therefore
        // make comparisons possible.

        if (this.pathAfter.toFile().isFile()) {
            // Create a "data" directory within the working directory
            Path workingDataPath = Paths.get(this.workingPath.toString(), "data");
            Files.createDirectories(workingDataPath);
            // Copy to temp directory
            // Filename is currently hardcoded to "data"
            String filename = "data"; //this.pathAfter.getFileName().toString();
            Files.copy(this.pathAfter, Paths.get(workingDataPath.toString(), filename));
            // Update pathAfter to point to the new path
            this.pathAfter = workingDataPath;
        }
    }

    private void process() throws IOException, DataException {

        ArbitraryDataDiff diff = new ArbitraryDataDiff(this.pathBefore, this.pathAfter, this.previousSignature);
        this.finalPath = diff.getDiffPath();
        diff.compute();

        this.totalFileCount = diff.getTotalFileCount();
        this.metadata = diff.getMetadata();
    }

    public Path getFinalPath() {
        return this.finalPath;
    }

    public int getTotalFileCount() {
        return this.totalFileCount;
    }

    public ArbitraryDataMetadataPatch getMetadata() {
        return this.metadata;
    }

}
