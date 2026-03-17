package org.qortal.test.minting;

import org.junit.Before;
import org.junit.Test;
import org.qortal.block.Block;
import org.qortal.data.block.BlockData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.transform.Transformer;
import org.qortal.utils.NTP;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BlockTimestampTests extends Common {

    private static class BlockTimestampDataPoint {
        public byte[] minterPublicKey;
        public int minterAccountLevel;
        public long blockTimestamp;
    }

    private static final Random RANDOM = new Random();

    @Before
    public void beforeTest() throws DataException {
        Common.useSettings("test-settings-v2-block-timestamps.json");
        NTP.setFixedOffset(0L);
    }

    @Test
    public void testTimestamps() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            Block parentBlock = BlockUtils.mintBlock(repository);
            BlockData parentBlockData = parentBlock.getBlockData();

            // Generate lots of test minters
            List<BlockTimestampDataPoint> dataPoints = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                BlockTimestampDataPoint dataPoint = new BlockTimestampDataPoint();

                dataPoint.minterPublicKey = new byte[Transformer.PUBLIC_KEY_LENGTH];
                RANDOM.nextBytes(dataPoint.minterPublicKey);

                dataPoint.minterAccountLevel = RANDOM.nextInt(5) + 5;

                dataPoint.blockTimestamp = Block.calcTimestamp(parentBlockData, dataPoint.minterPublicKey, dataPoint.minterAccountLevel);

                System.out.printf("[%d] level %d, blockTimestamp %d - parentTimestamp %d = %d%n",
                        i,
                        dataPoint.minterAccountLevel,
                        dataPoint.blockTimestamp,
                        parentBlockData.getTimestamp(),
                        dataPoint.blockTimestamp - parentBlockData.getTimestamp()
                );
            }
        }
    }
}
