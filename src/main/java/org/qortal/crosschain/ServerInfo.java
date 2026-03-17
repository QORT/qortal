package org.qortal.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
public class ServerInfo {

        private long averageResponseTime;

        private String hostName;

        private int port;

        private String connectionType;

        private boolean isCurrent;

        public ServerInfo() {
        }

        public ServerInfo(long averageResponseTime, String hostName, int port, String connectionType, boolean isCurrent) {
                this.averageResponseTime = averageResponseTime;
                this.hostName = hostName;
                this.port = port;
                this.connectionType = connectionType;
                this.isCurrent = isCurrent;
        }

        public long getAverageResponseTime() {
                return averageResponseTime;
        }

        public String getHostName() {
                return hostName;
        }

        public int getPort() {
                return port;
        }

        public String getConnectionType() {
                return connectionType;
        }

        public boolean isCurrent() {
                return isCurrent;
        }

        @Override
        public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                ServerInfo that = (ServerInfo) o;
                return averageResponseTime == that.averageResponseTime && port == that.port && isCurrent == that.isCurrent && Objects.equals(hostName, that.hostName) && Objects.equals(connectionType, that.connectionType);
        }

        @Override
        public int hashCode() {
                return Objects.hash(averageResponseTime, hostName, port, connectionType, isCurrent);
        }

        @Override
        public String toString() {
                return "ServerInfo{" +
                        "averageResponseTime=" + averageResponseTime +
                        ", hostName='" + hostName + '\'' +
                        ", port=" + port +
                        ", connectionType='" + connectionType + '\'' +
                        ", isCurrent=" + isCurrent +
                        '}';
        }
}
