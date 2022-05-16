package org.qortal.api.model;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class CrossChainBitcoinRefundRequest {

	@Schema(description = "Bitcoin PRIVATE KEY for refund", example = "cSP3zTb6bfm8GATtAcEJ8LqYtNQmzZ9jE2wQUVnZGiBzojDdrwKV")
	public byte[] refundPrivateKey;

	@Schema(description = "Bitcoin HASH160(public key) for redeem", example = "2daMveGc5pdjRyFacbxBzMksCbyC")
	public byte[] redeemPublicKeyHash;

	@Schema(description = "Qortal AT address")
	public String atAddress;

	@Schema(description = "Bitcoin miner fee", example = "0.00001000")
	public BigDecimal bitcoinMinerFee;

	@Schema(description = "Bitcoin HASH160(public key) for receiving funds, or omit to derive from private key", example = "u17kBVKkKSp12oUzaxFwNnq1JZf")
	public byte[] receivingAccountInfo;

	public CrossChainBitcoinRefundRequest() {
	}

}
