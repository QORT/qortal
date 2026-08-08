package org.qortal.test;

import com.google.common.hash.HashCode;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.Block;
import org.qortal.controller.BlockMinter;
import org.qortal.crosschain.AcctMode;
import org.qortal.crosschain.LitecoinACCTv3;
import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.data.block.BlockData;
import org.qortal.data.crosschain.CrossChainTradeData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AtUtils;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.MessageTransaction;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PruneTests extends Common {

    // Constants for test AT (an LTC ACCT)
    public static final byte[] litecoinPublicKeyHash = HashCode.fromString("bb00bb11bb22bb33bb44bb55bb66bb77bb88bb99").asBytes();
    public static final int tradeTimeout = 20; // blocks
    public static final long redeemAmount = 80_40200000L;
    public static final long fundingAmount = 123_45600000L;
    public static final long litecoinAmount = 864200L; // 0.00864200 LTC

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
            repository.getATRepository().rebuildLatestAtStates(5);
            repository.saveChanges();
            int numATStatesPruned = repository.getATRepository().pruneAtStates(0, 5);
            assertEquals(3, numATStatesPruned);
            repository.getATRepository().setAtPruneHeight(6);

            // Make sure that blocks 2-4 are now missing block data and AT states data
            for (Integer i=2; i <= 4; i++) {
                BlockData blockData = repository.getBlockRepository().fromHeight(i);
                assertNull(blockData);
                List<ATStateData> atStatesDataList = repository.getATRepository().getBlockATStatesAtHeight(i);
                assertTrue(atStatesDataList.isEmpty());
            }

            // Block 5 should have full AT states data even though it was pruned.
            // This is because we identified that as the "latest" AT state in that block range
            BlockData blockData = repository.getBlockRepository().fromHeight(5);
            assertNull(blockData);
            List<ATStateData> atStatesDataList = repository.getATRepository().getBlockATStatesAtHeight(5);
            assertEquals(1, atStatesDataList.size());

            // Blocks 6-10 have block data and full AT states data
            for (Integer i=6; i <= 10; i++) {
                blockData = repository.getBlockRepository().fromHeight(i);
                assertNotNull(blockData.getSignature());
                atStatesDataList = repository.getATRepository().getBlockATStatesAtHeight(i);
                assertNotNull(atStatesDataList);
                assertFalse(atStatesDataList.isEmpty());
                ATStateData atStatesData = repository.getATRepository().getATStateAtHeight(atStatesDataList.get(0).getATAddress(), i);
                assertNotNull(atStatesData.getStateHash());
                assertNotNull(atStatesData.getStateData());
            }
        }
    }

    @Test
    public void testPruneSleepingAt() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {
            PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
            PrivateKeyAccount tradeAccount = Common.getTestAccount(repository, "alice");

            DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
            Account at = deployAtTransaction.getATAccount();
            String atAddress = at.getAddress();

            // Mint enough blocks to take the original DEPLOY_AT past the prune threshold (in this case 20)
            Block block = BlockUtils.mintBlocks(repository, 25);

            // Send creator's address to AT, instead of typical partner's address
            byte[] messageData = LitecoinACCTv3.getInstance().buildCancelMessage(deployer.getAddress());
            long txTimestamp = block.getBlockData().getTimestamp();
            MessageTransaction messageTransaction = sendMessage(repository, deployer, messageData, atAddress, txTimestamp);

            // AT should process 'cancel' message in next block
            BlockUtils.mintBlock(repository);

            // Prune AT states up to block 20
            repository.getATRepository().rebuildLatestAtStates(20);
            repository.saveChanges();
            int numATStatesPruned = repository.getATRepository().pruneAtStates(0, 20);
            assertEquals(1, numATStatesPruned); // deleted state at heights 2, but state at height 3 remains

            // Check AT is finished
            ATData atData = repository.getATRepository().fromATAddress(atAddress);
            assertTrue(atData.getIsFinished());

            // AT should be in CANCELLED mode
            CrossChainTradeData tradeData = LitecoinACCTv3.getInstance().populateTradeData(repository, atData);
            assertEquals(AcctMode.CANCELLED, tradeData.mode);

            // Test orphaning - should be possible because the previous AT state at height 3 is still available
            BlockUtils.orphanLastBlock(repository);
        }
    }


    // Helper methods for AT testing
    private DeployAtTransaction doDeploy(Repository repository, PrivateKeyAccount deployer, String tradeAddress) throws DataException {
        byte[] creationBytes = LitecoinACCTv3.buildQortalAT(tradeAddress, litecoinPublicKeyHash, redeemAmount, litecoinAmount, tradeTimeout);

        long txTimestamp = System.currentTimeMillis();
        byte[] lastReference = deployer.getLastReference();

        if (lastReference == null) {
            System.err.println(String.format("Qortal account %s has no last reference", deployer.getAddress()));
            System.exit(2);
        }

        Long fee = null;
        String name = "QORT-LTC cross-chain trade";
        String description = String.format("Qortal-Litecoin cross-chain trade");
        String atType = "ACCT";
        String tags = "QORT-LTC ACCT";

        BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, deployer.getPublicKey(), fee, null);
        TransactionData deployAtTransactionData = new DeployAtTransactionData(baseTransactionData, name, description, atType, tags, creationBytes, fundingAmount, Asset.QORT);

        DeployAtTransaction deployAtTransaction = new DeployAtTransaction(repository, deployAtTransactionData);

        fee = deployAtTransaction.calcRecommendedFee();
        deployAtTransactionData.setFee(fee);

        TransactionUtils.signAndMint(repository, deployAtTransactionData, deployer);

        return deployAtTransaction;
    }

    private MessageTransaction sendMessage(Repository repository, PrivateKeyAccount sender, byte[] data, String recipient, long txTimestamp) throws DataException {
        byte[] lastReference = sender.getLastReference();

        if (lastReference == null) {
            System.err.println(String.format("Qortal account %s has no last reference", sender.getAddress()));
            System.exit(2);
        }

        Long fee = null;
        int version = 4;
        int nonce = 0;
        long amount = 0;
        Long assetId = null; // because amount is zero

        BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, sender.getPublicKey(), fee, null);
        TransactionData messageTransactionData = new MessageTransactionData(baseTransactionData, version, nonce, recipient, amount, assetId, data, false, false);

        MessageTransaction messageTransaction = new MessageTransaction(repository, messageTransactionData);

        fee = messageTransaction.calcRecommendedFee();
        messageTransactionData.setFee(fee);

        TransactionUtils.signAndMint(repository, messageTransactionData, sender);

        return messageTransaction;
    }
}
