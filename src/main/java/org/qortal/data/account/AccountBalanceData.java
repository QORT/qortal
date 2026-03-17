package org.qortal.data.account;


import org.qortal.utils.Amounts;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class AccountBalanceData {

	// Properties
	private String address;
	private long assetId;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long balance;

	// Not always present:
	private Integer height;
	private String assetName;

	// Constructors

	// necessary for JAXB
	protected AccountBalanceData() {
	}

	public AccountBalanceData(String address, long assetId, long balance) {
		this.address = address;
		this.assetId = assetId;
		this.balance = balance;
	}

	public AccountBalanceData(String address, long assetId, long balance, int height) {
		this(address, assetId, balance);

		this.height = height;
	}

	public AccountBalanceData(String address, long assetId, long balance, String assetName) {
		this(address, assetId, balance);

		this.assetName = assetName;
	}

	// Getters/Setters

	public String getAddress() {
		return this.address;
	}

	public long getAssetId() {
		return this.assetId;
	}

	public long getBalance() {
		return this.balance;
	}

	public void setBalance(long balance) {
		this.balance = balance;
	}

	public Integer getHeight() {
		return this.height;
	}

	public String getAssetName() {
		return this.assetName;
	}

	public String toString() {
		return String.format("%s has %s %s [assetId %d]", this.address, Amounts.prettyAmount(this.balance), (assetName != null ? assetName : ""), assetId);
	}

}
