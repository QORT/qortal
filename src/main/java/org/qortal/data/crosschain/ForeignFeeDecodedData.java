package org.qortal.data.crosschain;

import org.json.JSONObject;
import org.qortal.data.account.MintingAccountData;
import org.qortal.utils.Base58;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Objects;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class ForeignFeeDecodedData {

	protected long timestamp;
	protected byte[] data;
	protected String atAddress;
	protected Integer fee;

	// Constructors

	// necessary for JAXB serialization
	protected ForeignFeeDecodedData() {
	}

	public ForeignFeeDecodedData(long timestamp, byte[] data, String atAddress, Integer fee) {
		this.timestamp = timestamp;
		this.data = data;
		this.atAddress = atAddress;
		this.fee = fee;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getData() {
		return this.data;
	}

	public String getAtAddress() {
		return atAddress;
	}

	public Integer getFee() {
		return this.fee;
	}

	// Comparison


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ForeignFeeDecodedData that = (ForeignFeeDecodedData) o;
		return timestamp == that.timestamp && Objects.equals(atAddress, that.atAddress) && Objects.equals(fee, that.fee);
	}

	@Override
	public int hashCode() {
		return Objects.hash(timestamp, atAddress, fee);
	}

	@Override
	public String toString() {
		return "ForeignFeeDecodedData{" +
				"timestamp=" + timestamp +
				", atAddress='" + atAddress + '\'' +
				", fee=" + fee +
				'}';
	}

	public JSONObject toJson() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("data", Base58.encode(this.data));
		jsonObject.put("atAddress", this.atAddress);
		jsonObject.put("timestamp", this.timestamp);
		jsonObject.put("fee", this.fee);
		return jsonObject;
	}

	public static ForeignFeeDecodedData fromJson(JSONObject json) {
		return new ForeignFeeDecodedData(
				json.isNull("timestamp") ? null : json.getLong("timestamp"),
				json.isNull("data") ? null : Base58.decode(json.getString("data")),
				json.isNull("atAddress") ? null : json.getString("atAddress"),
				json.isNull("fee") ? null : json.getInt("fee"));
	}
}