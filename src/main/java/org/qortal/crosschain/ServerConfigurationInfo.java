package org.qortal.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.List;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
public class ServerConfigurationInfo {

    private List<ServerInfo> servers;
    private List<ServerInfo> remainingServers;
    private List<ServerInfo> uselessServers;

    public ServerConfigurationInfo() {
    }

    public ServerConfigurationInfo(
            List<ServerInfo> servers,
            List<ServerInfo> remainingServers,
            List<ServerInfo> uselessServers) {
        this.servers = servers;
        this.remainingServers = remainingServers;
        this.uselessServers = uselessServers;
    }

    public List<ServerInfo> getServers() {
        return servers;
    }

    public List<ServerInfo> getRemainingServers() {
        return remainingServers;
    }

    public List<ServerInfo> getUselessServers() {
        return uselessServers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerConfigurationInfo that = (ServerConfigurationInfo) o;
        return Objects.equals(servers, that.servers) && Objects.equals(remainingServers, that.remainingServers) && Objects.equals(uselessServers, that.uselessServers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(servers, remainingServers, uselessServers);
    }

    @Override
    public String toString() {
        return "ServerConfigurationInfo{" +
                "servers=" + servers +
                ", remainingServers=" + remainingServers +
                ", uselessServers=" + uselessServers +
                '}';
    }
}
