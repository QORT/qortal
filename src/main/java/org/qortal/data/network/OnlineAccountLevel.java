package org.qortal.data.network;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;


// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class OnlineAccountLevel {

	protected int level;
	protected int count;

	// Constructors

	// necessary for JAXB serialization
	protected OnlineAccountLevel() {
	}

	public OnlineAccountLevel(int level, int count) {
		this.level = level;
		this.count = count;
	}

	public int getLevel() {
		return this.level;
	}

	public int getCount() {
		return this.count;
	}

	public void setCount(int count) {
		this.count = count;
	}


	// Comparison

	@Override
	public boolean equals(Object other) {
		if (other == this)
			return true;

		if (!(other instanceof OnlineAccountLevel))
			return false;

		OnlineAccountLevel otherOnlineAccountData = (OnlineAccountLevel) other;

		if (otherOnlineAccountData.level != this.level)
			return false;

		return true;
	}
}
