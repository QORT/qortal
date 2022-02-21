package org.qortal.repository;

import java.util.List;

import org.qortal.data.crosschain.TradeBotData;

public interface CrossChainRepository {

	public TradeBotData getTradeBotData(byte[] tradePrivateKey) throws DataException;

	/** Returns true if there is an existing trade-bot entry relating to given AT address, excluding trade-bot entries with given states. */
	public boolean existsTradeWithAtExcludingStates(String atAddress, List<String> excludeStates) throws DataException;

	public List<TradeBotData> getAllTradeBotData() throws DataException;

	public void save(TradeBotData tradeBotData) throws DataException;

	/** Delete trade-bot states using passed private key. */
	public int delete(byte[] tradePrivateKey) throws DataException;

}
