package org.qortal.arbitrary;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.qortal.arbitrary.exception.MissingDataException;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.arbitrary.ArbitraryDataBuildManager;
import org.qortal.controller.arbitrary.ArbitraryDataManager;
import org.qortal.controller.arbitrary.ArbitraryDataStorageManager;
import org.qortal.crypto.AES;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.ArbitraryTransactionData.*;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.arbitrary.ArbitraryDataFile.*;
import org.qortal.settings.Settings;
import org.qortal.transform.Transformer;
import org.qortal.utils.ArbitraryTransactionUtils;
import org.qortal.utils.Base58;
import org.qortal.utils.FilesystemUtils;
import org.qortal.utils.ZipUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class ArbitraryDataReader {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataReader.class);

    private final String resourceId;
    private final ResourceIdType resourceIdType;
    private final Service service;
    private final String identifier;
    private ArbitraryTransactionData transactionData;
    private String secret58;
    private Path filePath;
    private boolean canRequestMissingFiles;

    // Intermediate paths
    private final Path workingPath;
    private final Path uncompressedPath;

    // Stats (available for synchronous builds only)
    private int layerCount;
    private byte[] latestSignature;

    public ArbitraryDataReader(String resourceId, ResourceIdType resourceIdType, Service service, String identifier) {
        // Ensure names are always lowercase
        if (resourceIdType == ResourceIdType.NAME) {
            resourceId = resourceId.toLowerCase();
        }

        // If identifier is a blank string, or reserved keyword "default", treat it as null
        if (identifier == null || identifier.equals("") || identifier.equals("default")) {
            identifier = null;
        }

        this.resourceId = resourceId;
        this.resourceIdType = resourceIdType;
        this.service = service;
        this.identifier = identifier;

        this.workingPath = this.buildWorkingPath();
        this.uncompressedPath = Paths.get(this.workingPath.toString(), "data");

        // By default we can request missing files
        // Callers can use setCanRequestMissingFiles(false) to prevent it
        this.canRequestMissingFiles = true;
    }

    private Path buildWorkingPath() {
        // Use the user-specified temp dir, as it is deterministic, and is more likely to be located on reusable storage hardware
        String baseDir = Settings.getInstance().getTempDataPath();
        String identifier = this.identifier != null ?  this.identifier : "default";
        return Paths.get(baseDir, "reader", this.resourceIdType.toString(), this.resourceId, this.service.toString(), identifier);
    }

    public boolean isCachedDataAvailable() {
        // If this resource is in the build queue then we shouldn't attempt to serve
        // cached data, as it may not be fully built
        if (ArbitraryDataBuildManager.getInstance().isInBuildQueue(this.createQueueItem())) {
            return false;
        }

        // Not in the build queue - so check the cache itself
        ArbitraryDataCache cache = new ArbitraryDataCache(this.uncompressedPath, false,
                this.resourceId, this.resourceIdType, this.service, this.identifier);
        if (cache.isCachedDataAvailable()) {
            this.filePath = this.uncompressedPath;
            return true;
        }
        return false;
    }

    public boolean isBuilding() {
        return ArbitraryDataBuildManager.getInstance().isInBuildQueue(this.createQueueItem());
    }

    private ArbitraryDataBuildQueueItem createQueueItem() {
        return new ArbitraryDataBuildQueueItem(this.resourceId, this.resourceIdType, this.service, this.identifier);
    }

    /**
     * loadAsynchronously
     *
     * Attempts to load the resource asynchronously
     * This adds the build task to a queue, and the result will be cached when complete
     * To check the status of the build, periodically call isCachedDataAvailable()
     * Once it returns true, you can then use getFilePath() to access the data itself.
     *
     * @param overwrite - set to true to force rebuild an existing cache
     * @return true if added or already present in queue; false if not
     */
    public boolean loadAsynchronously(boolean overwrite, int priority) {
        ArbitraryDataCache cache = new ArbitraryDataCache(this.uncompressedPath, overwrite,
                this.resourceId, this.resourceIdType, this.service, this.identifier);
        if (cache.isCachedDataAvailable()) {
            // Use cached data
            this.filePath = this.uncompressedPath;
            return true;
        }

        ArbitraryDataBuildQueueItem item = this.createQueueItem();
        item.setPriority(priority);
        return ArbitraryDataBuildManager.getInstance().addToBuildQueue(item);
    }

    /**
     * loadSynchronously
     *
     * Attempts to load the resource synchronously
     * Warning: this can block for a long time when building or fetching complex data
     * If no exception is thrown, you can then use getFilePath() to access the data immediately after returning
     *
     * @param overwrite - set to true to force rebuild an existing cache
     * @throws IOException
     * @throws DataException
     * @throws MissingDataException
     */
    public void loadSynchronously(boolean overwrite) throws DataException, IOException, MissingDataException {
        try {
            ArbitraryDataCache cache = new ArbitraryDataCache(this.uncompressedPath, overwrite,
                    this.resourceId, this.resourceIdType, this.service, this.identifier);
            if (cache.isCachedDataAvailable()) {
                // Use cached data
                this.filePath = this.uncompressedPath;
                return;
            }

            this.preExecute();
            this.deleteExistingFiles();
            this.fetch();
            this.decrypt();
            this.uncompress();
            this.validate();

        } catch (DataException e) {
            this.deleteWorkingDirectory();
            throw new DataException(e.getMessage());

        } finally {
            this.postExecute();
        }
    }

    private void preExecute() throws DataException {
        ArbitraryDataBuildManager.getInstance().setBuildInProgress(true);
        this.checkEnabled();
        this.createWorkingDirectory();
        this.createUncompressedDirectory();
    }

    private void postExecute() {
        ArbitraryDataBuildManager.getInstance().setBuildInProgress(false);
    }

    private void checkEnabled() throws DataException {
        if (!Settings.getInstance().isQdnEnabled()) {
            throw new DataException("QDN is disabled in settings");
        }
    }

    private void createWorkingDirectory() throws DataException {
        try {
            Files.createDirectories(this.workingPath);
        } catch (IOException e) {
            throw new DataException("Unable to create temp directory");
        }
    }

    /**
     * Working directory should only be deleted on failure, since it is currently used to
     * serve a cached version of the resource for subsequent requests.
     * @throws IOException
     */
    private void deleteWorkingDirectory() throws IOException {
        FilesystemUtils.safeDeleteDirectory(this.workingPath, true);
    }

    private void createUncompressedDirectory() throws DataException {
        try {
            // Create parent directory
            Files.createDirectories(this.uncompressedPath.getParent());
            // Ensure child directory doesn't already exist
            FileUtils.deleteDirectory(this.uncompressedPath.toFile());

        } catch (IOException e) {
            throw new DataException("Unable to create uncompressed directory");
        }
    }

    private void deleteExistingFiles() {
        final Path uncompressedPath = this.uncompressedPath;
        if (FilesystemUtils.pathInsideDataOrTempPath(uncompressedPath)) {
            if (Files.exists(uncompressedPath)) {
                LOGGER.trace("Attempting to delete path {}", this.uncompressedPath);
                try {
                    Files.walkFileTree(uncompressedPath, new SimpleFileVisitor<>() {

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                            // Don't delete the parent directory, as we want to leave an empty folder
                            if (dir.compareTo(uncompressedPath) == 0) {
                                return FileVisitResult.CONTINUE;
                            }

                            if (e == null) {
                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            } else {
                                throw e;
                            }
                        }

                    });
                } catch (IOException e) {
                    LOGGER.debug("Unable to delete file or directory: {}", e.getMessage());
                }
            }
        }
    }

    private void fetch() throws DataException, IOException, MissingDataException {
        switch (resourceIdType) {

            case FILE_HASH:
                this.fetchFromFileHash();
                break;

            case NAME:
                this.fetchFromName();
                break;

            case SIGNATURE:
                this.fetchFromSignature();
                break;

            case TRANSACTION_DATA:
                this.fetchFromTransactionData(this.transactionData);
                break;

            default:
                throw new DataException(String.format("Unknown resource ID type specified: %s", resourceIdType.toString()));
        }
    }

    private void fetchFromFileHash() throws DataException {
        // Load data file directly from the hash (without a signature)
        ArbitraryDataFile arbitraryDataFile = ArbitraryDataFile.fromHash58(resourceId, null);
        // Set filePath to the location of the ArbitraryDataFile
        this.filePath = arbitraryDataFile.getFilePath();
    }

    private void fetchFromName() throws DataException, IOException, MissingDataException {
        try {

            // Build the existing state using past transactions
            ArbitraryDataBuilder builder = new ArbitraryDataBuilder(this.resourceId, this.service, this.identifier);
            builder.build();
            Path builtPath = builder.getFinalPath();
            if (builtPath == null) {
                throw new DataException("Unable to build path");
            }

            // Update stats
            this.layerCount = builder.getLayerCount();
            this.latestSignature = builder.getLatestSignature();

            // Set filePath to the builtPath
            this.filePath = builtPath;

        } catch (InvalidObjectException e) {
            // Hash validation failed. Invalidate the cache for this name, so it can be rebuilt
            LOGGER.info("Deleting {}", this.workingPath.toString());
            FilesystemUtils.safeDeleteDirectory(this.workingPath, false);
            throw(e);
        }
    }

    private void fetchFromSignature() throws DataException, IOException, MissingDataException {

        // Load the full transaction data from the database so we can access the file hashes
        ArbitraryTransactionData transactionData;
        try (final Repository repository = RepositoryManager.getRepository()) {
            transactionData = (ArbitraryTransactionData) repository.getTransactionRepository().fromSignature(Base58.decode(resourceId));
        }
        if (transactionData == null) {
            throw new DataException(String.format("Transaction data not found for signature %s", this.resourceId));
        }

        this.fetchFromTransactionData(transactionData);
    }

    private void fetchFromTransactionData(ArbitraryTransactionData transactionData) throws DataException, IOException, MissingDataException {
        if (transactionData == null) {
            throw new DataException(String.format("Transaction data not found for signature %s", this.resourceId));
        }

        // Load hashes
        byte[] digest = transactionData.getData();
        byte[] metadataHash = transactionData.getMetadataHash();
        byte[] signature = transactionData.getSignature();

        // Load secret
        byte[] secret = transactionData.getSecret();
        if (secret != null) {
            this.secret58 = Base58.encode(secret);
        }

        // Load data file(s)
        ArbitraryDataFile arbitraryDataFile = ArbitraryDataFile.fromHash(digest, signature);
        ArbitraryTransactionUtils.checkAndRelocateMiscFiles(transactionData);
        arbitraryDataFile.setMetadataHash(metadataHash);

        if (!arbitraryDataFile.allFilesExist()) {
            if (ArbitraryDataStorageManager.getInstance().isNameBlocked(transactionData.getName())) {
                throw new DataException(
                        String.format("Unable to request missing data for file %s because the name is blocked", arbitraryDataFile));
            }
            else {
                // Ask the arbitrary data manager to fetch data for this transaction
                String message;
                if (this.canRequestMissingFiles) {
                    boolean requested = ArbitraryDataManager.getInstance().fetchData(transactionData);

                    if (requested) {
                        message = String.format("Requested missing data for file %s", arbitraryDataFile);
                    } else {
                        message = String.format("Unable to reissue request for missing file %s for signature %s due to rate limit. Please try again later.", arbitraryDataFile, Base58.encode(transactionData.getSignature()));
                    }
                }
                else {
                    message = String.format("Missing data for file %s", arbitraryDataFile);
                }

                // Throw a missing data exception, which allows subsequent layers to fetch data
                LOGGER.trace(message);
                throw new MissingDataException(message);
            }
        }

        if (arbitraryDataFile.allChunksExist() && !arbitraryDataFile.exists()) {
            // We have all the chunks but not the complete file, so join them
            arbitraryDataFile.join();
        }

        // If the complete file still doesn't exist then something went wrong
        if (!arbitraryDataFile.exists()) {
            throw new IOException(String.format("File doesn't exist: %s", arbitraryDataFile));
        }
        // Ensure the complete hash matches the joined chunks
        if (!Arrays.equals(arbitraryDataFile.digest(), digest)) {
            // Delete the invalid file
            arbitraryDataFile.delete();
            throw new DataException("Unable to validate complete file hash");
        }
        // Ensure the file's size matches the size reported by the transaction (throws a DataException if not)
        arbitraryDataFile.validateFileSize(transactionData.getSize());

        // Set filePath to the location of the ArbitraryDataFile
        this.filePath = arbitraryDataFile.getFilePath();
    }

    private void decrypt() throws DataException {
        try {
            // First try with explicit parameters (CBC mode with PKCS5 padding)
            this.decryptUsingAlgo("AES/CBC/PKCS5Padding");

        } catch (DataException e) {
            // Something went wrong, so fall back to default AES params (necessary for legacy resource support)
            this.decryptUsingAlgo("AES");

            // TODO: delete files and block this resource if privateDataEnabled is false and the second attempt fails too
        }
    }

    private void decryptUsingAlgo(String algorithm) throws DataException {
        // Decrypt if we have the secret key.
        byte[] secret = this.secret58 != null ? Base58.decode(this.secret58) : null;
        if (secret != null && secret.length == Transformer.AES256_LENGTH) {
            try {
                Path unencryptedPath = Paths.get(this.workingPath.toString(), "zipped.zip");
                SecretKey aesKey = new SecretKeySpec(secret, 0, secret.length, algorithm);
                AES.decryptFile(algorithm, aesKey, this.filePath.toString(), unencryptedPath.toString());

                // Replace filePath pointer with the encrypted file path
                // Don't delete the original ArbitraryDataFile, as this is handled in the cleanup phase
                this.filePath = unencryptedPath;

            } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchPaddingException
                    | BadPaddingException | IllegalBlockSizeException | IOException | InvalidKeyException e) {
                throw new DataException(String.format("Unable to decrypt file at path %s: %s", this.filePath, e.getMessage()));
            }
        } else {
            // Assume it is unencrypted. This will be the case when we have built a custom path by combining
            // multiple decrypted archives into a single state.
        }
    }

    private void uncompress() throws IOException, DataException {
        if (this.filePath == null || !Files.exists(this.filePath)) {
            throw new DataException("Can't uncompress non-existent file path");
        }
        File file = new File(this.filePath.toString());
        if (file.isDirectory()) {
            // Already a directory - nothing to uncompress
            // We still need to copy the directory to its final destination if it's not already there
            this.moveFilePathToFinalDestination();
            return;
        }

        try {
            // Default to ZIP compression - this is needed for previews
            Compression compression = transactionData != null ? transactionData.getCompression() : Compression.ZIP;

            // Handle each type of compression
            if (compression == Compression.ZIP) {
                ZipUtils.unzip(this.filePath.toString(), this.uncompressedPath.getParent().toString());
            }
            else if (compression == Compression.NONE) {
                Files.createDirectories(this.uncompressedPath);
                Path finalPath = Paths.get(this.uncompressedPath.toString(), "data");
                this.filePath.toFile().renameTo(finalPath.toFile());
            }
            else {
                throw new DataException(String.format("Unrecognized compression type: %s", transactionData.getCompression()));
            }
        } catch (IOException e) {
            throw new DataException(String.format("Unable to unzip file: %s", e.getMessage()));
        }

        if (!this.uncompressedPath.toFile().exists()) {
            throw new DataException(String.format("Unable to unzip file: %s", this.filePath));
        }

        // Delete original compressed file
        if (FilesystemUtils.pathInsideDataOrTempPath(this.filePath)) {
            if (Files.exists(this.filePath)) {
                Files.delete(this.filePath);
            }
        }

        // Replace filePath pointer with the uncompressed file path
        this.filePath = this.uncompressedPath;
    }

    private void validate() throws IOException, DataException {
        if (this.service.isValidationRequired()) {
            Service.ValidationResult result = this.service.validate(this.filePath);
            if (result != Service.ValidationResult.OK) {
                throw new DataException(String.format("Validation of %s failed: %s", this.service, result.toString()));
            }
        }
    }


    private void moveFilePathToFinalDestination() throws IOException, DataException {
        if (this.filePath.compareTo(this.uncompressedPath) != 0) {
            File source = new File(this.filePath.toString());
            File dest = new File(this.uncompressedPath.toString());
            if (!source.exists()) {
                throw new DataException("Source directory doesn't exist");
            }
            // Ensure destination directory doesn't exist
            FileUtils.deleteDirectory(dest);
            // Move files to destination
            FilesystemUtils.copyAndReplaceDirectory(source.toString(), dest.toString());

            try {
                // Delete existing
                if (FilesystemUtils.pathInsideDataOrTempPath(this.filePath)) {
                    File directory = new File(this.filePath.toString());
                    FileUtils.deleteDirectory(directory);
                }

                // ... and its parent directory if empty
                Path parentDirectory = this.filePath.getParent();
                if (FilesystemUtils.pathInsideDataOrTempPath(parentDirectory)) {
                    Files.deleteIfExists(parentDirectory);
                }

            } catch (DirectoryNotEmptyException e) {
                    // No need to log anything
            } catch (IOException e) {
                // This will eventually be cleaned up by a maintenance process, so log the error and continue
                LOGGER.debug("Unable to cleanup directories: {}", e.getMessage());
            }

            // Finally, update filePath to point to uncompressedPath
            this.filePath = this.uncompressedPath;
        }
    }


    public void setTransactionData(ArbitraryTransactionData transactionData) {
        this.transactionData = transactionData;
    }

    public void setSecret58(String secret58) {
        this.secret58 = secret58;
    }

    public Path getFilePath() {
        return this.filePath;
    }

    public int getLayerCount() {
        return this.layerCount;
    }

    public byte[] getLatestSignature() {
        return this.latestSignature;
    }

    /**
     * Use the below setter to ensure that we only read existing
     * data without requesting any missing files,
     *
     * @param canRequestMissingFiles - whether or not fetching missing files is allowed
     */
    public void setCanRequestMissingFiles(boolean canRequestMissingFiles) {
        this.canRequestMissingFiles = canRequestMissingFiles;
    }

}
