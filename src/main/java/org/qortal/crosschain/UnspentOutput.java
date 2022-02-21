package org.qortal.crosschain;

/** Unspent output info as returned by ElectrumX network. */
public class UnspentOutput {
	public final byte[] hash;
	public final int index;
	public final int height;
	public final long value;

	public UnspentOutput(byte[] hash, int index, int height, long value) {
		this.hash = hash;
		this.index = index;
		this.height = height;
		this.value = value;
	}
}