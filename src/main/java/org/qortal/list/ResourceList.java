package org.qortal.list;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.qortal.settings.Settings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResourceList {

    private static final Logger LOGGER = LogManager.getLogger(ResourceList.class);

    private String name;
    private List<String> list = Collections.synchronizedList(new ArrayList<>());

    /**
     * ResourceList
     * Creates or updates a list for the purpose of tracking resources on the Qortal network
     * This can be used for local blocking, or even for curating and sharing content lists
     * Lists are backed off to JSON files (in the lists folder) to ease sharing between nodes and users
     *
     * @param name - the name of the list, for instance "blockedAddresses"
     * @throws IOException
     */
    public ResourceList(String name) throws IOException {
        this.name = name;
        this.load();
    }


    /* Filesystem */

    private Path getFilePath() {
        String pathString = String.format("%s.json", Paths.get(Settings.getInstance().getListsPath(), this.name));
        return Paths.get(pathString);
    }

    public void save() throws IOException {
        if (this.name == null) {
            throw new IllegalStateException("Can't save list with missing name");
        }
        String jsonString = ResourceList.listToJSONString(this.list);
        Path filePath = this.getFilePath();

        // Don't create list if it's empty
        if (this.list != null && this.list.isEmpty()) {
            if (filePath != null && Files.exists(filePath)) {
                // Delete empty list
                Files.delete(filePath);
            }
            return;
        }

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
            String jsonString = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
            LOGGER.info("Unable to revert list {}: {}", this.name, e.getMessage());
        }
        return false;
    }


    /* List management */

    public void add(String resource) {
        if (resource == null || this.list == null) {
            return;
        }
        if (!this.contains(resource, true)) {
            this.list.add(resource);
        }
    }

    public void remove(String resource) {
        if (resource == null || this.list == null) {
            return;
        }
        this.list.remove(resource);
    }

    public void clear() {
        if (this.list == null) {
            return;
        }
        this.list.clear();
    }

    public boolean contains(String resource, boolean caseSensitive) {
        if (resource == null || this.list == null) {
            return false;
        }

        if (caseSensitive) {
            return this.list.contains(resource);
        }
        else {
            return this.list.stream().anyMatch(resource::equalsIgnoreCase);
        }
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

    public String getName() {
        return this.name;
    }

    public List<String> getList() {
        return this.list;
    }

    public String toString() {
        return this.name;
    }

}
