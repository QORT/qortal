package org.qortal.data.block;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.math.BigInteger;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class CommonBlockData {

	// Properties
	private BlockSummaryData commonBlockSummary = null;
	private List<BlockSummaryData> blockSummariesAfterCommonBlock = null;
	private BigInteger chainWeight = null;
	private BlockSummaryData chainTipData = null;

	// Constructors

	protected CommonBlockData() {
	}

	public CommonBlockData(BlockSummaryData commonBlockSummary, BlockSummaryData chainTipData) {
		this.commonBlockSummary = commonBlockSummary;
		this.chainTipData = chainTipData;
	}


	// Getters / setters

	public BlockSummaryData getCommonBlockSummary() {
		return this.commonBlockSummary;
	}

	public List<BlockSummaryData> getBlockSummariesAfterCommonBlock() {
		return this.blockSummariesAfterCommonBlock;
	}

	public void setBlockSummariesAfterCommonBlock(List<BlockSummaryData> blockSummariesAfterCommonBlock) {
		this.blockSummariesAfterCommonBlock = blockSummariesAfterCommonBlock;
	}

	public BigInteger getChainWeight() {
		return this.chainWeight;
	}

	public void setChainWeight(BigInteger chainWeight) {
		this.chainWeight = chainWeight;
	}

	public BlockSummaryData getChainTipData() {
		return this.chainTipData;
	}

}
