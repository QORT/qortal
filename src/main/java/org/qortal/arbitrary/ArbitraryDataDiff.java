package org.qortal.arbitrary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.qortal.arbitrary.metadata.ArbitraryDataMetadataPatch;
import org.qortal.arbitrary.patch.UnifiedDiffPatch;
import org.qortal.crypto.Crypto;
import org.qortal.repository.DataException;
import org.qortal.settings.Settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


public class ArbitraryDataDiff {

    /** Only create a patch if both the before and after file sizes are within defined limit **/
    private static final long MAX_DIFF_FILE_SIZE = 100 * 1024L; // 100kiB


    public enum DiffType {
        COMPLETE_FILE,
        UNIFIED_DIFF
    }

    public static class ModifiedPath {
        private Path path;
        private DiffType diffType;

        public ModifiedPath(Path path, DiffType diffType) {
            this.path = path;
            this.diffType = diffType;
        }

        public ModifiedPath(JSONObject jsonObject) {
            String pathString = jsonObject.getString("path");
            if (pathString != null) {
                this.path = Paths.get(pathString);
            }

            String diffTypeString = jsonObject.getString("type");
            if (diffTypeString != null) {
                this.diffType = DiffType.valueOf(diffTypeString);
            }
        }

        public Path getPath() {
            return this.path;
        }

        public DiffType getDiffType() {
            return this.diffType;
        }

        public String toString() {
            return this.path.toString();
        }
    }

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataDiff.class);

    private final Path pathBefore;
    private final Path pathAfter;
    private final byte[] previousSignature;
    private byte[] previousHash;
    private byte[] currentHash;
    private Path diffPath;
    private String identifier;

    private final List<Path> addedPaths;
    private final List<ModifiedPath> modifiedPaths;
    private final List<Path> removedPaths;

    private int totalFileCount;
    private ArbitraryDataMetadataPatch metadata;

    public ArbitraryDataDiff(Path pathBefore, Path pathAfter, byte[] previousSignature) throws DataException {
        this.pathBefore = pathBefore;
        this.pathAfter = pathAfter;
        this.previousSignature = previousSignature;

        this.addedPaths = new ArrayList<>();
        this.modifiedPaths = new ArrayList<>();
        this.removedPaths = new ArrayList<>();

        this.createRandomIdentifier();
        this.createOutputDirectory();
    }

    public void compute() throws IOException, DataException {
        try {
            this.preExecute();
            this.hashPreviousState();
            this.findAddedOrModifiedFiles();
            this.findRemovedFiles();
            this.validate();
            this.hashCurrentState();
            this.writeMetadata();

        } finally {
            this.postExecute();
        }
    }

    private void preExecute() {
        LOGGER.debug("Generating diff...");
    }

    private void postExecute() {

    }

    private void createRandomIdentifier() {
        this.identifier = UUID.randomUUID().toString();
    }

    private void createOutputDirectory() throws DataException {
        // Use the user-specified temp dir, as it is deterministic, and is more likely to be located on reusable storage hardware
        String baseDir = Settings.getInstance().getTempDataPath();
        Path tempDir = Paths.get(baseDir, "diff", this.identifier);
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new DataException("Unable to create temp directory");
        }
        this.diffPath = tempDir;
    }

    private void hashPreviousState() throws IOException, DataException {
        ArbitraryDataDigest digest = new ArbitraryDataDigest(this.pathBefore);
        digest.compute();
        this.previousHash = digest.getHash();
    }

    private void findAddedOrModifiedFiles() throws IOException {
        try {
            final Path pathBeforeAbsolute = this.pathBefore.toAbsolutePath();
            final Path pathAfterAbsolute = this.pathAfter.toAbsolutePath();
            final Path diffPathAbsolute = this.diffPath.toAbsolutePath();
            final ArbitraryDataDiff diff = this;

            // Check for additions or modifications
            Files.walkFileTree(this.pathAfter, new FileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path after, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path afterPathAbsolute, BasicFileAttributes attrs) throws IOException {
                    Path afterPathRelative = pathAfterAbsolute.relativize(afterPathAbsolute.toAbsolutePath());
                    Path beforePathAbsolute = pathBeforeAbsolute.resolve(afterPathRelative);

                    if (afterPathRelative.startsWith(".qortal")) {
                        // Ignore the .qortal metadata folder
                        return FileVisitResult.CONTINUE;
                    }

                    boolean wasAdded = false;
                    boolean wasModified = false;

                    if (!Files.exists(beforePathAbsolute)) {
                        LOGGER.trace("File was added: {}", afterPathRelative.toString());
                        diff.addedPaths.add(afterPathRelative);
                        wasAdded = true;
                    }
                    else if (Files.size(afterPathAbsolute) != Files.size(beforePathAbsolute)) {
                        // Check file size first because it's quicker
                        LOGGER.trace("File size was modified: {}", afterPathRelative.toString());
                        wasModified = true;
                    }
                    else if (!Arrays.equals(ArbitraryDataDiff.digestFromPath(afterPathAbsolute), ArbitraryDataDiff.digestFromPath(beforePathAbsolute))) {
                        // Check hashes as a last resort
                        LOGGER.trace("File contents were modified: {}", afterPathRelative.toString());
                        wasModified = true;
                    }

                    if (wasAdded) {
                        diff.copyFilePathToBaseDir(afterPathAbsolute, diffPathAbsolute, afterPathRelative);
                    }
                    if (wasModified) {
                        try {
                            diff.pathModified(beforePathAbsolute, afterPathAbsolute, afterPathRelative, diffPathAbsolute);
                        } catch (DataException e) {
                            // We can only throw IOExceptions because we are overriding FileVisitor.visitFile()
                            throw new IOException(e);
                        }
                    }

                    // Keep a tally of the total number of files to help with decision making
                    diff.totalFileCount++;

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e){
                    LOGGER.info("File visit failed: {}, error: {}", file.toString(), e.getMessage());
                    // TODO: throw exception?
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    return FileVisitResult.CONTINUE;
                }

            });
        } catch (IOException e) {
            LOGGER.info("IOException when walking through file tree: {}", e.getMessage());
            throw(e);
        }
    }

    private void findRemovedFiles() throws IOException {
        try {
            final Path pathBeforeAbsolute = this.pathBefore.toAbsolutePath();
            final Path pathAfterAbsolute = this.pathAfter.toAbsolutePath();
            final ArbitraryDataDiff diff = this;

            // Check for removals
            Files.walkFileTree(this.pathBefore, new FileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path before, BasicFileAttributes attrs) {
                    Path directoryPathBefore = pathBeforeAbsolute.relativize(before.toAbsolutePath());
                    Path directoryPathAfter = pathAfterAbsolute.resolve(directoryPathBefore);

                    if (directoryPathBefore.startsWith(".qortal")) {
                        // Ignore the .qortal metadata folder
                        return FileVisitResult.CONTINUE;
                    }

                    if (!Files.exists(directoryPathAfter)) {
                        LOGGER.trace("Directory was removed: {}", directoryPathAfter.toString());
                        diff.removedPaths.add(directoryPathBefore);
                        // TODO: we might need to mark directories differently to files
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path before, BasicFileAttributes attrs) {
                    Path filePathBefore = pathBeforeAbsolute.relativize(before.toAbsolutePath());
                    Path filePathAfter = pathAfterAbsolute.resolve(filePathBefore);

                    if (filePathBefore.startsWith(".qortal")) {
                        // Ignore the .qortal metadata folder
                        return FileVisitResult.CONTINUE;
                    }

                    if (!Files.exists(filePathAfter)) {
                        LOGGER.trace("File was removed: {}", filePathBefore.toString());
                        diff.removedPaths.add(filePathBefore);
                    }

                    // Keep a tally of the total number of files to help with decision making
                    diff.totalFileCount++;

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e){
                    LOGGER.info("File visit failed: {}, error: {}", file.toString(), e.getMessage());
                    // TODO: throw exception?
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    return FileVisitResult.CONTINUE;
                }

            });
        } catch (IOException e) {
            throw new IOException(String.format("IOException when walking through file tree: %s", e.getMessage()));
        }
    }

    private void validate() throws DataException {
        if (this.addedPaths.isEmpty() && this.modifiedPaths.isEmpty() && this.removedPaths.isEmpty()) {
            throw new DataException("Current state matches previous state. Nothing to do.");
        }
    }

    private void hashCurrentState() throws IOException, DataException {
        ArbitraryDataDigest digest = new ArbitraryDataDigest(this.pathAfter);
        digest.compute();
        this.currentHash = digest.getHash();
    }

    private void writeMetadata() throws IOException, DataException {
        ArbitraryDataMetadataPatch metadata = new ArbitraryDataMetadataPatch(this.diffPath);
        metadata.setAddedPaths(this.addedPaths);
        metadata.setModifiedPaths(this.modifiedPaths);
        metadata.setRemovedPaths(this.removedPaths);
        metadata.setPreviousSignature(this.previousSignature);
        metadata.setPreviousHash(this.previousHash);
        metadata.setCurrentHash(this.currentHash);
        metadata.write();
        this.metadata = metadata;
    }


    private void pathModified(Path beforePathAbsolute, Path afterPathAbsolute, Path afterPathRelative,
                              Path destinationBasePathAbsolute) throws IOException, DataException {

        Path destination = Paths.get(destinationBasePathAbsolute.toString(), afterPathRelative.toString());
        long beforeSize = Files.size(beforePathAbsolute);
        long afterSize = Files.size(afterPathAbsolute);
        DiffType diffType;

        if (beforeSize > MAX_DIFF_FILE_SIZE || afterSize > MAX_DIFF_FILE_SIZE) {
            // Files are large, so don't attempt a diff
            this.copyFilePathToBaseDir(afterPathAbsolute, destinationBasePathAbsolute, afterPathRelative);
            diffType = DiffType.COMPLETE_FILE;
        }
        else {
            // Attempt to create patch using java-diff-utils
            UnifiedDiffPatch unifiedDiffPatch = new UnifiedDiffPatch(beforePathAbsolute, afterPathAbsolute, destination);
            unifiedDiffPatch.create();
            if (unifiedDiffPatch.isValid()) {
                diffType = DiffType.UNIFIED_DIFF;
            }
            else {
                // Diff failed validation, so copy the whole file instead
                this.copyFilePathToBaseDir(afterPathAbsolute, destinationBasePathAbsolute, afterPathRelative);
                diffType = DiffType.COMPLETE_FILE;
            }
        }

        ModifiedPath modifiedPath = new ModifiedPath(afterPathRelative, diffType);
        this.modifiedPaths.add(modifiedPath);
    }

    private void copyFilePathToBaseDir(Path source, Path base, Path relativePath) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException(String.format("File not found: %s", source.toString()));
        }

        // Ensure parent folders exist in the destination
        Path dest = Paths.get(base.toString(), relativePath.toString());
        File file = new File(dest.toString());
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        LOGGER.trace("Copying {} to {}", source, dest);
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
    }
    

    public Path getDiffPath() {
        return this.diffPath;
    }

    public int getTotalFileCount() {
        return this.totalFileCount;
    }

    public ArbitraryDataMetadataPatch getMetadata() {
        return this.metadata;
    }


    // Utils

    private static byte[] digestFromPath(Path path) {
        try {
            return Crypto.digest(path.toFile());
        } catch (IOException e) {
            return null;
        }
    }

}
