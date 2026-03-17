package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.data.crosschain.CrossChainTradeData;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class CrossChainTradeSummary {

	private long tradeTimestamp;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long qortAmount;

	@Deprecated
	@Schema(description = "DEPRECATED: use foreignAmount instead")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long btcAmount;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long foreignAmount;

	private String atAddress;

	private String sellerAddress;

	private String buyerReceivingAddress;

	protected CrossChainTradeSummary() {
		/* For JAXB */
	}

	public CrossChainTradeSummary(CrossChainTradeData crossChainTradeData, long timestamp) {
		this.tradeTimestamp = timestamp;
		this.qortAmount = crossChainTradeData.qortAmount;
		this.foreignAmount = crossChainTradeData.expectedForeignAmount;
		this.btcAmount = this.foreignAmount;
		this.sellerAddress = crossChainTradeData.qortalCreator;
		this.buyerReceivingAddress = crossChainTradeData.qortalPartnerReceivingAddress;
		this.atAddress = crossChainTradeData.qortalAtAddress;
	}

	public long getTradeTimestamp() {
		return this.tradeTimestamp;
	}

	public long getQortAmount() {
		return this.qortAmount;
	}

	public long getBtcAmount() {
		return this.btcAmount;
	}

	public long getForeignAmount() { return this.foreignAmount; }

	public String getAtAddress() { return this.atAddress; }

	public String getSellerAddress() { return this.sellerAddress; }

	public String getBuyerReceivingAddressAddress() { return this.buyerReceivingAddress; }
}
