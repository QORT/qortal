package org.qortal.api.model;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class CrossChainBitcoinRedeemRequest {

	@Schema(description = "Bitcoin HASH160(public key) for refund", example = "2nGDBPPPFS1c9w1h33YwFk4KUJU2")
	public byte[] refundPublicKeyHash;

	@Schema(description = "Bitcoin PRIVATE KEY for redeem", example = "cUvGNSnu14q6Hr1X7TESjYVTqBpFjj8GGLGjGdpJwD9NhSQKeYUk")
	public byte[] redeemPrivateKey;

	@Schema(description = "Qortal AT address")
	public String atAddress;

	@Schema(description = "Bitcoin miner fee", example = "0.00001000")
	public BigDecimal bitcoinMinerFee;

	@Schema(description = "32-byte secret", example = "6gVbAXCVzJXAWwtAVGAfgAkkXpeXvPUwSciPmCfSfXJG")
	public byte[] secret;

	@Schema(description = "Bitcoin HASH160(public key) for receiving funds, or omit to derive from private key", example = "u17kBVKkKSp12oUzaxFwNnq1JZf")
	public byte[] receivingAccountInfo;

	public CrossChainBitcoinRedeemRequest() {
	}

}
