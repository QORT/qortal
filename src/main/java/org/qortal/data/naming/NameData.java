package org.qortal.data.naming;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class NameData {

	// Properties

	private String name;

	private String reducedName;

	private String owner;

	private String data;

	private long registered;

	private Long updated; // Not always present

	private boolean isForSale;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private Long salePrice;

	// For internal use - no need to expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] reference;

	// For internal use
	@XmlTransient
	@Schema(hidden = true)
	private int creationGroupId;

	// Constructors

	// necessary for JAXB
	protected NameData() {
	}

	// Typically used when fetching from repository
	public NameData(String name, String reducedName, String owner, String data, long registered,
			Long updated, boolean isForSale, Long salePrice,
			byte[] reference, int creationGroupId) {
		this.name = name;
		this.reducedName = reducedName;
		this.owner = owner;
		this.data = data;
		this.registered = registered;
		this.updated = updated;
		this.reference = reference;
		this.isForSale = isForSale;
		this.salePrice = salePrice;
		this.creationGroupId = creationGroupId;
	}

	// Typically used when registering a new name
	public NameData(String name, String reducedName, String owner, String data, long registered, byte[] reference, int creationGroupId) {
		this(name, reducedName, owner, data, registered, null, false, null, reference, creationGroupId);
	}

	// Typically used for name summsry
	public NameData(String name, String owner) {
		this(name, null, owner, null, 0L, null, false, null, null, 0);
	}

	// Getters / setters

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getReducedName() {
		return this.reducedName;
	}

	public void setReducedName(String reducedName) {
		this.reducedName = reducedName;
	}

	public String getOwner() {
		return this.owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getData() {
		return this.data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public long getRegistered() {
		return this.registered;
	}

	public Long getUpdated() {
		return this.updated;
	}

	public void setUpdated(Long updated) {
		this.updated = updated;
	}

	public boolean isForSale() {
		return this.isForSale;
	}

	public void setIsForSale(boolean isForSale) {
		this.isForSale = isForSale;
	}

	public Long getSalePrice() {
		return this.salePrice;
	}

	public void setSalePrice(Long salePrice) {
		this.salePrice = salePrice;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

	public int getCreationGroupId() {
		return this.creationGroupId;
	}

}
