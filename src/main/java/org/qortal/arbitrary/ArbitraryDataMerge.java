package org.qortal.arbitrary;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.arbitrary.ArbitraryDataDiff.DiffType;
import org.qortal.arbitrary.ArbitraryDataDiff.ModifiedPath;
import org.qortal.arbitrary.metadata.ArbitraryDataMetadataPatch;
import org.qortal.arbitrary.patch.UnifiedDiffPatch;
import org.qortal.repository.DataException;
import org.qortal.settings.Settings;
import org.qortal.utils.FilesystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

public class ArbitraryDataMerge {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataMerge.class);

    private final Path pathBefore;
    private final Path pathAfter;
    private Path mergePath;
    private String identifier;
    private ArbitraryDataMetadataPatch metadata;

    public ArbitraryDataMerge(Path pathBefore, Path pathAfter) {
        this.pathBefore = pathBefore;
        this.pathAfter = pathAfter;
    }

    public void compute() throws IOException, DataException {
        try {
            this.preExecute();
            this.copyPreviousStateToMergePath();
            this.loadMetadata();
            this.applyDifferences();
            this.copyMetadata();

        } finally {
            this.postExecute();
        }
    }

    private void preExecute() throws DataException {
        this.createRandomIdentifier();
        this.createOutputDirectory();
    }

    private void postExecute() {

    }

    private void createRandomIdentifier() {
        this.identifier = UUID.randomUUID().toString();
    }

    private void createOutputDirectory() throws DataException {
        // Use the user-specified temp dir, as it is deterministic, and is more likely to be located on reusable storage hardware
        String baseDir = Settings.getInstance().getTempDataPath();
        Path tempDir = Paths.get(baseDir, "merge", this.identifier);
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new DataException("Unable to create temp directory");
        }
        this.mergePath = tempDir;
    }

    private void copyPreviousStateToMergePath() throws IOException {
        ArbitraryDataMerge.copyDirPathToBaseDir(this.pathBefore, this.mergePath, Paths.get(""));
    }

    private void loadMetadata() throws IOException, DataException {
        this.metadata = new ArbitraryDataMetadataPatch(this.pathAfter);
        this.metadata.read();
    }

    private void applyDifferences() throws IOException, DataException {

        List<Path> addedPaths = this.metadata.getAddedPaths();
        for (Path path : addedPaths) {
            LOGGER.trace("File was added: {}", path.toString());
            Path filePath = Paths.get(this.pathAfter.toString(), path.toString());
            ArbitraryDataMerge.copyPathToBaseDir(filePath, this.mergePath, path);
        }

        List<ModifiedPath> modifiedPaths = this.metadata.getModifiedPaths();
        for (ModifiedPath modifiedPath : modifiedPaths) {
            LOGGER.trace("File was modified: {}", modifiedPath.toString());
            this.applyPatch(modifiedPath);
        }

        List<Path> removedPaths = this.metadata.getRemovedPaths();
        for (Path path : removedPaths) {
            LOGGER.trace("File was removed: {}", path.toString());
            ArbitraryDataMerge.deletePathInBaseDir(this.mergePath, path);
        }
    }

    private void applyPatch(ModifiedPath modifiedPath) throws IOException, DataException {
        if (modifiedPath.getDiffType() == DiffType.UNIFIED_DIFF) {
            // Create destination file from patch
            UnifiedDiffPatch unifiedDiffPatch = new UnifiedDiffPatch(pathBefore, pathAfter, mergePath);
            unifiedDiffPatch.apply(modifiedPath.getPath());
        }
        else if (modifiedPath.getDiffType() == DiffType.COMPLETE_FILE) {
            // Copy complete file
            Path filePath = Paths.get(this.pathAfter.toString(), modifiedPath.getPath().toString());
            ArbitraryDataMerge.copyPathToBaseDir(filePath, this.mergePath, modifiedPath.getPath());
        }
        else {
            throw new DataException(String.format("Unrecognized patch diff type: %s", modifiedPath.getDiffType()));
        }
    }

    private void copyMetadata() throws IOException {
        Path filePath = Paths.get(this.pathAfter.toString(), ".qortal");
        ArbitraryDataMerge.copyPathToBaseDir(filePath, this.mergePath, Paths.get(".qortal"));
    }


    private static void copyPathToBaseDir(Path source, Path base, Path relativePath) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException(String.format("File not found: %s", source.toString()));
        }

        File sourceFile = source.toFile();
        Path dest = Paths.get(base.toString(), relativePath.toString());
        LOGGER.trace("Copying {} to {}", source, dest);

        if (sourceFile.isFile()) {
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        else if (sourceFile.isDirectory()) {
            FilesystemUtils.copyAndReplaceDirectory(source.toString(), dest.toString());
        }
        else {
            throw new IOException(String.format("Invalid file: %s", source.toString()));
        }
    }

    private static void copyDirPathToBaseDir(Path source, Path base, Path relativePath) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException(String.format("File not found: %s", source.toString()));
        }

        Path dest = Paths.get(base.toString(), relativePath.toString());
        LOGGER.trace("Copying {} to {}", source, dest);
        FilesystemUtils.copyAndReplaceDirectory(source.toString(), dest.toString());
    }

    private static void deletePathInBaseDir(Path base, Path relativePath) throws IOException {
        Path dest = Paths.get(base.toString(), relativePath.toString());
        File file = new File(dest.toString());
        if (file.exists() && file.isFile()) {
            if (FilesystemUtils.pathInsideDataOrTempPath(dest)) {
                LOGGER.trace("Deleting file {}", dest);
                Files.delete(dest);
            }
        }
        if (file.exists() && file.isDirectory()) {
            if (FilesystemUtils.pathInsideDataOrTempPath(dest)) {
                LOGGER.trace("Deleting directory {}", dest);
                FileUtils.deleteDirectory(file);
            }
        }
    }

    public Path getMergePath() {
        return this.mergePath;
    }

}
