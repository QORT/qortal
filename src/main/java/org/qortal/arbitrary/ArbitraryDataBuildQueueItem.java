package org.qortal.arbitrary;

import org.qortal.arbitrary.exception.MissingDataException;
import org.qortal.arbitrary.ArbitraryDataFile.*;
import org.qortal.arbitrary.misc.Service;
import org.qortal.repository.DataException;
import org.qortal.utils.NTP;

import java.io.IOException;

public class ArbitraryDataBuildQueueItem extends ArbitraryDataResource {

    private final Long creationTimestamp;
    private Long buildStartTimestamp = null;
    private Long buildEndTimestamp = null;
    private Integer priority = 0;
    private boolean failed = false;

    private static int HIGH_PRIORITY_THRESHOLD = 5;

    /* The maximum amount of time to spend on a single build */
    // TODO: interrupt an in-progress build
    public static long BUILD_TIMEOUT = 60*1000L; // 60 seconds
    /* The amount of time to remember that a build has failed, to avoid retries */
    public static long FAILURE_TIMEOUT = 5*60*1000L; // 5 minutes

    public ArbitraryDataBuildQueueItem(String resourceId, ResourceIdType resourceIdType, Service service, String identifier) {
        super(resourceId, resourceIdType, service, identifier);

        this.creationTimestamp = NTP.getTime();
    }

    public void prepareForBuild() {
        this.buildStartTimestamp = NTP.getTime();
    }

    public void build() throws IOException, DataException, MissingDataException {
        Long now = NTP.getTime();
        if (now == null) {
            this.buildStartTimestamp = null;
            throw new DataException("NTP time hasn't synced yet");
        }

        if (this.buildStartTimestamp == null) {
            this.buildStartTimestamp = now;
        }
        ArbitraryDataReader arbitraryDataReader =
                new ArbitraryDataReader(this.resourceId, this.resourceIdType, this.service, this.identifier);

        try {
            arbitraryDataReader.loadSynchronously(true);
        } finally {
            this.buildEndTimestamp = NTP.getTime();
        }
    }

    public boolean isBuilding() {
        return this.buildStartTimestamp != null;
    }

    public boolean isQueued() {
        return this.buildStartTimestamp == null;
    }

    public boolean hasReachedBuildTimeout(Long now) {
        if (now == null || this.creationTimestamp == null) {
            return true;
        }
        return now - this.creationTimestamp > BUILD_TIMEOUT;
    }

    public boolean hasReachedFailureTimeout(Long now) {
        if (now == null || this.buildStartTimestamp == null) {
            return true;
        }
        return now - this.buildStartTimestamp > FAILURE_TIMEOUT;
    }

    public Long getBuildStartTimestamp() {
        return this.buildStartTimestamp;
    }

    public Integer getPriority() {
        if (this.priority != null) {
            return this.priority;
        }
        return 0;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public boolean isHighPriority() {
        return this.priority >= HIGH_PRIORITY_THRESHOLD;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }
}
