package org.qortal.data.group;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupBanData {

	// Properties
	private int groupId;
	private String offender;
	private String admin;
	private long banned;
	private String reason;
	private Long expiry;
	/** Reference to GROUP_BAN transaction, used to rebuild this ban during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] reference;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupBanData() {
	}

	public GroupBanData(int groupId, String offender, String admin, long banned, String reason, Long expiry, byte[] reference) {
		this.groupId = groupId;
		this.offender = offender;
		this.admin = admin;
		this.banned = banned;
		this.reason = reason;
		this.expiry = expiry;
		this.reference = reference;
	}

	// Getters / setters

	public int getGroupId() {
		return this.groupId;
	}

	public String getOffender() {
		return this.offender;
	}

	public String getAdmin() {
		return this.admin;
	}

	public long getBanned() {
		return this.banned;
	}

	public String getReason() {
		return this.reason;
	}
	
	public Long getExpiry() {
		return this.expiry;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

}
