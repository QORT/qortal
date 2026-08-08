package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.data.asset.OrderData;
import org.qortal.data.asset.TradeData;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@Schema(description = "Asset trade, including order info")
// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class TradeWithOrderInfo {

	@Schema(implementation = TradeData.class, name = "trade", title = "trade data")
	@XmlElement(name = "trade")
	public TradeData tradeData;

	@Schema(implementation = OrderData.class, name = "order", title = "order data")
	@XmlElement(name = "initiatingOrder")
	public OrderData initiatingOrderData;

	@Schema(implementation = OrderData.class, name = "order", title = "order data")
	@XmlElement(name = "targetOrder")
	public OrderData targetOrderData;

	// For JAX-RS
	protected TradeWithOrderInfo() {
	}

	public TradeWithOrderInfo(TradeData tradeData, OrderData initiatingOrderData, OrderData targetOrderData) {
		this.tradeData = tradeData;
		this.initiatingOrderData = initiatingOrderData;
		this.targetOrderData = targetOrderData;
	}

}
