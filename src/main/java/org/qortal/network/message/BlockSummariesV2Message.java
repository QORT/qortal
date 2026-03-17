package org.qortal.network.message;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.transform.Transformer;
import org.qortal.transform.block.BlockTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BlockSummariesV2Message extends Message {

	public static final long MINIMUM_PEER_VERSION = 0x0300060001L;

	private static final int BLOCK_SUMMARY_V2_LENGTH = BlockTransformer.BLOCK_SIGNATURE_LENGTH /* block signature */
			+ Transformer.PUBLIC_KEY_LENGTH /* minter public key */
			+ Transformer.INT_LENGTH /* online accounts count */
			+ Transformer.LONG_LENGTH /* block timestamp */
			+ Transformer.INT_LENGTH /* transactions count */
			+ BlockTransformer.BLOCK_SIGNATURE_LENGTH; /* block reference */

	private List<BlockSummaryData> blockSummaries;

	public BlockSummariesV2Message(List<BlockSummaryData> blockSummaries) {
		super(MessageType.BLOCK_SUMMARIES_V2);

		// Shortcut for when there are no summaries
		if (blockSummaries.isEmpty()) {
			this.dataBytes = Message.EMPTY_DATA_BYTES;
			return;
		}

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// First summary's height
			bytes.write(Ints.toByteArray(blockSummaries.get(0).getHeight()));

			for (BlockSummaryData blockSummary : blockSummaries) {
				bytes.write(blockSummary.getSignature());
				bytes.write(blockSummary.getMinterPublicKey());
				bytes.write(Ints.toByteArray(blockSummary.getOnlineAccountsCount()));
				bytes.write(Longs.toByteArray(blockSummary.getTimestamp()));
				bytes.write(Ints.toByteArray(blockSummary.getTransactionCount()));
				bytes.write(blockSummary.getReference());
			}
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private BlockSummariesV2Message(int id, List<BlockSummaryData> blockSummaries) {
		super(id, MessageType.BLOCK_SUMMARIES_V2);

		this.blockSummaries = blockSummaries;
	}

	public List<BlockSummaryData> getBlockSummaries() {
		return this.blockSummaries;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		List<BlockSummaryData> blockSummaries = new ArrayList<>();

		// If there are no bytes remaining then we can treat this as an empty array of summaries
		if (bytes.remaining() == 0)
			return new BlockSummariesV2Message(id, blockSummaries);

		int height = bytes.getInt();

		// Expecting bytes remaining to be exact multiples of BLOCK_SUMMARY_V2_LENGTH
		if (bytes.remaining() % BLOCK_SUMMARY_V2_LENGTH != 0)
			throw new BufferUnderflowException();

		while (bytes.hasRemaining()) {
			byte[] signature = new byte[BlockTransformer.BLOCK_SIGNATURE_LENGTH];
			bytes.get(signature);

			byte[] minterPublicKey = new byte[Transformer.PUBLIC_KEY_LENGTH];
			bytes.get(minterPublicKey);

			int onlineAccountsCount = bytes.getInt();

			long timestamp = bytes.getLong();

			int transactionsCount = bytes.getInt();

			byte[] reference = new byte[BlockTransformer.BLOCK_SIGNATURE_LENGTH];
			bytes.get(reference);

			BlockSummaryData blockSummary = new BlockSummaryData(height, signature, minterPublicKey,
					onlineAccountsCount, timestamp, transactionsCount, reference);
			blockSummaries.add(blockSummary);

			height++;
		}

		return new BlockSummariesV2Message(id, blockSummaries);
	}

}
