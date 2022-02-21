package org.qortal.data.arbitrary;

import org.qortal.network.Peer;
import java.util.Objects;

public class ArbitraryRelayInfo {

    private final String hash58;
    private final String signature58;
    private final Peer peer;
    private final Long timestamp;

    public ArbitraryRelayInfo(String hash58, String signature58, Peer peer, Long timestamp) {
        this.hash58 = hash58;
        this.signature58 = signature58;
        this.peer = peer;
        this.timestamp = timestamp;
    }

    public boolean isValid() {
        return this.getHash58() != null && this.getSignature58() != null
                && this.getPeer() != null && this.getTimestamp() != null;
    }

    public String getHash58() {
        return this.hash58;
    }

    public String getSignature58() {
        return signature58;
    }

    public Peer getPeer() {
        return peer;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("%s = %s, %s, %d", this.hash58, this.signature58, this.peer, this.timestamp);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof ArbitraryRelayInfo))
            return false;

        ArbitraryRelayInfo otherRelayInfo = (ArbitraryRelayInfo) other;

        return this.peer == otherRelayInfo.getPeer()
                && Objects.equals(this.hash58, otherRelayInfo.getHash58())
                && Objects.equals(this.signature58, otherRelayInfo.getSignature58());
    }
}
