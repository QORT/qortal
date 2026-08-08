package org.qortal.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.api.DevProxyService;
import org.qortal.repository.DataException;
import org.qortal.settings.Settings;

public class DevProxyManager {

    protected static final Logger LOGGER = LogManager.getLogger(DevProxyManager.class);

    private static DevProxyManager instance;

    private boolean running = false;

    private String sourceHostAndPort = "127.0.0.1:5173"; // Default for React/Vite

    private DevProxyManager() {

    }

    public static DevProxyManager getInstance() {
        if (instance == null)
            instance = new DevProxyManager();

        return instance;
    }

    public void start() throws DataException {
        synchronized(this) {
            if (this.running) {
                // Already running
                return;
            }

            LOGGER.info(String.format("Starting developer proxy service on port %d", Settings.getInstance().getDevProxyPort()));
            DevProxyService devProxyService = DevProxyService.getInstance();
            devProxyService.start();
            this.running = true;
        }
    }

    public void stop() {
        synchronized(this) {
            if (!this.running) {
                // Not running
                return;
            }

            LOGGER.info(String.format("Shutting down developer proxy service"));
            DevProxyService devProxyService = DevProxyService.getInstance();
            devProxyService.stop();
            this.running = false;
        }
    }

    public void setSourceHostAndPort(String sourceHostAndPort) {
        this.sourceHostAndPort = sourceHostAndPort;
    }

    public String getSourceHostAndPort() {
        return this.sourceHostAndPort;
    }

    public Integer getPort() {
        return Settings.getInstance().getDevProxyPort();
    }

    public boolean isRunning() {
        return this.running;
    }

}
