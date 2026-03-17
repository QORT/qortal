package org.qortal.data.arbitrary;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class DataMonitorInfo {
    private long timestamp;
    private String identifier;
    private String name;
    private String service;
    private String description;
    private long transactionTimestamp;
    private long latestPutTimestamp;

    public DataMonitorInfo() {
    }

    public DataMonitorInfo(long timestamp, String identifier, String name, String service, String description, long transactionTimestamp, long latestPutTimestamp) {

        this.timestamp = timestamp;
        this.identifier = identifier;
        this.name = name;
        this.service = service;
        this.description = description;
        this.transactionTimestamp = transactionTimestamp;
        this.latestPutTimestamp = latestPutTimestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public String getService() {
        return service;
    }

    public String getDescription() {
        return description;
    }

    public long getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public long getLatestPutTimestamp() {
        return latestPutTimestamp;
    }
}
