package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.data.crosschain.CrossChainTradeData;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class CrossChainTradeLedgerEntry {

	private String market;

	private String currency;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long quantity;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long feeAmount;

	private String feeCurrency;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long totalPrice;

	private long tradeTimestamp;

	protected CrossChainTradeLedgerEntry() {
		/* For JAXB */
	}

	public CrossChainTradeLedgerEntry(String market, String currency, long quantity, long feeAmount, String feeCurrency, long totalPrice, long tradeTimestamp) {
		this.market = market;
		this.currency = currency;
		this.quantity = quantity;
		this.feeAmount = feeAmount;
		this.feeCurrency = feeCurrency;
		this.totalPrice = totalPrice;
		this.tradeTimestamp = tradeTimestamp;
	}

	public String getMarket() {
		return market;
	}

	public String getCurrency() {
		return currency;
	}

	public long getQuantity() {
		return quantity;
	}

	public long getFeeAmount() {
		return feeAmount;
	}

	public String getFeeCurrency() {
		return feeCurrency;
	}

	public long getTotalPrice() {
		return totalPrice;
	}

	public long getTradeTimestamp() {
		return tradeTimestamp;
	}
}