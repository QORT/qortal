package org.qortal.crosschain;

import java.util.Objects;

public class ChainableServerConnection {

    private ChainableServer server;
    private String requestedBy;
    private boolean open;
    private boolean success;
    private long currentTimeMillis;
    private String notes;

    public ChainableServerConnection(ChainableServer server, String requestedBy, boolean open, boolean success, long currentTimeMillis, String notes) {
        this.server = server;
        this.requestedBy = requestedBy;
        this.open = open;
        this.success = success;
        this.currentTimeMillis = currentTimeMillis;
        this.notes = notes;
    }

    public ChainableServer getServer() {
        return server;
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

    public long getCurrentTimeMillis() {
        return currentTimeMillis;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChainableServerConnection that = (ChainableServerConnection) o;
        return currentTimeMillis == that.currentTimeMillis && Objects.equals(server, that.server);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, currentTimeMillis);
    }

    @Override
    public String toString() {
        return "ChainableServerConnection{" +
                "server=" + server +
                ", requestedBy='" + requestedBy + '\'' +
                ", open=" + open +
                ", success=" + success +
                ", currentTimeMillis=" + currentTimeMillis +
                ", notes='" + notes + '\'' +
                '}';
    }
}
