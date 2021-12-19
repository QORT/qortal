package org.qortal.list;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.qortal.settings.Settings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ResourceList {

    private static final Logger LOGGER = LogManager.getLogger(ResourceList.class);

    private String category;
    private String resourceName;
    private List<String> list = new ArrayList<>();

    /**
     * ResourceList
     * Creates or updates a list for the purpose of tracking resources on the Qortal network
     * This can be used for local blocking, or even for curating and sharing content lists
     * Lists are backed off to JSON files (in the lists folder) to ease sharing between nodes and users
     *
     * @param category - for instance "blacklist", "whitelist", or "userlist"
     * @param resourceName - for instance "address", "poll", or "group"
     * @throws IOException
     */
    public ResourceList(String category, String resourceName) throws IOException {
        this.category = category;
        this.resourceName = resourceName;
        this.load();
    }


    /* Filesystem */

    private Path getFilePath() {
        String pathString = String.format("%s%s%s_%s.json", Settings.getInstance().getListsPath(),
                File.separator, this.category, this.resourceName);
        return Paths.get(pathString);
    }

    public void save() throws IOException {
        if (this.resourceName == null) {
            throw new IllegalStateException("Can't save list with missing resource name");
        }
        if (this.category == null) {
            throw new IllegalStateException("Can't save list with missing category");
        }
        String jsonString = ResourceList.listToJSONString(this.list);
        Path filePath = this.getFilePath();

        // Create parent directory if needed
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create lists directory");
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toString()));
        writer.write(jsonString);
        writer.close();
    }

    private boolean load() throws IOException {
        Path path = this.getFilePath();
        File resourceListFile = new File(path.toString());
        if (!resourceListFile.exists()) {
            return false;
        }

        try {
            String jsonString = new String(Files.readAllBytes(path));
            this.list = ResourceList.listFromJSONString(jsonString);
        } catch (IOException e) {
            throw new IOException(String.format("Couldn't read contents from file %s", path.toString()));
        }

        return true;
    }

    public boolean revert() {
        try {
            return this.load();
        } catch (IOException e) {
            LOGGER.info("Unable to revert {} {}", this.resourceName, this.category);
        }
        return false;
    }


    /* List management */

    public void add(String resource) {
        if (resource == null || this.list == null) {
            return;
        }
        if (!this.contains(resource)) {
            this.list.add(resource);
        }
    }

    public void remove(String resource) {
        if (resource == null || this.list == null) {
            return;
        }
        this.list.remove(resource);
    }

    public boolean contains(String resource) {
        if (resource == null || this.list == null) {
            return false;
        }
        return this.list.contains(resource);
    }


    /* Utils */

    public static String listToJSONString(List<String> list) {
        if (list == null) {
            return null;
        }
        JSONArray items = new JSONArray();
        for (String item : list) {
            items.put(item);
        }
        return items.toString(4);
    }

    private static List<String> listFromJSONString(String jsonString) {
        if (jsonString == null) {
            return null;
        }
        JSONArray jsonList = new JSONArray(jsonString);
        List<String> resourceList = new ArrayList<>();
        for (int i=0; i<jsonList.length(); i++) {
            String item = (String)jsonList.get(i);
            resourceList.add(item);
        }
        return resourceList;
    }

    public String getJSONString() {
        return ResourceList.listToJSONString(this.list);
    }

    public String getCategory() {
        return this.category;
    }

    public String getResourceName() {
        return this.resourceName;
    }

    public String toString() {
        return String.format("%s %s", this.category, this.resourceName);
    }

}
