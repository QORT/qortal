package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class CrossChainDualSecretRequest {

	@Schema(description = "Public key to match AT's trade 'partner'", example = "C6wuddsBV3HzRrXUtezE7P5MoRXp5m3mEDokRDGZB6ry")
	public byte[] partnerPublicKey;

	@Schema(description = "Qortal AT address")
	public String atAddress;

	@Schema(description = "secret-A (32 bytes)", example = "FHMzten4he9jZ4HGb4297Utj6F5g2w7serjq2EnAg2s1")
	public byte[] secretA;

	@Schema(description = "secret-B (32 bytes)", example = "EN2Bgx3BcEMtxFCewmCVSMkfZjVKYhx3KEXC5A21KBGx")
	public byte[] secretB;

	@Schema(description = "Qortal address for receiving QORT from AT")
	public String receivingAddress;

	public CrossChainDualSecretRequest() {
	}

}
