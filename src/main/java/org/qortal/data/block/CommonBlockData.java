package org.qortal.data.block;

import org.qortal.data.network.PeerChainTipData;

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
	private PeerChainTipData chainTipData = null;

	// Constructors

	protected CommonBlockData() {
	}

	public CommonBlockData(BlockSummaryData commonBlockSummary, PeerChainTipData chainTipData) {
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

	public PeerChainTipData getChainTipData() {
		return this.chainTipData;
	}

}
