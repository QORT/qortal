package org.qortal.data.block;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Arrays;

@XmlAccessorType(XmlAccessType.FIELD)
public class BlockSummaryData {

	// Properties
	private int height;
	private byte[] signature;
	private byte[] minterPublicKey;
	private int onlineAccountsCount;

	// Optional, set during construction
	private Long timestamp;
	private Integer transactionCount;

	// Optional, set after construction
	private Integer minterLevel;

	// Constructors

	protected BlockSummaryData() {
	}

	public BlockSummaryData(int height, byte[] signature, byte[] minterPublicKey, int onlineAccountsCount) {
		this.height = height;
		this.signature = signature;
		this.minterPublicKey = minterPublicKey;
		this.onlineAccountsCount = onlineAccountsCount;
	}

	public BlockSummaryData(int height, byte[] signature, byte[] minterPublicKey, int onlineAccountsCount, long timestamp, int transactionCount) {
		this.height = height;
		this.signature = signature;
		this.minterPublicKey = minterPublicKey;
		this.onlineAccountsCount = onlineAccountsCount;
		this.timestamp = timestamp;
		this.transactionCount = transactionCount;
	}

	public BlockSummaryData(BlockData blockData) {
		this.height = blockData.getHeight();
		this.signature = blockData.getSignature();
		this.minterPublicKey = blockData.getMinterPublicKey();
		this.onlineAccountsCount = blockData.getOnlineAccountsCount();

		this.timestamp = blockData.getTimestamp();
		this.transactionCount = blockData.getTransactionCount();
	}

	// Getters / setters

	public int getHeight() {
		return this.height;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public byte[] getMinterPublicKey() {
		return this.minterPublicKey;
	}

	public int getOnlineAccountsCount() {
		return this.onlineAccountsCount;
	}

	public Long getTimestamp() {
		return this.timestamp;
	}

	public Integer getTransactionCount() {
		return this.transactionCount;
	}

	public Integer getMinterLevel() {
		return this.minterLevel;
	}

	public void setMinterLevel(Integer minterLevel) {
		this.minterLevel = minterLevel;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o == null || getClass() != o.getClass())
			return false;

		BlockSummaryData otherBlockSummary = (BlockSummaryData) o;
		if (this.getSignature() == null || otherBlockSummary.getSignature() == null)
			return false;

		// Treat two block summaries as equal if they have matching signatures
		return Arrays.equals(this.getSignature(), otherBlockSummary.getSignature());
	}

}
