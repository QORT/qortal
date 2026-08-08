package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.transform.Transformer;
import org.qortal.transform.block.BlockTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BlockSummariesMessage extends Message {

	private static final int BLOCK_SUMMARY_LENGTH = BlockTransformer.BLOCK_SIGNATURE_LENGTH + Transformer.INT_LENGTH + Transformer.PUBLIC_KEY_LENGTH + Transformer.INT_LENGTH;

	private List<BlockSummaryData> blockSummaries;

	public BlockSummariesMessage(List<BlockSummaryData> blockSummaries) {
		super(MessageType.BLOCK_SUMMARIES);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(Ints.toByteArray(blockSummaries.size()));

			for (BlockSummaryData blockSummary : blockSummaries) {
				bytes.write(Ints.toByteArray(blockSummary.getHeight()));
				bytes.write(blockSummary.getSignature());
				bytes.write(blockSummary.getMinterPublicKey());
				bytes.write(Ints.toByteArray(blockSummary.getOnlineAccountsCount()));
			}
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private BlockSummariesMessage(int id, List<BlockSummaryData> blockSummaries) {
		super(id, MessageType.BLOCK_SUMMARIES);

		this.blockSummaries = blockSummaries;
	}

	public List<BlockSummaryData> getBlockSummaries() {
		return this.blockSummaries;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		int count = bytes.getInt();

		if (bytes.remaining() < count * BLOCK_SUMMARY_LENGTH)
			throw new BufferUnderflowException();

		List<BlockSummaryData> blockSummaries = new ArrayList<>();
		for (int i = 0; i < count; ++i) {
			int height = bytes.getInt();

			byte[] signature = new byte[BlockTransformer.BLOCK_SIGNATURE_LENGTH];
			bytes.get(signature);

			byte[] minterPublicKey = new byte[Transformer.PUBLIC_KEY_LENGTH];
			bytes.get(minterPublicKey);

			int onlineAccountsCount = bytes.getInt();

			BlockSummaryData blockSummary = new BlockSummaryData(height, signature, minterPublicKey, onlineAccountsCount);
			blockSummaries.add(blockSummary);
		}

		return new BlockSummariesMessage(id, blockSummaries);
	}

}
