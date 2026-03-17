package org.qortal.arbitrary.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.repository.DataException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ArbitraryDataQortalMetadata
 *
 * This is a base class to handle reading and writing JSON to a .qortal folder
 * within the supplied filePath. This is used when storing data against an existing
 * arbitrary data file structure.
 *
 * It is not usable on its own; it must be subclassed, with three methods overridden:
 *
 * fileName() - the file name to use within the .qortal folder
 * readJson() - code to unserialize the JSON file
 * buildJson() - code to serialize the JSON file
 *
 */
public class ArbitraryDataQortalMetadata extends ArbitraryDataMetadata {

    protected static final Logger LOGGER = LogManager.getLogger(ArbitraryDataQortalMetadata.class);

    protected Path filePath;
    protected Path qortalDirectoryPath;

    protected String jsonString;

    public ArbitraryDataQortalMetadata(Path filePath) {
        super(filePath);

        this.qortalDirectoryPath = Paths.get(filePath.toString(), ".qortal");
    }

    protected String fileName() {
        // To be overridden
        return null;
    }


    @Override
    public void write() throws IOException, DataException {
        this.buildJson();
        this.createParentDirectories();
        this.createQortalDirectory();

        Path patchPath = Paths.get(this.qortalDirectoryPath.toString(), this.fileName());
        BufferedWriter writer = new BufferedWriter(new FileWriter(patchPath.toString()));
        writer.write(this.jsonString);
        writer.newLine();
        writer.close();
    }

    @Override
    protected void loadJson() throws IOException {
        Path path = Paths.get(this.qortalDirectoryPath.toString(), this.fileName());
        File patchFile = new File(path.toString());
        if (!patchFile.exists()) {
            throw new IOException(String.format("Patch file doesn't exist: %s", path.toString()));
        }

        this.jsonString = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }


    protected void createQortalDirectory() throws DataException {
        try {
            Files.createDirectories(this.qortalDirectoryPath);
        } catch (IOException e) {
            throw new DataException("Unable to create .qortal directory");
        }
    }

}
