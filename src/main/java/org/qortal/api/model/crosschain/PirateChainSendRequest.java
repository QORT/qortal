package org.qortal.api.model.crosschain;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(XmlAccessType.FIELD)
public class PirateChainSendRequest {

	@Schema(description = "32 bytes of entropy, Base58 encoded", example = "5oSXF53qENtdUyKhqSxYzP57m6RhVFP9BJKRr9E5kRGV")
	public String entropy58;

	@Schema(description = "Recipient's Pirate Chain address", example = "zc...")
	public String receivingAddress;

	@Schema(description = "Amount of ARRR to send", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public long arrrAmount;

	@Schema(description = "Transaction fee per byte (optional). Default is 0.00000100 ARRR (100 sats) per byte", example = "0.00000100", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public Long feePerByte;

	@Schema(description = "Optional memo to include information for the recipient", example = "zc...")
	public String memo;

	public PirateChainSendRequest() {
	}

}
