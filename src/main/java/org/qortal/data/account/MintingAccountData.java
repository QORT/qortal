package org.qortal.data.account;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import org.json.JSONObject;
import org.qortal.crypto.Crypto;
import org.qortal.utils.Base58;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class MintingAccountData {

	// Properties

	// Never actually displayed by API
	@Schema(hidden = true)
	@XmlTransient
	protected byte[] privateKey;

	// Read-only by API, we never ask for it as input
	@Schema(accessMode = AccessMode.READ_ONLY)
	protected byte[] publicKey;

	// Not always present - used by API if not null
	protected String mintingAccount;
	protected String recipientAccount;
	protected String address;

	// Constructors

	// For JAXB
	protected MintingAccountData() {
	}

	public MintingAccountData(byte[] privateKey, byte[] publicKey) {
		this.privateKey = privateKey;
		this.publicKey = publicKey;
	}

	public MintingAccountData(MintingAccountData srcMintingAccountData, RewardShareData rewardShareData) {
		this(srcMintingAccountData.privateKey, srcMintingAccountData.publicKey);

		if (rewardShareData != null) {
			this.recipientAccount = rewardShareData.getRecipient();
			this.mintingAccount = rewardShareData.getMinter();
		} else {
			this.address = Crypto.toAddress(this.publicKey);
		}
	}

	// Getters/Setters

	public byte[] getPrivateKey() {
		return this.privateKey;
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}


	// JSON

	public JSONObject toJson() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("privateKey", Base58.encode(this.getPrivateKey()));
		jsonObject.put("publicKey", Base58.encode(this.getPublicKey()));
		return jsonObject;
	}

	public static MintingAccountData fromJson(JSONObject json) {
		return new MintingAccountData(
				json.isNull("privateKey") ? null : Base58.decode(json.getString("privateKey")),
				json.isNull("publicKey") ? null : Base58.decode(json.getString("publicKey"))
		);
	}

}
