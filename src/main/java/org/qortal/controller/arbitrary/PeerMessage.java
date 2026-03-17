package org.qortal.controller.arbitrary;

import org.qortal.network.Peer;
import org.qortal.network.message.Message;

public class PeerMessage {
    Peer peer;
    Message message;

    public PeerMessage(Peer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }

    public Peer getPeer() {
        return peer;
    }

    public Message getMessage() {
        return message;
    }
}
