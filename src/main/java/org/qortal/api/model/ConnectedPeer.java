package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.data.network.PeerChainTipData;
import org.qortal.data.network.PeerData;
import org.qortal.network.Handshake;
import org.qortal.network.Peer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@XmlAccessorType(XmlAccessType.FIELD)
public class ConnectedPeer {

    public enum Direction {
        INBOUND,
        OUTBOUND;
    }

    public Direction direction;
    public Handshake handshakeStatus;
    public Long lastPing;
    public Long connectedWhen;
    public Long peersConnectedWhen;

    public String address;
    public String version;

    public String nodeId;

    public Integer lastHeight;
    @Schema(example = "base58")
    public byte[] lastBlockSignature;
    public Long lastBlockTimestamp;
    public UUID connectionId;
    public String age;

    protected ConnectedPeer() {
    }

    public ConnectedPeer(Peer peer) {
        this.direction = peer.isOutbound() ? Direction.OUTBOUND : Direction.INBOUND;
        this.handshakeStatus = peer.getHandshakeStatus();
        this.lastPing = peer.getLastPing();

        PeerData peerData = peer.getPeerData();
        this.connectedWhen = peer.getConnectionTimestamp();
        this.peersConnectedWhen = peer.getPeersConnectionTimestamp();

        this.address = peerData.getAddress().toString();

        this.version = peer.getPeersVersionString();
        this.nodeId = peer.getPeersNodeId();
        this.connectionId = peer.getPeerConnectionId();
        if (peer.getConnectionEstablishedTime() > 0) {
            long age = (System.currentTimeMillis() - peer.getConnectionEstablishedTime());
            long minutes = TimeUnit.MILLISECONDS.toMinutes(age);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(age) - TimeUnit.MINUTES.toSeconds(minutes);
            this.age = String.format("%dm %ds", minutes, seconds);
        } else {
            this.age = "connecting...";
        }

        PeerChainTipData peerChainTipData = peer.getChainTipData();
        if (peerChainTipData != null) {
            this.lastHeight = peerChainTipData.getLastHeight();
            this.lastBlockSignature = peerChainTipData.getLastBlockSignature();
            this.lastBlockTimestamp = peerChainTipData.getLastBlockTimestamp();
        }
    }

}
