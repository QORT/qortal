package org.qortal.test;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.controller.BlockMinter;
import org.qortal.data.at.ATStateData;
import org.qortal.data.block.BlockData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AtUtils;
import org.qortal.test.common.Common;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PruneTests extends Common {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @Test
    public void testPruning() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Alice self share online
            List<PrivateKeyAccount> mintingAndOnlineAccounts = new ArrayList<>();
            PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
            mintingAndOnlineAccounts.add(aliceSelfShare);

            // Deploy an AT so that we have AT state data
            PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");
            byte[] creationBytes = AtUtils.buildSimpleAT();
            long fundingAmount = 1_00000000L;
            AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);

            // Mint some blocks
            for (int i = 2; i <= 10; i++)
                BlockMinter.mintTestingBlock(repository, mintingAndOnlineAccounts.toArray(new PrivateKeyAccount[0]));

            // Make sure that all blocks have full AT state data and data hash
            for (Integer i=2; i <= 10; i++) {
                BlockData blockData = repository.getBlockRepository().fromHeight(i);
                assertNotNull(blockData.getSignature());
                assertEquals(i, blockData.getHeight());
                List<ATStateData> atStatesDataList = repository.getATRepository().getBlockATStatesAtHeight(i);
                assertNotNull(atStatesDataList);
                assertFalse(atStatesDataList.isEmpty());
                ATStateData atStatesData = repository.getATRepository().getATStateAtHeight(atStatesDataList.get(0).getATAddress(), i);
                assertNotNull(atStatesData.getStateHash());
                assertNotNull(atStatesData.getStateData());
            }

            // Prune blocks 2-5
            int numBlocksPruned = repository.getBlockRepository().pruneBlocks(0, 5);
            assertEquals(4, numBlocksPruned);
            repository.getBlockRepository().setBlockPruneHeight(6);

            // Prune AT states for blocks 2-5
            int numATStatesPruned = repository.getATRepository().pruneAtStates(0, 5);
            assertEquals(4, numATStatesPruned);
            repository.getATRepository().setAtPruneHeight(6);

            // Make sure that blocks 2-5 are now missing block data and AT states data
            for (Integer i=2; i <= 5; i++) {
                BlockData blockData = repository.getBlockRepository().fromHeight(i);
                assertNull(blockData);
                List<ATStateData> atStatesDataList = repository.getATRepository().getBlockATStatesAtHeight(i);
                assertTrue(atStatesDataList.isEmpty());
            }

            // ... but blocks 6-10 have block data and full AT states data
            for (Integer i=6; i <= 10; i++) {
                BlockData blockData = repository.getBlockRepository().fromHeight(i);
                assertNotNull(blockData.getSignature());
                List<ATStateData> atStatesDataList = repository.getATRepository().getBlockATStatesAtHeight(i);
                assertNotNull(atStatesDataList);
                assertFalse(atStatesDataList.isEmpty());
                ATStateData atStatesData = repository.getATRepository().getATStateAtHeight(atStatesDataList.get(0).getATAddress(), i);
                assertNotNull(atStatesData.getStateHash());
                assertNotNull(atStatesData.getStateData());
            }
        }
    }

}
