package org.qortal.data.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class TransactionSummary {

    private String atAddress;
    private String p2shValue;
    private String p2shAddress;
    private String lockingHash;
    private Integer lockingTimestamp;
    private long lockingTotalAmount;
    private long lockingFee;
    private int lockingSize;
    private String unlockingHash;
    private Integer unlockingTimestamp;
    private long unlockingTotalAmount;
    private long unlockingFee;
    private int unlockingSize;

    public TransactionSummary(){}
    
    public TransactionSummary(
            String atAddress,
            String p2shValue,
            String p2shAddress,
            String lockingHash,
            Integer lockingTimestamp,
            long lockingTotalAmount,
            long lockingFee,
            int lockingSize,
            String unlockingHash,
            Integer unlockingTimestamp,
            long unlockingTotalAmount,
            long unlockingFee,
            int unlockingSize) {

        this.atAddress = atAddress;
        this.p2shValue = p2shValue;
        this.p2shAddress = p2shAddress;
        this.lockingHash = lockingHash;
        this.lockingTimestamp = lockingTimestamp;
        this.lockingTotalAmount = lockingTotalAmount;
        this.lockingFee = lockingFee;
        this.lockingSize = lockingSize;
        this.unlockingHash = unlockingHash;
        this.unlockingTimestamp = unlockingTimestamp;
        this.unlockingTotalAmount = unlockingTotalAmount;
        this.unlockingFee = unlockingFee;
        this.unlockingSize = unlockingSize;
    }

    public String getAtAddress() {
        return atAddress;
    }

    public String getP2shValue() {
        return p2shValue;
    }

    public String getP2shAddress() {
        return p2shAddress;
    }

    public String getLockingHash() {
        return lockingHash;
    }

    public Integer getLockingTimestamp() {
        return lockingTimestamp;
    }

    public long getLockingTotalAmount() {
        return lockingTotalAmount;
    }

    public long getLockingFee() {
        return lockingFee;
    }

    public int getLockingSize() {
        return lockingSize;
    }

    public String getUnlockingHash() {
        return unlockingHash;
    }

    public Integer getUnlockingTimestamp() {
        return unlockingTimestamp;
    }

    public long getUnlockingTotalAmount() {
        return unlockingTotalAmount;
    }

    public long getUnlockingFee() {
        return unlockingFee;
    }

    public int getUnlockingSize() {
        return unlockingSize;
    }
}
