package org.qortal.data.network;

import org.qortal.account.PublicKeyAccount;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.Arrays;
import java.util.Objects;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class OnlineAccountData {

	protected long timestamp;
	protected byte[] signature;
	protected byte[] publicKey;
	protected Integer nonce;

	@XmlTransient
	private int hash;

	// Constructors

	// necessary for JAXB serialization
	protected OnlineAccountData() {
	}

	public OnlineAccountData(long timestamp, byte[] signature, byte[] publicKey, Integer nonce) {
		this.timestamp = timestamp;
		this.signature = signature;
		this.publicKey = publicKey;
		this.nonce = nonce;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	public Integer getNonce() {
		return this.nonce;
	}

	// For JAXB
	@XmlElement(name = "address")
	protected String getAddress() {
		return new PublicKeyAccount(null, this.publicKey).getAddress();
	}

	// Comparison

	@Override
	public boolean equals(Object other) {
		if (other == this)
			return true;

		if (!(other instanceof OnlineAccountData))
			return false;

		OnlineAccountData otherOnlineAccountData = (OnlineAccountData) other;

		// Very quick comparison
		if (otherOnlineAccountData.timestamp != this.timestamp)
			return false;

		// Almost as quick
		if (!Objects.equals(otherOnlineAccountData.nonce, this.nonce))
			return false;

		if (!Arrays.equals(otherOnlineAccountData.publicKey, this.publicKey))
			return false;

		// We don't compare signature because it's not our remit to verify and newer aggregate signatures use random nonces

		return true;
	}

	@Override
	public int hashCode() {
		int h = this.hash;
		if (h == 0) {
			h = Objects.hash(timestamp, nonce);
			h = 31 * h + Arrays.hashCode(publicKey);
			// We don't use signature because newer aggregate signatures use random nonces
			this.hash = h;
		}
		return h;
	}

}
