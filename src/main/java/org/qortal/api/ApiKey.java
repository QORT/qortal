package org.qortal.api;

import org.qortal.settings.Settings;
import org.qortal.utils.Base58;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

public class ApiKey {

    private String apiKey;

    public ApiKey() throws IOException {
        this.load();
    }

    public void generate() throws IOException {
        byte[] apiKey = new byte[16];
        new SecureRandom().nextBytes(apiKey);
        this.apiKey = Base58.encode(apiKey);

        this.save();
    }


    /* Filesystem */

    private Path getFilePath() {
        return Paths.get(Settings.getInstance().getApiKeyPath(), "apikey.txt");
    }

    private boolean load() throws IOException {
        Path path = this.getFilePath();
        File apiKeyFile = new File(path.toString());
        if (!apiKeyFile.exists()) {
            // Try settings - to allow legacy API keys to be supported
            return this.loadLegacyApiKey();
        }

        try {
            this.apiKey = new String(Files.readAllBytes(path));

        } catch (IOException e) {
            throw new IOException(String.format("Couldn't read contents from file %s", path.toString()));
        }

        return true;
    }

    private boolean loadLegacyApiKey() {
        String legacyApiKey = Settings.getInstance().getApiKey();
        if (legacyApiKey != null && !legacyApiKey.isEmpty()) {
            this.apiKey = Settings.getInstance().getApiKey();

            try {
                // Save it to the apikey file
                this.save();
            } catch (IOException e) {
                // Ignore failures as it will be reloaded from settings next time
            }
            return true;
        }
        return false;
    }

    public void save() throws IOException {
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            throw new IllegalStateException("Unable to save a blank API key");
        }

        Path filePath = this.getFilePath();

        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toString()));
        writer.write(this.apiKey);
        writer.close();
    }

    public void delete() throws IOException {
        this.apiKey = null;

        Path filePath = this.getFilePath();
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
    }


    public boolean generated() {
        return (this.apiKey != null);
    }

    public boolean exists() {
        return this.getFilePath().toFile().exists();
    }

    @Override
    public String toString() {
        return this.apiKey;
    }

}
