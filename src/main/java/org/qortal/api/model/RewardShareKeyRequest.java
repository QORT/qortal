package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class RewardShareKeyRequest {

	@Schema(example = "private_key")
	public byte[] mintingAccountPrivateKey;

	@Schema(example = "public_key")
	public byte[] recipientAccountPublicKey;

	public RewardShareKeyRequest() {
	}

}
