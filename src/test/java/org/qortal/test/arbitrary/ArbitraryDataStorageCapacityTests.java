package org.qortal.test.arbitrary;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.arbitrary.ArbitraryDataCleanupManager;
import org.qortal.controller.arbitrary.ArbitraryDataManager;
import org.qortal.controller.arbitrary.ArbitraryDataStorageManager;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.list.ResourceListManager;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.test.common.ArbitraryUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.test.common.transaction.TestTransaction;
import org.qortal.transaction.RegisterNameTransaction;
import org.qortal.utils.Base58;
import org.qortal.utils.ListUtils;
import org.qortal.utils.NTP;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class ArbitraryDataStorageCapacityTests extends Common {

    @Before
    public void beforeTest() throws DataException, InterruptedException, IllegalAccessException {
        Common.useDefaultSettings();
        this.deleteDataDirectories();
        this.deleteListsDirectory();

        // Set difficulty to 1 to speed up the tests
        FieldUtils.writeField(ArbitraryDataManager.getInstance(), "powDifficulty", 1, true);
    }

    @After
    public void afterTest() throws DataException {
        this.deleteDataDirectories();
        this.deleteListsDirectory();
        ArbitraryDataStorageManager.getInstance().shutdown();
    }


    @Test
    public void testCalculateTotalStorageCapacity() {
        ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();
        double storageFullThreshold = 0.9; // 90%
        Long now = NTP.getTime();
        assertNotNull("NTP time must be synced", now);
        long expectedTotalStorageCapacity = Settings.getInstance().getMaxStorageCapacity();

        // Capacity isn't initially calculated
        assertNull(storageManager.getStorageCapacity());
        assertEquals(0L, storageManager.getTotalDirectorySize());
        assertFalse(storageManager.isStorageCapacityCalculated());

        // We need to calculate the directory size because we haven't yet
        assertTrue(storageManager.shouldCalculateDirectorySize(now));
        storageManager.calculateDirectorySize(now);
        assertTrue(storageManager.isStorageCapacityCalculated());

        // Storage capacity should equal the value specified in settings
        assertNotNull(storageManager.getStorageCapacity());
        assertEquals(expectedTotalStorageCapacity, storageManager.getStorageCapacity().longValue());

        // We shouldn't calculate storage capacity again so soon
        now += 9 * 60 * 1000L;
        assertFalse(storageManager.shouldCalculateDirectorySize(now));

        // ... but after 10 minutes we should recalculate
        now += 1 * 60 * 1000L + 1L;
        assertTrue(storageManager.shouldCalculateDirectorySize(now));
    }

    @Test
    public void testCalculateStorageCapacityPerName() {
        ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();
        ResourceListManager resourceListManager = ResourceListManager.getInstance();
        double storageFullThreshold = 0.9; // 90%
        Long now = NTP.getTime();
        assertNotNull("NTP time must be synced", now);

        // Capacity isn't initially calculated
        assertNull(storageManager.getStorageCapacity());
        assertEquals(0L, storageManager.getTotalDirectorySize());
        assertFalse(storageManager.isStorageCapacityCalculated());

        // We need to calculate the total directory size because we haven't yet
        assertTrue(storageManager.shouldCalculateDirectorySize(now));
        storageManager.calculateDirectorySize(now);
        assertTrue(storageManager.isStorageCapacityCalculated());

        // Storage capacity should initially equal the total
        assertEquals(0, resourceListManager.getItemCountForList("followedNames"));
        assertEquals(0, ListUtils.followedNamesCount());
        long totalStorageCapacity = storageManager.getStorageCapacityIncludingThreshold(storageFullThreshold);
        assertEquals(totalStorageCapacity, storageManager.storageCapacityPerName(storageFullThreshold));

        // Follow some names
        assertTrue(resourceListManager.addToList("followedNames", "Test1", false));
        assertTrue(resourceListManager.addToList("followedNames", "Test2", false));
        assertTrue(resourceListManager.addToList("followedNames", "Test3", false));
        assertTrue(resourceListManager.addToList("followedNames", "Test4", false));
        assertTrue(resourceListManager.addToList("followedNames", "Test5", false));
        assertTrue(resourceListManager.addToList("followedNames", "Test6", false));

        // Ensure the followed name count is correct
        assertEquals(6, resourceListManager.getItemCountForList("followedNames"));
        assertEquals(6, ListUtils.followedNamesCount());

        // Storage space per name should be the total storage capacity divided by the number of names
        // then multiplied by 4, to allow for names that don't use much space
        long expectedStorageCapacityPerName = (long)(totalStorageCapacity / 6.0f) * 4L;
        assertEquals(expectedStorageCapacityPerName, storageManager.storageCapacityPerName(storageFullThreshold));
    }


    private void deleteDataDirectories() {
        // Delete data directory if exists
        Path dataPath = Paths.get(Settings.getInstance().getDataPath());
        try {
            FileUtils.deleteDirectory(dataPath.toFile());
        } catch (IOException e) {

        }

        // Delete temp data directory if exists
        Path tempDataPath = Paths.get(Settings.getInstance().getTempDataPath());
        try {
            FileUtils.deleteDirectory(tempDataPath.toFile());
        } catch (IOException e) {

        }
    }

    private void deleteListsDirectory() {
        // Delete lists directory if exists
        Path listsPath = Paths.get(Settings.getInstance().getListsPath());
        try {
            FileUtils.deleteDirectory(listsPath.toFile());
        } catch (IOException e) {

        }
    }

}
