package org.qortal.api.model.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class LitecoinSendRequest {

	@Schema(description = "Litecoin BIP32 extended private key", example = "tprv___________________________________________________________________________________________________________")
	public String xprv58;

	@Schema(description = "Recipient's Litecoin address ('legacy' P2PKH only)", example = "LiTecoinEaterAddressDontSendhLfzKD")
	public String receivingAddress;

	@Schema(description = "Amount of LTC to send", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public long litecoinAmount;

	@Schema(description = "Transaction fee per byte (optional). Default is 0.00000100 LTC (100 sats) per byte", example = "0.00000100", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public Long feePerByte;

	public LitecoinSendRequest() {
	}

}
