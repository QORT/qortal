package org.qortal.api.model.crosschain;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class TradeBotRespondRequests {

	@Schema(description = "Foreign blockchain private key, e.g. BIP32 'm' key for Bitcoin/Litecoin starting with 'xprv'",
			example = "xprv___________________________________________________________________________________________________________")
	public String foreignKey;

	@Schema(description = "List of address matches")
	@XmlElement(name = "addresses")
	public List<String> addresses;

	@Schema(description = "Qortal address for receiving QORT from AT", example = "Qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq")
	public String receivingAddress;

	public TradeBotRespondRequests() {
	}

	public TradeBotRespondRequests(String foreignKey, List<String> addresses, String receivingAddress) {
		this.foreignKey = foreignKey;
		this.addresses = addresses;
		this.receivingAddress = receivingAddress;
	}

	@Schema(description = "Address Match")
	// All properties to be converted to JSON via JAX-RS
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class AddressMatch {
		@Schema(description = "AT Address")
		public String atAddress;

		@Schema(description = "Receiving Address")
		public String receivingAddress;

		// For JAX-RS
		protected AddressMatch() {
		}

		public AddressMatch(String atAddress, String receivingAddress) {
			this.atAddress = atAddress;
			this.receivingAddress = receivingAddress;
		}

		@Override
		public String toString() {
			return "AddressMatch{" +
					"atAddress='" + atAddress + '\'' +
					", receivingAddress='" + receivingAddress + '\'' +
					'}';
		}
	}

	@Override
	public String toString() {
		return "TradeBotRespondRequests{" +
				"foreignKey='" + foreignKey + '\'' +
				", addresses=" + addresses +
				'}';
	}
}