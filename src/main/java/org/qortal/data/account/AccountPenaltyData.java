package org.qortal.data.account;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class AccountPenaltyData {

	// Properties
	private String address;
	private int blocksMintedPenalty;

	// Constructors

	// necessary for JAXB
	protected AccountPenaltyData() {
	}

	public AccountPenaltyData(String address, int blocksMintedPenalty) {
		this.address = address;
		this.blocksMintedPenalty = blocksMintedPenalty;
	}

	// Getters/Setters

	public String getAddress() {
		return this.address;
	}

	public int getBlocksMintedPenalty() {
		return this.blocksMintedPenalty;
	}

	public String toString() {
		return String.format("%s has penalty %d", this.address, this.blocksMintedPenalty);
	}

	@Override
	public boolean equals(Object b) {
		if (!(b instanceof AccountPenaltyData))
			return false;

		return this.getAddress().equals(((AccountPenaltyData) b).getAddress());
	}

	@Override
	public int hashCode() {
		return address.hashCode();
	}

}
