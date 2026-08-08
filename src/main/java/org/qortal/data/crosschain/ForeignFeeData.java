package org.qortal.data.crosschain;

import org.json.JSONObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class ForeignFeeData {

	private String blockchain;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long fee;

	protected ForeignFeeData() {
		/* JAXB */
	}

	public ForeignFeeData(String blockchain,
                          long fee) {
		this.blockchain = blockchain;
		this.fee = fee;
	}

	public String getBlockchain() {
		return this.blockchain;
	}

	public long getFee() {
		return this.fee;
	}

	public JSONObject toJson() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("blockchain", this.getBlockchain());
	    jsonObject.put("fee", this.getFee());
		return jsonObject;
	}

	public static ForeignFeeData fromJson(JSONObject json) {
		return new ForeignFeeData(
				json.isNull("blockchain") ? null : json.getString("blockchain"),
				json.isNull("fee") ? null : json.getLong("fee")
		);
	}

	@Override
	public String toString() {
		return "ForeignFeeData{" +
				"blockchain='" + blockchain + '\'' +
				", fee=" + fee +
				'}';
	}
}
