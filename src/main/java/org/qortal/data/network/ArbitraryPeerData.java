package org.qortal.data.network;

import com.google.common.net.InetAddresses;
import org.qortal.crypto.Crypto;
import org.qortal.network.Peer;
import org.qortal.utils.NTP;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ArbitraryPeerData {

    private final byte[] hash;
    private final String peerAddress;
    private Integer successes;
    private Integer failures;
    private Long lastAttempted;
    private Long lastRetrieved;

    public ArbitraryPeerData(byte[] hash, String peerAddress, Integer successes,
                             Integer failures, Long lastAttempted, Long lastRetrieved) {
        this.hash = hash;
        this.peerAddress = peerAddress;
        this.successes = successes;
        this.failures = failures;
        this.lastAttempted = lastAttempted;
        this.lastRetrieved = lastRetrieved;
    }

    public ArbitraryPeerData(byte[] signature, Peer peer) {
        this(Crypto.digest(signature), peer.getPeerData().getAddress().toString(),
                0, 0, 0L, 0L);
    }

    public ArbitraryPeerData(byte[] signature, String peerAddress) {
        this(Crypto.digest(signature), peerAddress, 0, 0, 0L, 0L);
    }

    public boolean isPeerAddressValid() {
        // Validate the peer address to prevent arbitrary values being added to the db
        String[] parts = this.peerAddress.split(":");
        if (parts.length != 2) {
            // Invalid format
            return false;
        }
        String host = parts[0];
        if (!InetAddresses.isInetAddress(host)) {
            // Invalid host
            return false;
        }
        int port = Integer.valueOf(parts[1]);
        if (port <= 0 || port > 65535) {
            // Invalid port
            return false;
        }

        // Make sure that it's not a local address
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                // Ignore local addresses
                return false;
            }
        } catch (UnknownHostException e) {
            return false;
        }

        // Valid host/port combination
        return true;
    }

    public void incrementSuccesses() {
        this.successes++;
    }

    public void incrementFailures() {
        this.failures++;
    }

    public void markAsAttempted() {
        this.lastAttempted = NTP.getTime();
    }

    public void markAsRetrieved() {
        this.lastRetrieved = NTP.getTime();
    }

    public byte[] getHash() {
        return this.hash;
    }

    public String getPeerAddress() {
        return this.peerAddress;
    }

    public Integer getSuccesses() {
        return this.successes;
    }

    public Integer getFailures() {
        return this.failures;
    }

    public Long getLastAttempted() {
        return this.lastAttempted;
    }

    public Long getLastRetrieved() {
        return this.lastRetrieved;
    }

}
