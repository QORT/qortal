package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.block.Block;
import org.qortal.transform.TransformationException;
import org.qortal.transform.block.BlockTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

// This is an OUTGOING-only Message which more readily lends itself to being cached
public class CachedBlockMessage extends Message implements Cloneable {

	public CachedBlockMessage(Block block) throws TransformationException {
		super(MessageType.BLOCK);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(Ints.toByteArray(block.getBlockData().getHeight()));

			bytes.write(BlockTransformer.toBytes(block));
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public CachedBlockMessage(byte[] cachedBytes) {
		super(MessageType.BLOCK);

		this.dataBytes = cachedBytes;
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		throw new UnsupportedOperationException("CachedBlockMessage is for outgoing messages only");
	}

}
