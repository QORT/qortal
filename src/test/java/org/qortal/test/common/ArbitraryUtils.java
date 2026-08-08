package org.qortal.test.common;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.arbitrary.ArbitraryDataTransactionBuilder;
import org.qortal.arbitrary.misc.Category;
import org.qortal.arbitrary.misc.Service;
import org.qortal.block.BlockChain;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transaction.Transaction;
import org.qortal.utils.NTP;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class ArbitraryUtils {

    public static ArbitraryDataFile createAndMintTxn(Repository repository, String publicKey58, Path path, String name, String identifier,
                                                     ArbitraryTransactionData.Method method, Service service, PrivateKeyAccount account,
                                                     int chunkSize) throws DataException {

        long fee = BlockChain.getInstance().getUnitFeeAtTimestamp(NTP.getTime());
        return ArbitraryUtils.createAndMintTxn(repository, publicKey58, path, name, identifier, method, service,
                account, chunkSize, fee, false, null, null, null, null);
    }

    public static ArbitraryDataFile createAndMintTxn(Repository repository, String publicKey58, Path path, String name, String identifier,
                                                     ArbitraryTransactionData.Method method, Service service, PrivateKeyAccount account,
                                                     int chunkSize, long fee, boolean computeNonce,
                                                     String title, String description, List<String> tags, Category category) throws DataException {

        ArbitraryDataTransactionBuilder txnBuilder = new ArbitraryDataTransactionBuilder(
                repository, publicKey58, fee, path, name, method, service, identifier, title, description, tags, category);

        txnBuilder.setChunkSize(chunkSize);
        txnBuilder.build();
        if (computeNonce) {
            txnBuilder.computeNonce();
        }
        ArbitraryTransactionData transactionData = txnBuilder.getArbitraryTransactionData();
        Transaction.ValidationResult result = TransactionUtils.signAndImport(repository, transactionData, account);
        if (result != Transaction.ValidationResult.OK) {
            throw new DataException(String.format("Arbitrary transaction invalid: %s", result.toString()));
        }
        BlockUtils.mintBlock(repository);

        // We need a new ArbitraryDataFile instance because the files will have been moved to the signature's folder
        // Or, it may now be using RAW_DATA instead of a hash
        return ArbitraryDataFile.fromTransactionData(transactionData);
    }

    public static ArbitraryDataFile createAndMintTxn(Repository repository, String publicKey58, Path path, String name, String identifier,
                                                     ArbitraryTransactionData.Method method, Service service, PrivateKeyAccount account) throws DataException {

        // Use default chunk size
        int chunkSize = ArbitraryDataFile.CHUNK_SIZE;
        return ArbitraryUtils.createAndMintTxn(repository, publicKey58, path, name, identifier, method, service, account, chunkSize);
    }

    public static Path generateRandomDataPath(int length) throws IOException {
        return generateRandomDataPath(length, false);
    }

    /**
     * Generate random data, held in a single file within a directory
     * @param length - size of file to create
     * @param returnFilePath - if true, the file's path is returned. If false, the outer directory's path is returned.
     * @return - path to file or directory, depending on the "returnFilePath" boolean
     * @throws IOException
     */
    public static Path generateRandomDataPath(int length, boolean returnFilePath) throws IOException {
        // Create a file in a random temp directory
        Path tempDir = Files.createTempDirectory("generateRandomDataPath");
        File file = new File(Paths.get(tempDir.toString(), "file.txt").toString());
        file.deleteOnExit();

        // Write a random string to the file
        BufferedWriter file1Writer = new BufferedWriter(new FileWriter(file));
        String initialString = ArbitraryUtils.generateRandomString(length - 1); // -1 due to newline at EOF

        // Add a newline every 50 chars
        // initialString = initialString.replaceAll("(.{50})", "$1\n");

        file1Writer.write(initialString);
        file1Writer.newLine();
        file1Writer.close();

        if (returnFilePath) {
            return file.toPath();
        }

        return tempDir;
    }

    public static String generateRandomString(int length) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

}
