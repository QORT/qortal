package org.qortal.data.arbitrary;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryResourceStatus {

    public enum Status {
        // Note: integer values must not be updated, as they are stored in the db
        PUBLISHED(1, "Published", "Published but not yet downloaded"),
        NOT_PUBLISHED(2, "Not published", "Resource does not exist"),
        DOWNLOADING(3, "Downloading", "Locating and downloading files..."),
        DOWNLOADED(4, "Downloaded", "Files downloaded"),
        BUILDING(5, "Building", "Building..."),
        READY(6, "Ready", "Ready"),
        MISSING_DATA(7, "Missing data", "Unable to locate all files. Please try again later"),
        BUILD_FAILED(8, "Build failed", "Build failed. Please try again later"),
        UNSUPPORTED(9, "Unsupported", "Unsupported request"),
        BLOCKED(10, "Blocked", "Name is blocked so content cannot be served");

        public int value;
        private String title;
        private String description;

        private static final Map<Integer, Status> map = stream(Status.values())
                .collect(toMap(status -> status.value, status -> status));

        Status(int value, String title, String description) {
            this.value = value;
            this.title = title;
            this.description = description;
        }

        public static Status valueOf(Integer value) {
            if (value == null) {
                return null;
            }
            return map.get(value);
        }
    }

    private Status status;
    private String id;
    private String title;
    private String description;

    private Integer localChunkCount;
    private Integer totalChunkCount;
    private Float percentLoaded;

    public ArbitraryResourceStatus() {
    }

    public ArbitraryResourceStatus(Status status, Integer localChunkCount, Integer totalChunkCount) {
        this.status = status;
        this.id = status.toString();
        this.title = status.title;
        this.description = status.description;
        this.localChunkCount = localChunkCount;
        this.totalChunkCount = totalChunkCount;
        this.percentLoaded = (this.localChunkCount != null && this.totalChunkCount != null && this.totalChunkCount > 0 && this.totalChunkCount >= this.localChunkCount) ? this.localChunkCount / (float)this.totalChunkCount * 100.0f : null;
    }

    public ArbitraryResourceStatus(Status status) {
        this(status, null, null);
    }

    public Status getStatus() {
        return this.status;
    }

    public String getTitle() {
        return this.title;
    }

    public Integer getLocalChunkCount() {
        return this.localChunkCount;
    }

    public Integer getTotalChunkCount() {
        return this.totalChunkCount;
    }
}
