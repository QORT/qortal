package org.qortal.data.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Objects;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class ForeignFeeEncodedData {

	protected long timestamp;
	protected String data;
	protected String atAddress;
	protected Integer fee;

	// Constructors

	// necessary for JAXB serialization
	protected ForeignFeeEncodedData() {
	}

	public ForeignFeeEncodedData(long timestamp, String data, String atAddress, Integer fee) {
		this.timestamp = timestamp;
		this.data = data;
		this.atAddress = atAddress;
		this.fee = fee;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public String getData() {
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
		ForeignFeeEncodedData that = (ForeignFeeEncodedData) o;
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
}