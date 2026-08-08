package org.qortal.repository;

import org.qortal.data.network.PeerData;
import org.qortal.network.PeerAddress;

import java.util.List;

public interface NetworkRepository {

	public List<PeerData> getAllPeers() throws DataException;

	public void save(PeerData peerData) throws DataException;

	public int delete(PeerAddress peerAddress) throws DataException;

	public int deleteAllPeers() throws DataException;

}
