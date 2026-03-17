package org.qortal.data.crosschain;

import org.qortal.crosschain.BitcoinyTransaction;
import org.qortal.crosschain.TransactionHash;

import java.util.List;
import java.util.Map;

public class AtomicTransactionData {
    public final TransactionHash hash;
    public final Integer timestamp;
    public final List<BitcoinyTransaction.Input> inputs;
    public final Map<List<String>, Long> valueByAddress;
    public final long totalAmount;
    public final int size;

    public AtomicTransactionData(
            TransactionHash hash,
            Integer timestamp,
            List<BitcoinyTransaction.Input> inputs,
            Map<List<String>, Long> valueByAddress,
            long totalAmount,
            int size) {
        
        this.hash = hash;
        this.timestamp = timestamp;
        this.inputs = inputs;
        this.valueByAddress = valueByAddress;
        this.totalAmount = totalAmount;
        this.size = size;
    }
}
