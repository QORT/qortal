package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.block.Block;
import org.qortal.data.at.ATStateData;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transform.TransformationException;
import org.qortal.transform.block.BlockTransformer;
import org.qortal.utils.Triple;

import com.google.common.primitives.Ints;

public class BlockMessage extends Message {

	private static final Logger LOGGER = LogManager.getLogger(BlockMessage.class);

	private Block block = null;

	private BlockData blockData = null;
	private List<TransactionData> transactions = null;
	private List<ATStateData> atStates = null;

	private int height;

	public BlockMessage(Block block) {
		super(MessageType.BLOCK);

		this.block = block;
		this.blockData = block.getBlockData();
		this.height = block.getBlockData().getHeight();
	}

	private BlockMessage(int id, BlockData blockData, List<TransactionData> transactions, List<ATStateData> atStates) {
		super(id, MessageType.BLOCK);

		this.blockData = blockData;
		this.transactions = transactions;
		this.atStates = atStates;

		this.height = blockData.getHeight();
	}

	public BlockData getBlockData() {
		return this.blockData;
	}

	public List<TransactionData> getTransactions() {
		return this.transactions;
	}

	public List<ATStateData> getAtStates() {
		return this.atStates;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws UnsupportedEncodingException {
		try {
			int height = byteBuffer.getInt();

			Triple<BlockData, List<TransactionData>, List<ATStateData>> blockInfo = BlockTransformer.fromByteBuffer(byteBuffer);

			BlockData blockData = blockInfo.getA();
			blockData.setHeight(height);

			return new BlockMessage(id, blockData, blockInfo.getB(), blockInfo.getC());
		} catch (TransformationException e) {
			LOGGER.info(String.format("Received garbled BLOCK message: %s", e.getMessage()));
			return null;
		}
	}

	@Override
	protected byte[] toData() {
		if (this.block == null)
			return null;

		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(this.height));

			bytes.write(BlockTransformer.toBytes(this.block));

			return bytes.toByteArray();
		} catch (TransformationException | IOException e) {
			return null;
		}
	}

	public BlockMessage cloneWithNewId(int newId) {
		BlockMessage clone = new BlockMessage(this.block);
		clone.setId(newId);
		return clone;
	}

}
