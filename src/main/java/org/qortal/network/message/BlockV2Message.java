package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.block.Block;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transform.TransformationException;
import org.qortal.transform.block.BlockTransformation;
import org.qortal.transform.block.BlockTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class BlockV2Message extends Message {

	private static final Logger LOGGER = LogManager.getLogger(BlockV2Message.class);
	public static final long MIN_PEER_VERSION = 0x300030003L; // 3.3.3

	private BlockData blockData;
	private List<TransactionData> transactions;
	private byte[] atStatesHash;

	public BlockV2Message(Block block) throws TransformationException {
		super(MessageType.BLOCK_V2);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(Ints.toByteArray(block.getBlockData().getHeight()));

			bytes.write(BlockTransformer.toBytesV2(block));
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public BlockV2Message(byte[] cachedBytes) {
		super(MessageType.BLOCK_V2);

		this.dataBytes = cachedBytes;
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private BlockV2Message(int id, BlockData blockData, List<TransactionData> transactions, byte[] atStatesHash) {
		super(id, MessageType.BLOCK_V2);

		this.blockData = blockData;
		this.transactions = transactions;
		this.atStatesHash = atStatesHash;
	}

	public BlockData getBlockData() {
		return this.blockData;
	}

	public List<TransactionData> getTransactions() {
		return this.transactions;
	}

	public byte[] getAtStatesHash() {
		return this.atStatesHash;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException {
		try {
			int height = byteBuffer.getInt();

			BlockTransformation blockTransformation = BlockTransformer.fromByteBufferV2(byteBuffer);

			BlockData blockData = blockTransformation.getBlockData();
			blockData.setHeight(height);

			return new BlockV2Message(id, blockData, blockTransformation.getTransactions(), blockTransformation.getAtStatesHash());
		} catch (TransformationException e) {
			LOGGER.info(String.format("Received garbled BLOCK_V2 message: %s", e.getMessage()));
			throw new MessageException(e.getMessage(), e);
		}
	}

}
