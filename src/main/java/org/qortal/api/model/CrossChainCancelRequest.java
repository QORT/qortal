package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class CrossChainCancelRequest {

	@Schema(description = "AT creator's public key", example = "K6wuddsBV3HzRrXFFezE7P5MoRXp5m3mEDokRDGZB6ry")
	public byte[] creatorPublicKey;

	@Schema(description = "Qortal trade AT address")
	public String atAddress;

	public CrossChainCancelRequest() {
	}

}
