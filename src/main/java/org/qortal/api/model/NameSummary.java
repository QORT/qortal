package org.qortal.api.model;

import org.qortal.data.naming.NameData;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.NONE)
public class NameSummary {

	private NameData nameData;

	protected NameSummary() {
	}

	public NameSummary(NameData nameData) {
		this.nameData = nameData;
	}

	@XmlElement(name = "name")
	public String getName() {
		return this.nameData.getName();
	}

	@XmlElement(name = "owner")
	public String getOwner() {
		return this.nameData.getOwner();
	}

}
