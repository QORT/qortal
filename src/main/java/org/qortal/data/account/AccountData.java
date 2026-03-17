package org.qortal.data.account;

import org.qortal.group.Group;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class AccountData {

	// Properties
	protected String address;
	protected byte[] reference;
	protected byte[] publicKey;
	protected int defaultGroupId;
	protected int flags;
	protected int level;
	protected int blocksMinted;
	protected int blocksMintedAdjustment;
	protected int blocksMintedPenalty;

	// Constructors

	// For JAXB
	protected AccountData() {
	}

	public AccountData(String address, byte[] reference, byte[] publicKey, int defaultGroupId, int flags, int level, int blocksMinted, int blocksMintedAdjustment, int blocksMintedPenalty) {
		this.address = address;
		this.reference = reference;
		this.publicKey = publicKey;
		this.defaultGroupId = defaultGroupId;
		this.flags = flags;
		this.level = level;
		this.blocksMinted = blocksMinted;
		this.blocksMintedAdjustment = blocksMintedAdjustment;
		this.blocksMintedPenalty = blocksMintedPenalty;
	}

	public AccountData(String address) {
		this(address, null, null, Group.NO_GROUP, 0, 0, 0, 0, 0);
	}

	// Getters/Setters

	public String getAddress() {
		return this.address;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	public void setPublicKey(byte[] publicKey) {
		this.publicKey = publicKey;
	}

	public int getDefaultGroupId() {
		return this.defaultGroupId;
	}

	public void setDefaultGroupId(int defaultGroupId) {
		this.defaultGroupId = defaultGroupId;
	}

	public int getFlags() {
		return this.flags;
	}

	public void setFlags(int flags) {
		this.flags = flags;
	}

	public int getLevel() {
		return this.level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public int getBlocksMinted() {
		return this.blocksMinted;
	}

	public void setBlocksMinted(int blocksMinted) {
		this.blocksMinted = blocksMinted;
	}

	public int getBlocksMintedAdjustment() {
		return this.blocksMintedAdjustment;
	}

	public void setBlocksMintedAdjustment(int blocksMintedAdjustment) {
		this.blocksMintedAdjustment = blocksMintedAdjustment;
	}

	public int getBlocksMintedPenalty() {
		return this.blocksMintedPenalty;
	}

	public void setBlocksMintedPenalty(int blocksMintedPenalty) {
		this.blocksMintedPenalty = blocksMintedPenalty;
	}

	// Comparison

	@Override
	public boolean equals(Object b) {
		if (!(b instanceof AccountData))
			return false;

		return this.getAddress().equals(((AccountData) b).getAddress());
	}

	@Override
	public int hashCode() {
		return this.getAddress().hashCode();
	}

}
