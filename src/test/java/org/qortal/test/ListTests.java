package org.qortal.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.list.ResourceList;
import org.qortal.list.ResourceListManager;
import org.qortal.repository.DataException;
import org.qortal.settings.Settings;
import org.qortal.test.common.Common;
import org.qortal.utils.ListUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

public class ListTests {

    @Before
    public void beforeTest() throws DataException, IOException {
        Common.useDefaultSettings();
        this.cleanup();
    }

    @After
    public void afterTest() throws DataException, IOException {
        this.cleanup();
    }

    private void cleanup() throws IOException {
        // Delete custom lists created by test methods
        ResourceList followedNamesTestList = new ResourceList("followedNames_test");
        followedNamesTestList.clear();
        followedNamesTestList.save();

        ResourceList blockedNamesTestList = new ResourceList("blockedNames_test");
        blockedNamesTestList.clear();
        blockedNamesTestList.save();

        // Clear resource list manager instance
        ResourceListManager.reset();
    }

    @Test
    public void testSingleList() {
        ResourceListManager resourceListManager = ResourceListManager.getInstance();
        String listName = "followedNames_test";
        String name = "testName";

        resourceListManager.addToList(listName, name, false);

        List<String> followedNames = resourceListManager.getStringsInList(listName);
        assertEquals(1, followedNames.size());
        assertEquals(followedNames.size(), ListUtils.followedNamesCount());
        assertEquals(name, followedNames.get(0));
    }

    @Test
    public void testListPrefix() {
        ResourceListManager resourceListManager = ResourceListManager.getInstance();

        List<String> initialFollowedNames = resourceListManager.getStringsInListsWithPrefix("followedNames");
        assertEquals(0, initialFollowedNames.size());

        List<String> initialBlockedNames = resourceListManager.getStringsInListsWithPrefix("blockedNames");
        assertEquals(0, initialBlockedNames.size());

        // Add to multiple lists
        resourceListManager.addToList("followedNames_CustomList1", "testName1", false);
        resourceListManager.addToList("followedNames_CustomList1", "testName2", false);
        resourceListManager.addToList("followedNames_CustomList2", "testName3", false);
        resourceListManager.addToList("followedNames_CustomList3", "testName4", false);
        resourceListManager.addToList("blockedNames_CustomList1", "testName5", false);

        // Check followedNames
        List<String> followedNames = resourceListManager.getStringsInListsWithPrefix("followedNames");
        assertEquals(4, followedNames.size());
        assertEquals(followedNames.size(), ListUtils.followedNamesCount());
        assertTrue(followedNames.contains("testName1"));
        assertTrue(followedNames.contains("testName2"));
        assertTrue(followedNames.contains("testName3"));
        assertTrue(followedNames.contains("testName4"));
        assertFalse(followedNames.contains("testName5"));

        // Check blockedNames
        List<String> blockedNames = resourceListManager.getStringsInListsWithPrefix("blockedNames");
        assertEquals(1, blockedNames.size());
        assertEquals(blockedNames.size(), ListUtils.blockedNames().size());
        assertTrue(blockedNames.contains("testName5"));
    }

    @Test
    public void testDataPersistence() {
        // Ensure lists are empty to begin with
        assertEquals(0, ResourceListManager.getInstance().getStringsInListsWithPrefix("followedNames").size());
        assertEquals(0, ResourceListManager.getInstance().getStringsInListsWithPrefix("blockedNames").size());

        // Add some items to multiple lists
        ResourceListManager.getInstance().addToList("followedNames_test", "testName1", true);
        ResourceListManager.getInstance().addToList("followedNames_test", "testName2", true);
        ResourceListManager.getInstance().addToList("blockedNames_test", "testName3", true);

        // Ensure they are added
        assertEquals(2, ResourceListManager.getInstance().getStringsInListsWithPrefix("followedNames").size());
        assertEquals(1, ResourceListManager.getInstance().getStringsInListsWithPrefix("blockedNames").size());

        // Clear local state
        ResourceListManager.reset();

        // Ensure items are automatically loaded back in from disk
        assertEquals(2, ResourceListManager.getInstance().getStringsInListsWithPrefix("followedNames").size());
        assertEquals(1, ResourceListManager.getInstance().getStringsInListsWithPrefix("blockedNames").size());

        // Delete followedNames file
        File followedNamesFile = Paths.get(Settings.getInstance().getListsPath(), "followedNames_test.json").toFile();
        followedNamesFile.delete();

        // Clear local state again
        ResourceListManager.reset();

        // Ensure only the blocked names are loaded back in
        assertEquals(0, ResourceListManager.getInstance().getStringsInListsWithPrefix("followedNames").size());
        assertEquals(1, ResourceListManager.getInstance().getStringsInListsWithPrefix("blockedNames").size());
    }

}
