package org.qortal.data.network;

import org.qortal.crypto.Crypto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Arrays;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class TradePresenceData {

	protected long timestamp;

	@XmlJavaTypeAdapter(
			type = byte[].class,
			value = org.qortal.api.Base58TypeAdapter.class
	)
	protected byte[] publicKey; // Could be BOB's or ALICE's

	// No need to send this via websocket / API
	@XmlTransient
	protected byte[] signature; // Not always present

	protected String atAddress; // Not always present

	// Have JAXB use getter instead
	@XmlTransient
	protected String tradeAddress; // Lazily instantiated

	// Constructors

	// necessary for JAXB serialization
	protected TradePresenceData() {
	}

	public TradePresenceData(long timestamp, byte[] publicKey, byte[] signature, String atAddress) {
		this.timestamp = timestamp;
		this.publicKey = publicKey;
		this.signature = signature;
		this.atAddress = atAddress;
	}

	public TradePresenceData(long timestamp, byte[] publicKey) {
		this(timestamp, publicKey, null, null);
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public String getAtAddress() {
		return this.atAddress;
	}

	// Probably doesn't need synchronization
	@XmlElement
	public String getTradeAddress() {
		if (tradeAddress != null)
			return tradeAddress;

		tradeAddress = Crypto.toAddress(this.publicKey);
		return tradeAddress;
	}

	// Comparison

	@Override
	public boolean equals(Object other) {
		if (other == this)
			return true;

		if (!(other instanceof TradePresenceData))
			return false;

		TradePresenceData otherTradePresenceData = (TradePresenceData) other;

		// Very quick comparison
		if (otherTradePresenceData.timestamp != this.timestamp)
			return false;

		if (!Arrays.equals(otherTradePresenceData.publicKey, this.publicKey))
			return false;

		if (otherTradePresenceData.atAddress != null && !otherTradePresenceData.atAddress.equals(this.atAddress))
			return false;

		if (this.atAddress != null && !this.atAddress.equals(otherTradePresenceData.atAddress))
			return false;

		if (!Arrays.equals(otherTradePresenceData.signature, this.signature))
			return false;

		return true;
	}

	@Override
	public int hashCode() {
		// Pretty lazy implementation
		return (int) this.timestamp;
	}

}
