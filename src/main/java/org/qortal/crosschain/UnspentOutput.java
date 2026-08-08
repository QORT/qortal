package org.qortal.crosschain;

/** Unspent output info as returned by ElectrumX network. */
public class UnspentOutput {
	public final byte[] hash;
	public final int index;
	public final int height;
	public final long value;

	// Optional fields returned by Pirate Light Client server
	public final byte[] script;
	public final String address;

	public UnspentOutput(byte[] hash, int index, int height, long value, byte[] script, String address) {
		this.hash = hash;
		this.index = index;
		this.height = height;
		this.value = value;
		this.script = script;
		this.address = address;
	}

	public UnspentOutput(byte[] hash, int index, int height, long value) {
		this(hash, index, height, value, null, null);
	}
}