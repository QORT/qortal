package org.qortal.arbitrary.patch;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.crypto.Crypto;
import org.qortal.repository.DataException;
import org.qortal.settings.Settings;
import org.qortal.utils.FilesystemUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class UnifiedDiffPatch {

    private static final Logger LOGGER = LogManager.getLogger(UnifiedDiffPatch.class);

    private final Path before;
    private final Path after;
    private final Path destination;

    private String identifier;
    private Path validationPath;

    public UnifiedDiffPatch(Path before, Path after, Path destination) {
        this.before = before;
        this.after = after;
        this.destination = destination;
    }

    /**
     * Create a patch based on the differences in path "after"
     * compared with base path "before", outputting the patch
     * to the "destination" path.
     *
     * @throws IOException
     */
    public void create() throws IOException {
        if (!Files.exists(before)) {
            throw new IOException(String.format("File not found (before): %s", before.toString()));
        }
        if (!Files.exists(after)) {
            throw new IOException(String.format("File not found (after): %s", after.toString()));
        }

        // Ensure parent folders exist in the destination
        File file = new File(destination.toString());
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        // Delete an existing file if it exists
        File destFile = destination.toFile();
        if (destFile.exists() && destFile.isFile()) {
            Files.delete(destination);
        }

        // Load the two files into memory
        List<String> original = FileUtils.readLines(before.toFile(), StandardCharsets.UTF_8);
        List<String> revised = FileUtils.readLines(after.toFile(), StandardCharsets.UTF_8);

        // Check if the original file ends with a newline
        boolean endsWithNewline = FilesystemUtils.fileEndsWithNewline(before);

        // Generate diff information
        Patch<String> diff = DiffUtils.diff(original, revised);

        // Generate unified diff format
        String originalFileName = before.getFileName().toString();
        String revisedFileName = after.getFileName().toString();
        List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(originalFileName, revisedFileName, original, diff, 0);

        // Write the diff to the destination directory
        FileWriter fileWriter = new FileWriter(destination.toString(), true);
        BufferedWriter writer = new BufferedWriter(fileWriter);
        for (int i=0; i<unifiedDiff.size(); i++) {
            String line = unifiedDiff.get(i);
            writer.append(line);
            // Add a newline if this isn't the last line, or the original ended with a newline
            if (i < unifiedDiff.size()-1 || endsWithNewline) {
                writer.newLine();
            }
        }
        writer.flush();
        writer.close();
    }

    /**
     * Validate the patch at the "destination" path to ensure
     * it works correctly and is smaller than the original file
     *
     * @return true if valid, false if invalid
     */
    public boolean isValid() throws DataException {
        this.createRandomIdentifier();
        this.createTempValidationDirectory();

        // Merge the patch with the original path
        Path tempPath = Paths.get(this.validationPath.toString(), this.identifier);

        try {
            UnifiedDiffPatch unifiedDiffPatch = new UnifiedDiffPatch(before, destination, tempPath);
            unifiedDiffPatch.apply(null);

            byte[] inputDigest = Crypto.digest(after.toFile());
            byte[] outputDigest = Crypto.digest(tempPath.toFile());
            if (Arrays.equals(inputDigest, outputDigest)) {
                // Patch is valid, but we might want to reject if it's larger than the original file
                long originalSize = Files.size(after);
                long patchSize = Files.size(destination);
                if (patchSize < originalSize) {
                    // Patch file is smaller than the original file size, so treat it as valid
                    return true;
                }
            }
            else {
                LOGGER.info("Checksum mismatch when verifying patch for file {}", destination.toString());
                return false;
            }

        }
        catch (IOException e) {
            LOGGER.info("Failed to compute merge for file {}: {}", destination.toString(), e.getMessage());
        }
        finally {
            try {
                Files.delete(tempPath);
            } catch (IOException e) {
                // Not important - will be cleaned up later
            }
        }

        return false;
    }

    /**
     * Apply a patch at path "after" on top of base path "before",
     * outputting the combined results to the "destination" path.
     * If before and after are directories, a relative path suffix
     * can be used to specify the file within these folder structures.
     *
     * @param pathSuffix - a file path to append to the base paths, or null if the base paths are already files
     * @throws IOException
     */
    public void apply(Path pathSuffix) throws IOException, DataException {
        Path originalPath = this.before;
        Path patchPath = this.after;
        Path mergePath = this.destination;

        // If a path has been supplied, we need to append it to the base paths
        if (pathSuffix != null) {
            originalPath = Paths.get(this.before.toString(), pathSuffix.toString());
            patchPath = Paths.get(this.after.toString(), pathSuffix.toString());
            mergePath = Paths.get(this.destination.toString(), pathSuffix.toString());
        }

        if (!patchPath.toFile().exists()) {
            throw new DataException("Patch file doesn't exist, but its path was included in modifiedPaths");
        }

        // Delete an existing file, as we are starting from a duplicate of pathBefore
        File destFile = mergePath.toFile();
        if (destFile.exists() && destFile.isFile()) {
            Files.delete(mergePath);
        }

        List<String> originalContents = FileUtils.readLines(originalPath.toFile(), StandardCharsets.UTF_8);
        List<String> patchContents = FileUtils.readLines(patchPath.toFile(), StandardCharsets.UTF_8);

        // Check if the patch file (and therefore the original file) ends with a newline
        boolean endsWithNewline = FilesystemUtils.fileEndsWithNewline(patchPath);

        // At first, parse the unified diff file and get the patch
        Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchContents);

        // Then apply the computed patch to the given text
        try {
            List<String> patchedContents = DiffUtils.patch(originalContents, patch);

            // Write the patched file to the merge directory
            FileWriter fileWriter = new FileWriter(mergePath.toString(), true);
            BufferedWriter writer = new BufferedWriter(fileWriter);
            for (int i=0; i<patchedContents.size(); i++) {
                String line = patchedContents.get(i);
                writer.append(line);
                // Add a newline if this isn't the last line, or the original ended with a newline
                if (i < patchedContents.size()-1 || endsWithNewline) {
                    writer.newLine();
                }
            }
            writer.flush();
            writer.close();

        } catch (PatchFailedException e) {
            throw new DataException(String.format("Failed to apply patch for path %s: %s", pathSuffix, e.getMessage()));
        }
    }

    private void createRandomIdentifier() {
        this.identifier = UUID.randomUUID().toString();
    }

    private void createTempValidationDirectory() throws DataException {
        // Use the user-specified temp dir, as it is deterministic, and is more likely to be located on reusable storage hardware
        String baseDir = Settings.getInstance().getTempDataPath();
        Path tempDir = Paths.get(baseDir, "diff", "validate");
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new DataException("Unable to create temp directory");
        }
        this.validationPath = tempDir;
    }

}
