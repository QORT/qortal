package org.qortal.api.model;

import org.qortal.data.asset.OrderData;

import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(XmlAccessType.NONE)
public class AggregatedOrder {

	// Not exposed to API
	private OrderData orderData;

	// Needed by JAXB for [un]marshalling
	protected AggregatedOrder() {
	}

	public void beforeMarshal(Marshaller m) {
		// OrderData needs to calculate values for us
		this.orderData.beforeMarshal(m);
	}

	public AggregatedOrder(OrderData orderData) {
		this.orderData = orderData;
	}

	@XmlElement(name = "price")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public long getPrice() {
		return this.orderData.getPrice();
	}

	@XmlElement(name = "unfulfilled")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public long getUnfulfilled() {
		return this.orderData.getAmount();
	}

	@XmlElement(name = "unfulfilledAssetId")
	public long getUnfulfilledAssetId() {
		return this.orderData.getAmountAssetId();
	}

	@XmlElement(name = "unfulfilledAssetName")
	public String getUnfulfilledAssetName() {
		return this.orderData.getAmountAssetName();
	}

	@XmlElement(name = "pricePair")
	public String getPricePair() {
		return this.orderData.getPricePair();
	}

}
