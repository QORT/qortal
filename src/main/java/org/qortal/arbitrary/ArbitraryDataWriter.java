package org.qortal.arbitrary;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.arbitrary.exception.MissingDataException;
import org.qortal.arbitrary.metadata.ArbitraryDataTransactionMetadata;
import org.qortal.arbitrary.misc.Service;
import org.qortal.crypto.Crypto;
import org.qortal.data.transaction.ArbitraryTransactionData.*;
import org.qortal.crypto.AES;
import org.qortal.repository.DataException;
import org.qortal.arbitrary.ArbitraryDataFile.*;
import org.qortal.settings.Settings;
import org.qortal.utils.Base58;
import org.qortal.utils.FilesystemUtils;
import org.qortal.utils.ZipUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class ArbitraryDataWriter {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataWriter.class);

    private Path filePath;
    private final String name;
    private final Service service;
    private final String identifier;
    private final Method method;
    private final Compression compression;

    private int chunkSize = ArbitraryDataFile.CHUNK_SIZE;

    private SecretKey aesKey;
    private ArbitraryDataFile arbitraryDataFile;

    // Intermediate paths to cleanup
    private Path workingPath;
    private Path compressedPath;
    private Path encryptedPath;

    public ArbitraryDataWriter(Path filePath, String name, Service service, String identifier, Method method, Compression compression) {
        this.filePath = filePath;
        this.name = name;
        this.service = service;
        this.method = method;
        this.compression = compression;

        // If identifier is a blank string, or reserved keyword "default", treat it as null
        if (identifier == null || identifier.equals("") || identifier.equals("default")) {
            identifier = null;
        }
        this.identifier = identifier;
    }

    public void save() throws IOException, DataException, InterruptedException, MissingDataException {
        try {
            this.preExecute();
            this.validateService();
            this.process();
            this.compress();
            this.encrypt();
            this.split();
            this.createMetadataFile();
            this.validate();

        } finally {
            this.postExecute();
        }
    }

    private void preExecute() throws DataException {
        this.checkEnabled();

        // Enforce compression when uploading a directory
        File file = new File(this.filePath.toString());
        if (file.isDirectory() && compression == Compression.NONE) {
            throw new DataException("Unable to upload a directory without compression");
        }

        // Create temporary working directory
        this.createWorkingDirectory();
    }

    private void postExecute() throws IOException {
        this.cleanupFilesystem();
    }

    private void checkEnabled() throws DataException {
        if (!Settings.getInstance().isQdnEnabled()) {
            throw new DataException("QDN is disabled in settings");
        }
    }

    private void createWorkingDirectory() throws DataException {
        // Use the user-specified temp dir, as it is deterministic, and is more likely to be located on reusable storage hardware
        String baseDir = Settings.getInstance().getTempDataPath();
        String identifier = Base58.encode(Crypto.digest(this.filePath.toString().getBytes()));
        Path tempDir = Paths.get(baseDir, "writer", identifier);
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new DataException("Unable to create temp directory");
        }
        this.workingPath = tempDir;
    }

    private void validateService() throws IOException, DataException {
        if (this.service.isValidationRequired()) {
            Service.ValidationResult result = this.service.validate(this.filePath);
            if (result != Service.ValidationResult.OK) {
                throw new DataException(String.format("Validation of %s failed: %s", this.service, result.toString()));
            }
        }
    }

    private void process() throws DataException, IOException, MissingDataException {
        switch (this.method) {

            case PUT:
                // Nothing to do
                break;

            case PATCH:
                this.processPatch();
                break;

            default:
                throw new DataException(String.format("Unknown method specified: %s", method.toString()));
        }
    }

    private void processPatch() throws DataException, IOException, MissingDataException {

        // Build the existing state using past transactions
        ArbitraryDataBuilder builder = new ArbitraryDataBuilder(this.name, this.service, this.identifier);
        builder.build();
        Path builtPath = builder.getFinalPath();

        // Obtain the latest signature, so this can be included in the patch
        byte[] latestSignature = builder.getLatestSignature();

        // Compute a diff of the latest changes on top of the previous state
        // Then use only the differences as our data payload
        ArbitraryDataCreatePatch patch = new ArbitraryDataCreatePatch(builtPath, this.filePath, latestSignature);
        patch.create();
        this.filePath = patch.getFinalPath();

        // Delete the input directory
        if (FilesystemUtils.pathInsideDataOrTempPath(builtPath)) {
            File directory = new File(builtPath.toString());
            FileUtils.deleteDirectory(directory);
        }

        // Validate the patch
        this.validatePatch();
    }

    private void validatePatch() throws DataException {
        if (this.filePath == null) {
            throw new DataException("Null path after creating patch");
        }

        File qortalMetadataDirectoryFile = Paths.get(this.filePath.toString(), ".qortal").toFile();
        if (!qortalMetadataDirectoryFile.exists()) {
            throw new DataException("Qortal metadata folder doesn't exist in patch");
        }
        if (!qortalMetadataDirectoryFile.isDirectory()) {
            throw new DataException("Qortal metadata folder isn't a directory");
        }

        File qortalPatchMetadataFile = Paths.get(this.filePath.toString(), ".qortal", "patch").toFile();
        if (!qortalPatchMetadataFile.exists()) {
            throw new DataException("Qortal patch metadata file doesn't exist in patch");
        }
        if (!qortalPatchMetadataFile.isFile()) {
            throw new DataException("Qortal patch metadata file isn't a file");
        }
    }

    private void compress() throws InterruptedException, DataException {
        // Compress the data if requested
        if (this.compression != Compression.NONE) {
            this.compressedPath = Paths.get(this.workingPath.toString(), "data.zip");
            try {

                if (this.compression == Compression.ZIP) {
                    LOGGER.info("Compressing...");
                    String enclosingFolderName = "data";
                    ZipUtils.zip(this.filePath.toString(), this.compressedPath.toString(), enclosingFolderName);
                }
                else {
                    throw new DataException(String.format("Unknown compression type specified: %s", compression.toString()));
                }
                // FUTURE: other compression types

                // Delete the input directory
                if (FilesystemUtils.pathInsideDataOrTempPath(this.filePath)) {
                    File directory = new File(this.filePath.toString());
                    FileUtils.deleteDirectory(directory);
                }
                // Replace filePath pointer with the zipped file path
                this.filePath = this.compressedPath;

            } catch (IOException | DataException e) {
                throw new DataException("Unable to zip directory", e);
            }
        }
    }

    private void encrypt() throws DataException {
        this.encryptedPath = Paths.get(this.workingPath.toString(), "data.zip.encrypted");
        try {
            // Encrypt the file with AES
            LOGGER.info("Encrypting...");
            this.aesKey = AES.generateKey(256);
            AES.encryptFile("AES/CBC/PKCS5Padding", this.aesKey, this.filePath.toString(), this.encryptedPath.toString());

            // Delete the input file
            if (FilesystemUtils.pathInsideDataOrTempPath(this.filePath)) {
                Files.delete(this.filePath);
            }
            // Replace filePath pointer with the encrypted file path
            this.filePath = this.encryptedPath;

        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchPaddingException
                | BadPaddingException | IllegalBlockSizeException | IOException | InvalidKeyException e) {
            throw new DataException(String.format("Unable to encrypt file %s: %s", this.filePath, e.getMessage()));
        }
    }

    private void split() throws IOException, DataException {
        // We don't have a signature yet, so use null to put the file in a generic folder
        this.arbitraryDataFile = ArbitraryDataFile.fromPath(this.filePath, null);
        if (this.arbitraryDataFile == null) {
            throw new IOException("No file available when trying to split");
        }

        int chunkCount = this.arbitraryDataFile.split(this.chunkSize);
        if (chunkCount > 0) {
            LOGGER.info(String.format("Successfully split into %d chunk%s", chunkCount, (chunkCount == 1 ? "" : "s")));
        }
        else {
            throw new DataException("Unable to split file into chunks");
        }
    }

    private void createMetadataFile() throws IOException, DataException {
        // If we have at least one chunk, we need to create an index file containing their hashes
        if (this.arbitraryDataFile.chunkCount() > 1) {
            // Create the JSON file
            Path chunkFilePath = Paths.get(this.workingPath.toString(), "metadata.json");
            ArbitraryDataTransactionMetadata chunkMetadata = new ArbitraryDataTransactionMetadata(chunkFilePath);
            chunkMetadata.setChunks(this.arbitraryDataFile.chunkHashList());
            chunkMetadata.write();

            // Create an ArbitraryDataFile from the JSON file (we don't have a signature yet)
            ArbitraryDataFile metadataFile = ArbitraryDataFile.fromPath(chunkFilePath, null);
            this.arbitraryDataFile.setMetadataFile(metadataFile);
        }
    }

    private void validate() throws IOException, DataException {
        if (this.arbitraryDataFile == null) {
            throw new DataException("No file available when validating");
        }
        this.arbitraryDataFile.setSecret(this.aesKey.getEncoded());

        // Validate the file
        ValidationResult validationResult = this.arbitraryDataFile.isValid();
        if (validationResult != ValidationResult.OK) {
            throw new DataException(String.format("File %s failed validation: %s", this.arbitraryDataFile, validationResult));
        }
        LOGGER.info("Whole file hash is valid: {}", this.arbitraryDataFile.digest58());

        // Validate each chunk
        for (ArbitraryDataFileChunk chunk : this.arbitraryDataFile.getChunks()) {
            validationResult = chunk.isValid();
            if (validationResult != ValidationResult.OK) {
                throw new DataException(String.format("Chunk %s failed validation: %s", chunk, validationResult));
            }
        }
        LOGGER.info("Chunk hashes are valid");

        // Validate chunks metadata file
        if (this.arbitraryDataFile.chunkCount() > 1) {
            ArbitraryDataFile metadataFile = this.arbitraryDataFile.getMetadataFile();
            if (metadataFile == null || !metadataFile.exists()) {
                throw new DataException("No metadata file available, but there are multiple chunks");
            }
            // Read the file
            ArbitraryDataTransactionMetadata metadata = new ArbitraryDataTransactionMetadata(metadataFile.getFilePath());
            metadata.read();
            // Check all chunks exist
            for (byte[] chunk : this.arbitraryDataFile.chunkHashList()) {
                if (!metadata.containsChunk(chunk)) {
                    throw new DataException(String.format("Missing chunk %s in metadata file", Base58.encode(chunk)));
                }
            }
        }
    }

    private void cleanupFilesystem() throws IOException {
        // Clean up
        if (FilesystemUtils.pathInsideDataOrTempPath(this.compressedPath)) {
            File zippedFile = new File(this.compressedPath.toString());
            if (zippedFile.exists()) {
                zippedFile.delete();
            }
        }
        if (FilesystemUtils.pathInsideDataOrTempPath(this.encryptedPath)) {
            File encryptedFile = new File(this.encryptedPath.toString());
            if (encryptedFile.exists()) {
                encryptedFile.delete();
            }
        }
        if (FilesystemUtils.pathInsideDataOrTempPath(this.workingPath)) {
            FileUtils.deleteDirectory(new File(this.workingPath.toString()));
        }
    }


    public ArbitraryDataFile getArbitraryDataFile() {
        return this.arbitraryDataFile;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

}
