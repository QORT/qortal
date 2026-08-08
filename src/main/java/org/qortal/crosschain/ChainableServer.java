package org.qortal.crosschain;

public interface ChainableServer {
    public void addResponseTime(long responseTime);

    public long averageResponseTime();

    public String getHostName();

    public int getPort();

    public ConnectionType getConnectionType();

    public enum ConnectionType {TCP, SSL}
}
