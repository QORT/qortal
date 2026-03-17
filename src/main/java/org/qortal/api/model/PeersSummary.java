package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class PeersSummary {

	public int inboundConnections;
	public int outboundConnections;

	public PeersSummary() {
	}

}
