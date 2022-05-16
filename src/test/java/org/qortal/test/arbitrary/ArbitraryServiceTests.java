package org.qortal.test.arbitrary;

import org.junit.Before;
import org.junit.Test;
import org.qortal.arbitrary.misc.Service;
import org.qortal.arbitrary.misc.Service.ValidationResult;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import static org.junit.Assert.*;

public class ArbitraryServiceTests extends Common {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @Test
    public void testDefaultValidation() throws IOException {
        // We don't validate the ARBITRARY_DATA service specifically, so we can use it to test the default validation method
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write to temp path
        Path path = Files.createTempFile("testDefaultValidation", null);
        path.toFile().deleteOnExit();
        Files.write(path, data, StandardOpenOption.CREATE);

        Service service = Service.ARBITRARY_DATA;
        assertFalse(service.isValidationRequired());
        // Test validation anyway to ensure that no exception is thrown
        assertEquals(ValidationResult.OK, service.validate(path));
    }

    @Test
    public void testValidateWebsite() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateWebsite");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "index.html"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data2"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data3"), data, StandardOpenOption.CREATE);

        Service service = Service.WEBSITE;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.OK, service.validate(path));
    }

    @Test
    public void testValidateWebsiteWithoutIndexFile() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateWebsiteWithoutIndexFile");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "data1.html"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data2"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data3"), data, StandardOpenOption.CREATE);

        Service service = Service.WEBSITE;
        assertTrue(service.isValidationRequired());

        // There is no index file in the root
        assertEquals(ValidationResult.MISSING_INDEX_FILE, service.validate(path));
    }

    @Test
    public void testValidateWebsiteWithoutIndexFileInRoot() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateWebsiteWithoutIndexFileInRoot");
        path.toFile().deleteOnExit();
        Files.createDirectories(Paths.get(path.toString(), "directory"));
        Files.write(Paths.get(path.toString(), "directory", "index.html"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data2"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data3"), data, StandardOpenOption.CREATE);

        Service service = Service.WEBSITE;
        assertTrue(service.isValidationRequired());

        // There is no index file in the root
        assertEquals(ValidationResult.MISSING_INDEX_FILE, service.validate(path));
    }

    @Test
    public void testValidQortalMetadata() throws IOException {
        // Metadata is to describe an arbitrary resource (title, description, tags, etc)
        String dataString = "{\"title\":\"Test Title\", \"description\":\"Test description\", \"tags\":[\"test\"]}";

        // Write to temp path
        Path path = Files.createTempFile("testValidQortalMetadata", null);
        path.toFile().deleteOnExit();
        Files.write(path, dataString.getBytes(), StandardOpenOption.CREATE);

        Service service = Service.QORTAL_METADATA;
        assertTrue(service.isValidationRequired());
        assertEquals(ValidationResult.OK, service.validate(path));
    }

    @Test
    public void testQortalMetadataMissingKeys() throws IOException {
        // Metadata is to describe an arbitrary resource (title, description, tags, etc)
        String dataString = "{\"description\":\"Test description\", \"tags\":[\"test\"]}";

        // Write to temp path
        Path path = Files.createTempFile("testQortalMetadataMissingKeys", null);
        path.toFile().deleteOnExit();
        Files.write(path, dataString.getBytes(), StandardOpenOption.CREATE);

        Service service = Service.QORTAL_METADATA;
        assertTrue(service.isValidationRequired());
        assertEquals(ValidationResult.MISSING_KEYS, service.validate(path));
    }

    @Test
    public void testQortalMetadataTooLarge() throws IOException {
        // Metadata is to describe an arbitrary resource (title, description, tags, etc)
        String dataString = "{\"title\":\"Test Title\", \"description\":\"Test description\", \"tags\":[\"test\"]}";

        // Generate some large data to go along with it
        int largeDataSize = 11*1024; // Larger than allowed 10kiB
        byte[] largeData = new byte[largeDataSize];
        new Random().nextBytes(largeData);

        // Write to temp path
        Path path = Files.createTempDirectory("testQortalMetadataTooLarge");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "data"), dataString.getBytes(), StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "large_data"), largeData, StandardOpenOption.CREATE);

        Service service = Service.QORTAL_METADATA;
        assertTrue(service.isValidationRequired());
        assertEquals(ValidationResult.EXCEEDS_SIZE_LIMIT, service.validate(path));
    }

    @Test
    public void testMultipleFileMetadata() throws IOException {
        // Metadata is to describe an arbitrary resource (title, description, tags, etc)
        String dataString = "{\"title\":\"Test Title\", \"description\":\"Test description\", \"tags\":[\"test\"]}";

        // Generate some large data to go along with it
        int otherDataSize = 1024; // Smaller than 10kiB limit
        byte[] otherData = new byte[otherDataSize];
        new Random().nextBytes(otherData);

        // Write to temp path
        Path path = Files.createTempDirectory("testMultipleFileMetadata");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "data"), dataString.getBytes(), StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "other_data"), otherData, StandardOpenOption.CREATE);

        Service service = Service.QORTAL_METADATA;
        assertTrue(service.isValidationRequired());

        // There are multiple files, so we don't know which one to parse as JSON
        assertEquals(ValidationResult.MISSING_KEYS, service.validate(path));
    }

}
