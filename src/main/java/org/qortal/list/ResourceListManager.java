package org.qortal.list;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ResourceListManager {

    private static final Logger LOGGER = LogManager.getLogger(ResourceListManager.class);

    private static ResourceListManager instance;
    private List<ResourceList> lists = new ArrayList<>();


    public ResourceListManager() {
    }

    public static synchronized ResourceListManager getInstance() {
        if (instance == null) {
            instance = new ResourceListManager();
        }
        return instance;
    }

    private ResourceList getList(String category, String resourceName) {
        for (ResourceList list : this.lists) {
            if (Objects.equals(list.getCategory(), category) &&
                    Objects.equals(list.getResourceName(), resourceName)) {
                return list;
            }
        }

        // List doesn't exist in array yet, so create it
        // This will load any existing data from the filesystem
        try {
            ResourceList list = new ResourceList(category, resourceName);
            this.lists.add(list);
            return list;

        } catch (IOException e) {
            LOGGER.info("Unable to load or create list {} {}: {}", category, resourceName, e.getMessage());
            return null;
        }

    }

    public boolean addToList(String category, String resourceName, String item, boolean save) {
        ResourceList list = this.getList(category, resourceName);
        if (list == null) {
            return false;
        }

        try {
            list.add(item);
            if (save) {
                list.save();
            }
            return true;

        } catch (IllegalStateException | IOException e) {
            LOGGER.info(String.format("Unable to add item %s to list %s", item, list), e);
            return false;
        }
    }

    public boolean removeFromList(String category, String resourceName, String item, boolean save) {
        ResourceList list = this.getList(category, resourceName);
        if (list == null) {
            return false;
        }

        try {
            list.remove(item);

            if (save) {
                list.save();
            }
            return true;

        } catch (IllegalStateException | IOException e) {
            LOGGER.info(String.format("Unable to remove item %s from list %s", item, list), e);
            return false;
        }
    }

    public boolean listContains(String category, String resourceName, String address) {
        ResourceList list = this.getList(category, resourceName);
        if (list == null) {
            return false;
        }
        return list.contains(address);
    }

    public void saveList(String category, String resourceName) {
        ResourceList list = this.getList(category, resourceName);
        if (list == null) {
            return;
        }

        try {
            list.save();
        } catch (IOException e) {
            LOGGER.info("Unable to save list {} - reverting back to last saved state", list);
            list.revert();
        }
    }

    public void revertList(String category, String resourceName) {
        ResourceList list = this.getList(category, resourceName);
        if (list == null) {
            return;
        }
        list.revert();
    }

    public String getJSONStringForList(String category, String resourceName) {
        ResourceList list = this.getList(category, resourceName);
        if (list == null) {
            return null;
        }
        return list.getJSONString();
    }

}
