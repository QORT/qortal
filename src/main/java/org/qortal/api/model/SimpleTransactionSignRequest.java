package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class SimpleTransactionSignRequest {

	@Schema(
		description = "signer's private key",
		example = "A9MNsATgQgruBUjxy2rjWY36Yf19uRioKZbiLFT2P7c6"
	)
	public byte[] privateKey;

	@Schema(
		description = "raw, unsigned transaction bytes",
		example = "base58"
	)
	public byte[] transactionBytes;

}
