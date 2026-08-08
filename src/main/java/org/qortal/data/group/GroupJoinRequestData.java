package org.qortal.data.group;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupJoinRequestData {

	// Properties
	private int groupId;
	private String joiner;
	/** Reference to JOIN_GROUP transaction, used to rebuild this join request during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] reference;

	// Constructors

	// necessary for JAX-RS serialization
	protected GroupJoinRequestData() {
	}

	public GroupJoinRequestData(int groupId, String joiner, byte[] reference) {
		this.groupId = groupId;
		this.joiner = joiner;
		this.reference = reference;
	}

	// Getters / setters

	public int getGroupId() {
		return this.groupId;
	}

	public String getJoiner() {
		return this.joiner;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

}
