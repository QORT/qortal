package org.qortal.list;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ResourceListManager {

    private static final Logger LOGGER = LogManager.getLogger(ResourceListManager.class);

    private static ResourceListManager instance;
    private List<ResourceList> lists = Collections.synchronizedList(new ArrayList<>());


    public ResourceListManager() {
    }

    public static synchronized ResourceListManager getInstance() {
        if (instance == null) {
            instance = new ResourceListManager();
        }
        return instance;
    }

    private ResourceList getList(String listName) {
        for (ResourceList list : this.lists) {
            if (Objects.equals(list.getName(), listName)) {
                return list;
            }
        }

        // List doesn't exist in array yet, so create it
        // This will load any existing data from the filesystem
        try {
            ResourceList list = new ResourceList(listName);
            this.lists.add(list);
            return list;

        } catch (IOException e) {
            LOGGER.info("Unable to load or create list {}: {}", listName, e.getMessage());
            return null;
        }

    }

    public boolean addToList(String listName, String item, boolean save) {
        ResourceList list = this.getList(listName);
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

    public boolean removeFromList(String listName, String item, boolean save) {
        ResourceList list = this.getList(listName);
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

    public boolean listContains(String listName, String item, boolean caseSensitive) {
        ResourceList list = this.getList(listName);
        if (list == null) {
            return false;
        }
        return list.contains(item, caseSensitive);
    }

    public void saveList(String listName) {
        ResourceList list = this.getList(listName);
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

    public void revertList(String listName) {
        ResourceList list = this.getList(listName);
        if (list == null) {
            return;
        }
        list.revert();
    }

    public String getJSONStringForList(String listName) {
        ResourceList list = this.getList(listName);
        if (list == null) {
            return null;
        }
        return list.getJSONString();
    }

    public List<String> getStringsInList(String listName) {
        ResourceList list = this.getList(listName);
        if (list == null) {
            return null;
        }
        return list.getList();
    }

    public int getItemCountForList(String listName) {
        ResourceList list = this.getList(listName);
        if (list == null) {
            return 0;
        }
        return list.getList().size();
    }

}
