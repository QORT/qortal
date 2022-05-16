package org.qortal.data.arbitrary;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryResourceStatus {

    public enum Status {
        PUBLISHED("Published", "Published but not yet downloaded"),
        DOWNLOADING("Downloading", "Locating and downloading files..."),
        DOWNLOADED("Downloaded", "Files downloaded"),
        BUILDING("Building", "Building..."),
        READY("Ready", "Ready"),
        MISSING_DATA("Missing data", "Unable to locate all files. Please try again later"),
        BUILD_FAILED("Build failed", "Build failed. Please try again later"),
        UNSUPPORTED("Unsupported", "Unsupported request"),
        BLOCKED("Blocked", "Name is blocked so content cannot be served");

        private String title;
        private String description;

        Status(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private String id;
    private String title;
    private String description;

    private Integer localChunkCount;
    private Integer totalChunkCount;

    public ArbitraryResourceStatus() {
    }

    public ArbitraryResourceStatus(Status status, Integer localChunkCount, Integer totalChunkCount) {
        this.id = status.toString();
        this.title = status.title;
        this.description = status.description;
        this.localChunkCount = localChunkCount;
        this.totalChunkCount = totalChunkCount;
    }

    public ArbitraryResourceStatus(Status status) {
        this(status, null, null);
    }
}
