package org.qortal.event;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class DataMonitorEvent implements Event{
    private long timestamp;
    private String identifier;
    private String name;
    private String service;
    private String description;
    private long transactionTimestamp;
    private long latestPutTimestamp;

    public DataMonitorEvent() {
    }

    public DataMonitorEvent(long timestamp, String identifier, String name, String service, String description, long transactionTimestamp, long latestPutTimestamp) {

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
