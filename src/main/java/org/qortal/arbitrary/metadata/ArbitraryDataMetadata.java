package org.qortal.arbitrary.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.qortal.repository.DataException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ArbitraryDataMetadata
 *
 * This is a base class to handle reading and writing JSON to the supplied filePath.
 *
 * It is not usable on its own; it must be subclassed, with two methods overridden:
 *
 * readJson() - code to unserialize the JSON file
 * buildJson() - code to serialize the JSON file
 *
 */
public class ArbitraryDataMetadata {

    protected static final Logger LOGGER = LogManager.getLogger(ArbitraryDataMetadata.class);

    protected Path filePath;

    protected String jsonString;

    public ArbitraryDataMetadata(Path filePath) {
        this.filePath = filePath;
    }

    protected void readJson() throws DataException, JSONException {
        // To be overridden
    }

    protected void buildJson() {
        // To be overridden
    }


    public void read() throws IOException, DataException {
        try {
            this.loadJson();
            this.readJson();

        } catch (JSONException e) {
            throw new DataException(String.format("Unable to read JSON at path %s: %s", this.filePath, e.getMessage()));
        }
    }

    public void write() throws IOException, DataException {
        this.buildJson();
        this.createParentDirectories();

        BufferedWriter writer = new BufferedWriter(new FileWriter(this.filePath.toString()));
        writer.write(this.jsonString);
        writer.newLine();
        writer.close();
    }

    public void delete() throws IOException {
        Files.delete(this.filePath);
    }


    protected void loadJson() throws IOException {
        File metadataFile = new File(this.filePath.toString());
        if (!metadataFile.exists()) {
            throw new IOException(String.format("Metadata file doesn't exist: %s", this.filePath.toString()));
        }

        this.jsonString = new String(Files.readAllBytes(this.filePath), StandardCharsets.UTF_8);
    }


    protected void createParentDirectories() throws DataException {
        try {
            Files.createDirectories(this.filePath.getParent());
        } catch (IOException e) {
            throw new DataException("Unable to create parent directories");
        }
    }


    public String getJsonString() {
        return this.jsonString;
    }

}
