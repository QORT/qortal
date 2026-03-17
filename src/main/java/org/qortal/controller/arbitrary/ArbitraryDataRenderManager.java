package org.qortal.controller.arbitrary;

import org.qortal.arbitrary.ArbitraryDataResource;
import org.qortal.utils.NTP;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ArbitraryDataRenderManager extends Thread {

    private static ArbitraryDataRenderManager instance;
    private volatile boolean isStopping = false;

    /**
     * Map to keep track of authorized resources for rendering.
     * Keyed by resource ID, with the authorization time as the value.
     */
    private Map<String, Long> authorizedResources = Collections.synchronizedMap(new HashMap<>());

    private static long AUTHORIZATION_TIMEOUT = 60 * 60 * 1000L; // 1 hour


    public ArbitraryDataRenderManager() {

    }

    public static ArbitraryDataRenderManager getInstance() {
        if (instance == null)
            instance = new ArbitraryDataRenderManager();

        return instance;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Arbitrary Data Render Manager");
        Thread.currentThread().setPriority(NORM_PRIORITY);

        try {
            while (!isStopping) {
                Thread.sleep(60000);

                Long now = NTP.getTime();
                this.cleanup(now);
            }
        } catch (InterruptedException e) {
            // Fall-through to exit thread...
        }
    }

    public void shutdown() {
        isStopping = true;
        this.interrupt();
    }

    public void cleanup(Long now) {
        if (now == null) {
            return;
        }
        final long minimumTimestamp = now - AUTHORIZATION_TIMEOUT;
        this.authorizedResources.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < minimumTimestamp);
    }

    public boolean isAuthorized(ArbitraryDataResource resource) {
        ArbitraryDataResource broadResource = new ArbitraryDataResource(resource.getResourceId(), null, null, null);

        for (String authorizedResourceKey : this.authorizedResources.keySet()) {
            if (authorizedResourceKey != null && resource != null) {
                // Check for exact match
                if (Objects.equals(authorizedResourceKey, resource.getUniqueKey())) {
                    return true;
                }
                // Check for a broad authorization (which applies to all services and identifiers under an authorized name)
                if (Objects.equals(authorizedResourceKey, broadResource.getUniqueKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void addToAuthorizedResources(ArbitraryDataResource resource) {
        if (!this.isAuthorized(resource)) {
            this.authorizedResources.put(resource.getUniqueKey(), NTP.getTime());
        }
    }

}
