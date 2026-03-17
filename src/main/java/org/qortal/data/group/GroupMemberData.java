package org.qortal.data.group;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupMemberData {

	// Properties
	private int groupId;
	private String member;
	private long joined;
	/** Reference to transaction that triggered membership. Could be JOIN_GROUP, GROUP_INVITE, CREATE_GROUP or others... */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] reference;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupMemberData() {
	}

	public GroupMemberData(int groupId, String member, long joined, byte[] reference) {
		this.groupId = groupId;
		this.member = member;
		this.joined = joined;
		this.reference = reference;
	}

	// Getters / setters

	public int getGroupId() {
		return this.groupId;
	}

	public String getMember() {
		return this.member;
	}

	public long getJoined() {
		return this.joined;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

}
