package org.qortal.crosschain;

import java.util.Comparator;

public class TransactionHash {

	public static final Comparator<TransactionHash> CONFIRMED_FIRST = (a, b) -> Boolean.compare(a.height != 0, b.height != 0);

	public final int height;
	public final String txHash;

	public TransactionHash(int height, String txHash) {
		this.height = height;
		this.txHash = txHash;
	}

	public int getHeight() {
		return this.height;
	}

	public String getTxHash() {
		return this.txHash;
	}

	public String toString() {
		return this.height == 0
				? String.format("txHash %s (unconfirmed)", this.txHash)
				: String.format("txHash %s (height %d)", this.txHash, this.height);
	}

}