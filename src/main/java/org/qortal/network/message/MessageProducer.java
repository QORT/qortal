package org.qortal.network.message;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface MessageProducer {
    Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException;
}
