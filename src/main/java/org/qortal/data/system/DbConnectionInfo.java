package org.qortal.data.system;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class DbConnectionInfo {

    private long updated;

    private String owner;

    private String state;

    public DbConnectionInfo() {
    }

    public DbConnectionInfo(long timeOpened, String owner, String state) {
        this.updated = timeOpened;
        this.owner = owner;
        this.state = state;
    }

    public long getUpdated() {
        return updated;
    }

    public String getOwner() {
        return owner;
    }

    public String getState() {
        return state;
    }
}
