package org.qortal.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
public class ServerConnectionInfo {

    private ServerInfo serverInfo;

    private String requestedBy;

    private boolean open;

    private boolean success;

    private long timeInMillis;

    private String notes;

    public ServerConnectionInfo() {
    }

    public ServerConnectionInfo(ServerInfo serverInfo, String requestedBy, boolean open, boolean success, long timeInMillis, String notes) {
        this.serverInfo = serverInfo;
        this.requestedBy = requestedBy;
        this.open = open;
        this.success = success;
        this.timeInMillis = timeInMillis;
        this.notes = notes;
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getTimeInMillis() {
        return timeInMillis;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerConnectionInfo that = (ServerConnectionInfo) o;
        return timeInMillis == that.timeInMillis && Objects.equals(serverInfo, that.serverInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverInfo, timeInMillis);
    }

    @Override
    public String toString() {
        return "ServerConnectionInfo{" +
                "serverInfo=" + serverInfo +
                ", requestedBy='" + requestedBy + '\'' +
                ", open=" + open +
                ", success=" + success +
                ", timeInMillis=" + timeInMillis +
                ", notes='" + notes + '\'' +
                '}';
    }
}
