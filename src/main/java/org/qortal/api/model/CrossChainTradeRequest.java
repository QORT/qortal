package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class CrossChainTradeRequest {

	@Schema(description = "AT creator's 'trade' public key", example = "C6wuddsBV3HzRrXUtezE7P5MoRXp5m3mEDokRDGZB6ry")
	public byte[] tradePublicKey;

	@Schema(description = "Qortal AT address")
	public String atAddress;

	@Schema(description = "Signature of trading partner's 'offer' MESSAGE transaction")
	public byte[] messageTransactionSignature;

	public CrossChainTradeRequest() {
	}

}
