package org.qortal.test.arbitrary;

import org.junit.Before;
import org.junit.Test;
import org.qortal.arbitrary.ArbitraryDataDigest;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.*;

public class ArbitraryDataDigestTests extends Common {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @Test
    public void testDirectoryDigest() throws IOException, DataException {
        Path dataPath = Paths.get("src/test/resources/arbitrary/demo1");
        String expectedHash58 = "DKyMuonWKoneJqiVHgw26Vk1ytrZG9PGsE9xfBg3GKDp";

        // Ensure directory exists
        assertTrue(dataPath.toFile().exists());
        assertTrue(dataPath.toFile().isDirectory());

        // Compute a hash
        ArbitraryDataDigest digest = new ArbitraryDataDigest(dataPath);
        digest.compute();
        assertEquals(expectedHash58, digest.getHash58());

        // Write a random file to .qortal/cache to ensure it isn't being included in the digest function
        // We exclude all .qortal files from the digest since they can be different with each build, and
        // we only care about the actual user files
        Path cachePath = Paths.get(dataPath.toString(), ".qortal", "cache");
        Files.createDirectories(cachePath.getParent());
        FileWriter fileWriter = new FileWriter(cachePath.toString());
        fileWriter.append(UUID.randomUUID().toString());
        fileWriter.close();

        // Recompute the hash
        digest = new ArbitraryDataDigest(dataPath);
        digest.compute();
        assertEquals(expectedHash58, digest.getHash58());

        // Now compute the hash 100 more times to ensure it's always the same
        for (int i=0; i<100; i++) {
            digest = new ArbitraryDataDigest(dataPath);
            digest.compute();
            assertEquals(expectedHash58, digest.getHash58());
        }
    }

}
