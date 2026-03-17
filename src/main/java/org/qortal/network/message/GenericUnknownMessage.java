package org.qortal.network.message;

import java.nio.ByteBuffer;

public class GenericUnknownMessage extends Message {

    public static final long MINIMUM_PEER_VERSION = 0x0300060001L;

    public GenericUnknownMessage() {
        super(MessageType.GENERIC_UNKNOWN);

        this.dataBytes = EMPTY_DATA_BYTES;
    }

    private GenericUnknownMessage(int id) {
        super(id, MessageType.GENERIC_UNKNOWN);
    }

    public static Message fromByteBuffer(int id, ByteBuffer bytes) {
        return new GenericUnknownMessage(id);
    }

}
