package org.qortal.repository;

import org.qortal.data.asset.AssetData;
import org.qortal.data.asset.OrderData;
import org.qortal.data.asset.RecentTradeData;
import org.qortal.data.asset.TradeData;

import java.util.List;

public interface AssetRepository {

	// Assets

	public AssetData fromAssetId(long assetId) throws DataException;

	public AssetData fromAssetName(String assetName) throws DataException;

	public boolean assetExists(long assetId) throws DataException;

	public boolean assetExists(String assetName) throws DataException;

	public boolean reducedAssetNameExists(String reducedAssetName) throws DataException;

	public List<AssetData> getAllAssets(Integer limit, Integer offset, Boolean reverse) throws DataException;

	public default List<AssetData> getAllAssets() throws DataException {
		return getAllAssets(null, null, null);
	}

	public List<Long> getRecentAssetIds(long startTimestamp) throws DataException;

	// For a list of asset holders, see AccountRepository.getAssetBalances

	public void save(AssetData assetData) throws DataException;

	public void delete(long assetId) throws DataException;

	// Orders

	public OrderData fromOrderId(byte[] orderId) throws DataException;

	public List<OrderData> getOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/** Returns open orders, ordered by ascending unit price (i.e. best price first), for use by order matching logic. */
	public default List<OrderData> getOpenOrders(long haveAssetId, long wantAssetId) throws DataException {
		return getOpenOrders(haveAssetId, wantAssetId, null, null, null);
	}

	public List<OrderData> getOpenOrdersForTrading(long haveAssetId, long wantAssetId, Long minimumPrice) throws DataException;

	public List<OrderData> getAggregatedOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	public List<OrderData> getAccountsOrders(byte[] publicKey, Boolean optIsClosed, Boolean optIsFulfilled, Integer limit, Integer offset, Boolean reverse)
			throws DataException;

	public List<OrderData> getAccountsOrders(byte[] publicKey, long haveAssetId, long wantAssetId, Boolean optIsClosed, Boolean optIsFulfilled,
			Integer limit, Integer offset, Boolean reverse) throws DataException;

	// Internal, non-API use
	public default List<OrderData> getAccountsOrders(byte[] publicKey, Boolean optIsClosed, Boolean optIsFulfilled) throws DataException {
		return getAccountsOrders(publicKey, optIsClosed, optIsFulfilled, null, null, null);
	}

	public void save(OrderData orderData) throws DataException;

	public void delete(byte[] orderId) throws DataException;

	// Trades

	public List<TradeData> getTrades(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	// Internal, non-API use
	public default List<TradeData> getTrades(long haveAssetId, long wantAssetId) throws DataException {
		return getTrades(haveAssetId, wantAssetId, null, null, null);
	}

	public List<RecentTradeData> getRecentTrades(List<Long> assetIds, List<Long> otherAssetIds, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/** Returns TradeData for trades where orderId was involved, i.e. either initiating OR target order */
	public List<TradeData> getOrdersTrades(byte[] orderId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	// Internal, non-API use
	public default List<TradeData> getOrdersTrades(byte[] orderId) throws DataException {
		return getOrdersTrades(orderId, null, null, null);
	}

	public void save(TradeData tradeData) throws DataException;

	public void delete(TradeData tradeData) throws DataException;

}
