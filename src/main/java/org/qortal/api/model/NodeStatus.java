package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.qortal.controller.Controller;
import org.qortal.network.Network;

@XmlAccessorType(XmlAccessType.FIELD)
public class NodeStatus {

	public final boolean isMintingPossible;
	public final boolean isSynchronizing;

	// Not always present
	public final Integer syncPercent;

	public final int numberOfConnections;

	public final int height;

	public NodeStatus() {
		this.isMintingPossible = Controller.getInstance().isMintingPossible();

		this.syncPercent = Controller.getInstance().getSyncPercent();
		this.isSynchronizing = this.syncPercent != null;

		this.numberOfConnections = Network.getInstance().getHandshakedPeers().size();

		this.height = Controller.getInstance().getChainHeight();
	}

}
