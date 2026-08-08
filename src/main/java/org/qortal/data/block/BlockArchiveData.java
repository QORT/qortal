package org.qortal.data.block;

public class BlockArchiveData {

	// Properties
	private byte[] signature;
	private Integer height;
	private Long timestamp;
	private byte[] minterPublicKey;

	// Constructors

	public BlockArchiveData(byte[] signature, Integer height, long timestamp, byte[] minterPublicKey) {
		this.signature = signature;
		this.height = height;
		this.timestamp = timestamp;
		this.minterPublicKey = minterPublicKey;
	}

	public BlockArchiveData(BlockData blockData) {
		this.signature = blockData.getSignature();
		this.height = blockData.getHeight();
		this.timestamp = blockData.getTimestamp();
		this.minterPublicKey = blockData.getMinterPublicKey();
	}

	// Getters/setters

	public byte[] getSignature() {
		return this.signature;
	}

	public Integer getHeight() {
		return this.height;
	}

	public Long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getMinterPublicKey() {
		return this.minterPublicKey;
	}

}
