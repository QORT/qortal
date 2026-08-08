package org.qortal.test.arbitrary;

import org.junit.Before;
import org.junit.Test;
import org.qortal.arbitrary.ArbitraryDataDigest;
import org.qortal.crypto.Crypto;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;
import org.qortal.utils.ZipUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class ArbitraryCompressionTests extends Common {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @Test
    public void testZipSingleFile() throws IOException, InterruptedException {
        String enclosingFolderName = "data";
        Path inputFile = Files.createTempFile("inputFile", null);
        Path outputDirectory = Files.createTempDirectory("outputDirectory");
        Path outputFile = Paths.get(outputDirectory.toString(), enclosingFolderName);
        inputFile.toFile().deleteOnExit();
        outputDirectory.toFile().deleteOnExit();

        // Write random data to the input file
        byte[] data = new byte[1024];
        new Random().nextBytes(data);
        Files.write(inputFile, data, StandardOpenOption.CREATE);

        assertTrue(Files.exists(inputFile));
        assertFalse(Files.exists(outputFile));

        // Zip...
        ZipUtils.zip(inputFile.toString(), outputFile.toString(), enclosingFolderName);

        assertTrue(Files.exists(inputFile));
        assertTrue(Files.exists(outputFile));

        // Ensure zipped file's hash differs from the original
        assertFalse(Arrays.equals(Crypto.digest(inputFile.toFile()), Crypto.digest(outputFile.toFile())));

        // Create paths for unzipping
        Path unzippedDirectory = Files.createTempDirectory("unzippedDirectory");
        // Single file data is unzipped directly, without an enclosing folder. Original name is maintained.
        Path unzippedFile = Paths.get(unzippedDirectory.toString(), enclosingFolderName, inputFile.getFileName().toString());
        unzippedDirectory.toFile().deleteOnExit();
        assertFalse(Files.exists(unzippedFile));

        // Now unzip...
        ZipUtils.unzip(outputFile.toString(), unzippedDirectory.toString());

        // Ensure resulting file exists
        assertTrue(Files.exists(unzippedFile));

        // And make sure it matches the original input file
        assertTrue(Arrays.equals(Crypto.digest(inputFile.toFile()), Crypto.digest(unzippedFile.toFile())));
    }

    @Test
    public void testZipDirectoryWithSingleFile() throws IOException, InterruptedException, DataException {
        String enclosingFolderName = "data";
        Path inputDirectory = Files.createTempDirectory("inputDirectory");
        Path outputDirectory = Files.createTempDirectory("outputDirectory");
        Path outputFile = Paths.get(outputDirectory.toString(), enclosingFolderName);
        inputDirectory.toFile().deleteOnExit();
        outputDirectory.toFile().deleteOnExit();

        Path inputFile = Paths.get(inputDirectory.toString(), "file");

        // Write random data to a file
        byte[] data = new byte[1024];
        new Random().nextBytes(data);
        Files.write(inputFile, data, StandardOpenOption.CREATE);

        assertTrue(Files.exists(inputDirectory));
        assertTrue(Files.exists(inputFile));
        assertFalse(Files.exists(outputFile));

        // Zip...
        ZipUtils.zip(inputDirectory.toString(), outputFile.toString(), enclosingFolderName);

        assertTrue(Files.exists(inputDirectory));
        assertTrue(Files.exists(outputFile));

        // Create paths for unzipping
        Path unzippedDirectory = Files.createTempDirectory("unzippedDirectory");
        unzippedDirectory.toFile().deleteOnExit();
        Path unzippedFile = Paths.get(unzippedDirectory.toString(), enclosingFolderName, "file");
        assertFalse(Files.exists(unzippedFile));

        // Now unzip...
        ZipUtils.unzip(outputFile.toString(), unzippedDirectory.toString());

        // Ensure resulting file exists
        assertTrue(Files.exists(unzippedFile));

        // And make sure they match the original input files
        assertTrue(Arrays.equals(Crypto.digest(inputFile.toFile()), Crypto.digest(unzippedFile.toFile())));

        // Unzipped files are placed within a folder named by the supplied enclosingFolderName
        Path unzippedInnerDirectory = Paths.get(unzippedDirectory.toString(), enclosingFolderName);

        // Finally, make sure the directory digests match
        ArbitraryDataDigest inputDirectoryDigest = new ArbitraryDataDigest(inputDirectory);
        inputDirectoryDigest.compute();
        ArbitraryDataDigest unzippedDirectoryDigest = new ArbitraryDataDigest(unzippedInnerDirectory);
        unzippedDirectoryDigest.compute();
        assertEquals(inputDirectoryDigest.getHash58(), unzippedDirectoryDigest.getHash58());
    }

    @Test
    public void testZipMultipleFiles() throws IOException, InterruptedException, DataException {
        String enclosingFolderName = "data";
        Path inputDirectory = Files.createTempDirectory("inputDirectory");
        Path outputDirectory = Files.createTempDirectory("outputDirectory");
        Path outputFile = Paths.get(outputDirectory.toString(), enclosingFolderName);
        inputDirectory.toFile().deleteOnExit();
        outputDirectory.toFile().deleteOnExit();

        Path inputFile1 = Paths.get(inputDirectory.toString(), "file1");
        Path inputFile2 = Paths.get(inputDirectory.toString(), "file2");

        // Write random data to some files
        byte[] data = new byte[1024];
        new Random().nextBytes(data);
        Files.write(inputFile1, data, StandardOpenOption.CREATE);
        Files.write(inputFile2, data, StandardOpenOption.CREATE);

        assertTrue(Files.exists(inputDirectory));
        assertTrue(Files.exists(inputFile1));
        assertTrue(Files.exists(inputFile2));
        assertFalse(Files.exists(outputFile));

        // Zip...
        ZipUtils.zip(inputDirectory.toString(), outputFile.toString(), enclosingFolderName);

        assertTrue(Files.exists(inputDirectory));
        assertTrue(Files.exists(outputFile));

        // Create paths for unzipping
        Path unzippedDirectory = Files.createTempDirectory("unzippedDirectory");
        unzippedDirectory.toFile().deleteOnExit();
        Path unzippedFile1 = Paths.get(unzippedDirectory.toString(), enclosingFolderName, "file1");
        Path unzippedFile2 = Paths.get(unzippedDirectory.toString(), enclosingFolderName, "file2");
        assertFalse(Files.exists(unzippedFile1));
        assertFalse(Files.exists(unzippedFile2));

        // Now unzip...
        ZipUtils.unzip(outputFile.toString(), unzippedDirectory.toString());

        // Ensure resulting files exist
        assertTrue(Files.exists(unzippedFile1));
        assertTrue(Files.exists(unzippedFile2));

        // And make sure they match the original input files
        assertTrue(Arrays.equals(Crypto.digest(inputFile1.toFile()), Crypto.digest(unzippedFile1.toFile())));
        assertTrue(Arrays.equals(Crypto.digest(inputFile2.toFile()), Crypto.digest(unzippedFile2.toFile())));

        // Unzipped files are placed within a folder named by the supplied enclosingFolderName
        Path unzippedInnerDirectory = Paths.get(unzippedDirectory.toString(), enclosingFolderName);

        // Finally, make sure the directory digests match
        ArbitraryDataDigest inputDirectoryDigest = new ArbitraryDataDigest(inputDirectory);
        inputDirectoryDigest.compute();
        ArbitraryDataDigest unzippedDirectoryDigest = new ArbitraryDataDigest(unzippedInnerDirectory);
        unzippedDirectoryDigest.compute();
        assertEquals(inputDirectoryDigest.getHash58(), unzippedDirectoryDigest.getHash58());
    }

}
