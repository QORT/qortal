package org.qortal.api.model.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class TradeBotRespondRequest {

	@Schema(description = "Qortal AT address", example = "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
	public String atAddress;

	@Deprecated
	@Schema(description = "Bitcoin BIP32 extended private key. DEPRECATED: use foreignKey instead", hidden = true,
			example = "xprv___________________________________________________________________________________________________________")
	public String xprv58;

	@Schema(description = "Foreign blockchain private key, e.g. BIP32 'm' key for Bitcoin/Litecoin starting with 'xprv'",
			example = "xprv___________________________________________________________________________________________________________")
	public String foreignKey;

	@Schema(description = "Qortal address for receiving QORT from AT", example = "Qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq")
	public String receivingAddress;

	public TradeBotRespondRequest() {
	}

}
