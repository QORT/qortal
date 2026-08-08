package org.qortal.transform.block;

import org.qortal.data.at.ATStateData;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.TransactionData;

import java.util.List;

public class BlockTransformation {
    private final BlockData blockData;
    private final List<TransactionData> transactions;
    private final List<ATStateData> atStates;
    private final byte[] atStatesHash;

    /*package*/ BlockTransformation(BlockData blockData, List<TransactionData> transactions, List<ATStateData> atStates) {
        this.blockData = blockData;
        this.transactions = transactions;
        this.atStates = atStates;
        this.atStatesHash = null;
    }

    /*package*/ BlockTransformation(BlockData blockData, List<TransactionData> transactions, byte[] atStatesHash) {
        this.blockData = blockData;
        this.transactions = transactions;
        this.atStates = null;
        this.atStatesHash = atStatesHash;
    }

    public BlockData getBlockData() {
        return blockData;
    }

    public List<TransactionData> getTransactions() {
        return transactions;
    }

    public List<ATStateData> getAtStates() {
        return atStates;
    }

    public byte[] getAtStatesHash() {
        return atStatesHash;
    }
}
