package org.qortal.controller.tradebot;

import org.qortal.api.model.crosschain.TradeBotCreateRequest;
import org.qortal.crosschain.ACCT;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.data.at.ATData;
import org.qortal.data.crosschain.CrossChainTradeData;
import org.qortal.data.crosschain.TradeBotData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.List;

public interface AcctTradeBot {

	public enum ResponseResult { OK, BALANCE_ISSUE, NETWORK_ISSUE, TRADE_ALREADY_EXISTS }

	/** Returns list of state names for trade-bot entries that have ended, e.g. redeemed, refunded or cancelled. */
	public List<String> getEndStates();

	public byte[] createTrade(Repository repository, TradeBotCreateRequest tradeBotCreateRequest) throws DataException;

	public ResponseResult startResponse(Repository repository, ATData atData, ACCT acct,
			CrossChainTradeData crossChainTradeData, String foreignKey, String receivingAddress) throws DataException;

	public boolean canDelete(Repository repository, TradeBotData tradeBotData) throws DataException;

	public void progress(Repository repository, TradeBotData tradeBotData) throws DataException, ForeignBlockchainException;

}
