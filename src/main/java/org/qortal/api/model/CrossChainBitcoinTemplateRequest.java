package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class CrossChainBitcoinTemplateRequest {

	@Schema(description = "Bitcoin HASH160(public key) for refund", example = "2nGDBPPPFS1c9w1h33YwFk4KUJU2")
	public byte[] refundPublicKeyHash;

	@Schema(description = "Bitcoin HASH160(public key) for redeem", example = "2daMveGc5pdjRyFacbxBzMksCbyC")
	public byte[] redeemPublicKeyHash;

	@Schema(description = "Qortal AT address")
	public String atAddress;

	public CrossChainBitcoinTemplateRequest() {
	}

}
