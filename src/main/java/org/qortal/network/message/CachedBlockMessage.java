package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.qortal.block.Block;
import org.qortal.transform.TransformationException;
import org.qortal.transform.block.BlockTransformer;

import com.google.common.primitives.Ints;

// This is an OUTGOING-only Message which more readily lends itself to being cached
public class CachedBlockMessage extends Message {

	private Block block = null;
	private byte[] cachedBytes = null;

	public CachedBlockMessage(Block block) {
		super(MessageType.BLOCK);

		this.block = block;
	}

	public CachedBlockMessage(byte[] cachedBytes) {
		super(MessageType.BLOCK);

		this.block = null;
		this.cachedBytes = cachedBytes;
	}
	
	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws UnsupportedEncodingException {
		throw new UnsupportedOperationException("CachedBlockMessage is for outgoing messages only");
	}

	@Override
	protected byte[] toData() {
		// Already serialized?
		if (this.cachedBytes != null)
			return cachedBytes;

		if (this.block == null)
			return null;

		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(this.block.getBlockData().getHeight()));

			bytes.write(BlockTransformer.toBytes(this.block));

			this.cachedBytes = bytes.toByteArray();
			// We no longer need source Block
			// and Block contains repository handle which is highly likely to be invalid after this call
			this.block = null;

			return this.cachedBytes;
		} catch (TransformationException | IOException e) {
			return null;
		}
	}

	public CachedBlockMessage cloneWithNewId(int newId) {
		CachedBlockMessage clone = new CachedBlockMessage(this.cachedBytes);
		clone.setId(newId);
		return clone;
	}

}
