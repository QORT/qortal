package org.qortal.repository.hsqldb;

import org.qortal.data.asset.AssetData;
import org.qortal.data.asset.OrderData;
import org.qortal.data.asset.RecentTradeData;
import org.qortal.data.asset.TradeData;
import org.qortal.repository.AssetRepository;
import org.qortal.repository.DataException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HSQLDBAssetRepository implements AssetRepository {

	protected HSQLDBRepository repository;

	public HSQLDBAssetRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	// Assets

	@Override
	public AssetData fromAssetId(long assetId) throws DataException {
		String sql = "SELECT owner, asset_name, description, quantity, is_divisible, data, "
				+ "is_unspendable, creation_group_id, reference, reduced_asset_name "
				+ "FROM Assets WHERE asset_id = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, assetId)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			String assetName = resultSet.getString(2);
			String description = resultSet.getString(3);
			long quantity = resultSet.getLong(4);
			boolean isDivisible = resultSet.getBoolean(5);
			String data = resultSet.getString(6);
			boolean isUnspendable = resultSet.getBoolean(7);
			int creationGroupId = resultSet.getInt(8);
			byte[] reference = resultSet.getBytes(9);
			String reducedAssetName = resultSet.getString(10);

			return new AssetData(assetId, owner, assetName, description, quantity, isDivisible, data, isUnspendable,
					creationGroupId, reference, reducedAssetName);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset from repository", e);
		}
	}

	@Override
	public AssetData fromAssetName(String assetName) throws DataException {
		String sql = "SELECT owner, asset_id, description, quantity, is_divisible, data, "
				+ "is_unspendable, creation_group_id, reference, reduced_asset_name "
				+ "FROM Assets WHERE asset_name = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, assetName)) {
			if (resultSet == null)
				return null;

			String owner = resultSet.getString(1);
			long assetId = resultSet.getLong(2);
			String description = resultSet.getString(3);
			long quantity = resultSet.getLong(4);
			boolean isDivisible = resultSet.getBoolean(5);
			String data = resultSet.getString(6);
			boolean isUnspendable = resultSet.getBoolean(7);
			int creationGroupId = resultSet.getInt(8);
			byte[] reference = resultSet.getBytes(9);
			String reducedAssetName = resultSet.getString(10);

			return new AssetData(assetId, owner, assetName, description, quantity, isDivisible, data, isUnspendable,
					creationGroupId, reference, reducedAssetName);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset from repository", e);
		}
	}

	@Override
	public boolean assetExists(long assetId) throws DataException {
		try {
			return this.repository.exists("Assets", "asset_id = ?", assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to check for asset in repository", e);
		}
	}

	@Override
	public boolean assetExists(String assetName) throws DataException {
		try {
			return this.repository.exists("Assets", "asset_name = ?", assetName);
		} catch (SQLException e) {
			throw new DataException("Unable to check for asset in repository", e);
		}
	}

	@Override
	public boolean reducedAssetNameExists(String reducedAssetName) throws DataException {
		try {
			return this.repository.exists("Assets", "reduced_asset_name = ?", reducedAssetName);
		} catch (SQLException e) {
			throw new DataException("Unable to check for asset in repository", e);
		}
	}

	@Override
	public List<AssetData> getAllAssets(Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(256);
		sql.append("SELECT asset_id, owner, asset_name, description, quantity, is_divisible, data, "
				+ "is_unspendable, creation_group_id, reference, reduced_asset_name "
				+ "FROM Assets ORDER BY asset_id");
		if (reverse != null && reverse)
			sql.append(" DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<AssetData> assets = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString())) {
			if (resultSet == null)
				return assets;

			do {
				long assetId = resultSet.getLong(1);
				String owner = resultSet.getString(2);
				String assetName = resultSet.getString(3);
				String description = resultSet.getString(4);
				long quantity = resultSet.getLong(5);
				boolean isDivisible = resultSet.getBoolean(6);
				String data = resultSet.getString(7);
				boolean isUnspendable = resultSet.getBoolean(8);
				int creationGroupId = resultSet.getInt(9);
				byte[] reference = resultSet.getBytes(10);
				String reducedAssetName = resultSet.getString(11);

				assets.add(new AssetData(assetId, owner, assetName, description, quantity, isDivisible, data,
						isUnspendable,creationGroupId, reference, reducedAssetName));
			} while (resultSet.next());

			return assets;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch all assets from repository", e);
		}
	}

	@Override
	public List<Long> getRecentAssetIds(long startTimestamp) throws DataException {
		String sql = "SELECT asset_id FROM IssueAssetTransactions JOIN Assets USING (asset_id) "
				+ "JOIN Transactions USING (signature) "
				+ "WHERE created_when >= ?";

		List<Long> assetIds = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, startTimestamp)) {
			if (resultSet == null)
				return assetIds;

			do {
				long assetId = resultSet.getLong(1);

				assetIds.add(assetId);
			} while (resultSet.next());

			return assetIds;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch recent asset IDs from repository", e);
		}
	}

	@Override
	public void save(AssetData assetData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Assets");

		saveHelper.bind("asset_id", assetData.getAssetId()).bind("owner", assetData.getOwner())
				.bind("asset_name", assetData.getName()).bind("description", assetData.getDescription())
				.bind("quantity", assetData.getQuantity()).bind("is_divisible", assetData.isDivisible())
				.bind("data", assetData.getData()).bind("is_unspendable", assetData.isUnspendable())
				.bind("creation_group_id", assetData.getCreationGroupId()).bind("reference", assetData.getReference())
				.bind("reduced_asset_name", assetData.getReducedAssetName());

		try {
			saveHelper.execute(this.repository);

			if (assetData.getAssetId() == null) {
				// Fetch new assetId
				try (ResultSet resultSet = this.repository.checkedExecute("SELECT asset_id FROM Assets WHERE reference = ?", assetData.getReference())) {
					if (resultSet == null)
						throw new DataException("Unable to fetch new asset ID from repository");

					assetData.setAssetId(resultSet.getLong(1));
				}
			}
		} catch (SQLException e) {
			throw new DataException("Unable to save asset into repository", e);
		}
	}

	@Override
	public void delete(long assetId) throws DataException {
		try {
			this.repository.delete("Assets", "asset_id = ?", assetId);

			// Also delete account balances that refer to asset
			this.repository.delete("AccountBalances", "asset_id = ?", assetId);
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset from repository", e);
		}
	}

	// Orders

	@Override
	public OrderData fromOrderId(byte[] orderId) throws DataException {
		String sql = "SELECT creator, have_asset_id, want_asset_id, amount, fulfilled, price, ordered_when, "
				+ "is_closed, is_fulfilled, HaveAsset.asset_name, WantAsset.asset_name "
				+ "FROM AssetOrders "
				+ "JOIN Assets AS HaveAsset ON HaveAsset.asset_id = have_asset_id "
				+ "JOIN Assets AS WantAsset ON WantAsset.asset_id = want_asset_id "
				+ "WHERE asset_order_id = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, orderId)) {
			if (resultSet == null)
				return null;

			byte[] creatorPublicKey = resultSet.getBytes(1);
			long haveAssetId = resultSet.getLong(2);
			long wantAssetId = resultSet.getLong(3);
			long amount = resultSet.getLong(4);
			long fulfilled = resultSet.getLong(5);
			long price = resultSet.getLong(6);
			long timestamp = resultSet.getLong(7);
			boolean isClosed = resultSet.getBoolean(8);
			boolean isFulfilled = resultSet.getBoolean(9);
			String haveAssetName = resultSet.getString(10);
			String wantAssetName = resultSet.getString(11);

			return new OrderData(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled, price,
					timestamp, isClosed, isFulfilled, haveAssetName, wantAssetName);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset order from repository", e);
		}
	}

	@Override
	public List<OrderData> getOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset,
			Boolean reverse) throws DataException {
		List<OrderData> orders = new ArrayList<>();

		// Cache have & want asset names for later use, which also saves a table join
		AssetData haveAssetData = this.fromAssetId(haveAssetId);
		if (haveAssetData == null)
			return orders;

		AssetData wantAssetData = this.fromAssetId(wantAssetId);
		if (wantAssetData == null)
			return orders;

		StringBuilder sql = new StringBuilder(512);
		sql.append("SELECT creator, asset_order_id, amount, fulfilled, price, ordered_when FROM AssetOrders "
				+ "WHERE have_asset_id = ? AND want_asset_id = ? AND NOT is_closed AND NOT is_fulfilled ");

		sql.append("ORDER BY price");
		if (reverse != null && reverse)
			sql.append(" DESC");

		sql.append(", ordered_when");
		if (reverse != null && reverse)
			sql.append(" DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return orders;

			do {
				byte[] creatorPublicKey = resultSet.getBytes(1);
				byte[] orderId = resultSet.getBytes(2);
				long amount = resultSet.getLong(3);
				long fulfilled = resultSet.getLong(4);
				long price = resultSet.getLong(5);
				long timestamp = resultSet.getLong(6);
				boolean isClosed = false;
				boolean isFulfilled = false;

				OrderData order = new OrderData(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled,
						price, timestamp, isClosed, isFulfilled, haveAssetData.getName(), wantAssetData.getName());
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch open asset orders from repository", e);
		}
	}

	@Override
	public List<OrderData> getOpenOrdersForTrading(long haveAssetId, long wantAssetId, Long minimumPrice) throws DataException {
		List<Object> bindParams = new ArrayList<>(3);

		StringBuilder sql = new StringBuilder(512);
		sql.append("SELECT creator, asset_order_id, amount, fulfilled, price, ordered_when FROM AssetOrders "
				+ "WHERE have_asset_id = ? AND want_asset_id = ? AND NOT is_closed AND NOT is_fulfilled ");

		Collections.addAll(bindParams, haveAssetId, wantAssetId);

		if (minimumPrice != null) {
			// 'new' pricing scheme implied
			// NOTE: haveAssetId and wantAssetId are for TARGET orders, so different from Order.process() caller
			if (haveAssetId < wantAssetId)
				sql.append("AND price >= ? ");
			else
				sql.append("AND price <= ? ");

			bindParams.add(minimumPrice);
		}

		sql.append("ORDER BY price");
		if (minimumPrice != null && haveAssetId < wantAssetId)
			sql.append(" DESC");

		sql.append(", ordered_when");

		List<OrderData> orders = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), bindParams.toArray())) {
			if (resultSet == null)
				return orders;

			do {
				byte[] creatorPublicKey = resultSet.getBytes(1);
				byte[] orderId = resultSet.getBytes(2);
				long amount = resultSet.getLong(3);
				long fulfilled = resultSet.getLong(4);
				long price = resultSet.getLong(5);
				long timestamp = resultSet.getLong(6);
				boolean isClosed = false;
				boolean isFulfilled = false;

				// We don't need asset names so we can use simpler constructor
				OrderData order = new OrderData(orderId, creatorPublicKey, haveAssetId, wantAssetId, amount, fulfilled,
						price, timestamp, isClosed, isFulfilled);
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch open asset orders for trading from repository", e);
		}
	}

	@Override
	public List<OrderData> getAggregatedOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset,
			Boolean reverse) throws DataException {
		List<OrderData> orders = new ArrayList<>();

		// Cache have & want asset names for later use, which also saves a table join
		AssetData haveAssetData = this.fromAssetId(haveAssetId);
		if (haveAssetData == null)
			return orders;

		AssetData wantAssetData = this.fromAssetId(wantAssetId);
		if (wantAssetData == null)
			return orders;

		StringBuilder sql = new StringBuilder(512);
		sql.append("SELECT price, SUM(amount - fulfilled), MAX(ordered_when) FROM AssetOrders "
				+ "WHERE have_asset_id = ? AND want_asset_id = ? AND NOT is_closed AND NOT is_fulfilled "
				+ "GROUP BY price ");

		sql.append("ORDER BY price");
		if (reverse != null && reverse)
			sql.append(" DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return orders;

			do {
				long price = resultSet.getLong(1);
				long totalUnfulfilled = resultSet.getLong(2);
				long timestamp = resultSet.getLong(3);

				OrderData order = new OrderData(null, null, haveAssetId, wantAssetId, totalUnfulfilled, 0L,
						price, timestamp, false, false, haveAssetData.getName(), wantAssetData.getName());
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch aggregated open asset orders from repository", e);
		}
	}

	@Override
	public List<OrderData> getAccountsOrders(byte[] publicKey, Boolean optIsClosed, Boolean optIsFulfilled,
			Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(1024);
		sql.append("SELECT asset_order_id, have_asset_id, want_asset_id, amount, fulfilled, price, ordered_when, "
				+ "is_closed, is_fulfilled, HaveAsset.asset_name, WantAsset.asset_name "
				+ "FROM AssetOrders "
				+ "JOIN Assets AS HaveAsset ON HaveAsset.asset_id = have_asset_id "
				+ "JOIN Assets AS WantAsset ON WantAsset.asset_id = want_asset_id "
				+ "WHERE creator = ?");

		if (optIsClosed != null) {
			sql.append(" AND is_closed = ");
			sql.append(optIsClosed ? "TRUE" : "FALSE");
		}

		if (optIsFulfilled != null) {
			sql.append(" AND is_fulfilled = ");
			sql.append(optIsFulfilled ? "TRUE" : "FALSE");
		}

		sql.append(" ORDER BY ordered_when");
		if (reverse != null && reverse)
			sql.append(" DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<OrderData> orders = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), publicKey)) {
			if (resultSet == null)
				return orders;

			do {
				byte[] orderId = resultSet.getBytes(1);
				long haveAssetId = resultSet.getLong(2);
				long wantAssetId = resultSet.getLong(3);
				long amount = resultSet.getLong(4);
				long fulfilled = resultSet.getLong(5);
				long price = resultSet.getLong(6);
				long timestamp = resultSet.getLong(7);
				boolean isClosed = resultSet.getBoolean(8);
				boolean isFulfilled = resultSet.getBoolean(9);
				String haveAssetName = resultSet.getString(10);
				String wantAssetName = resultSet.getString(11);

				OrderData order = new OrderData(orderId, publicKey, haveAssetId, wantAssetId, amount, fulfilled,
						price, timestamp, isClosed, isFulfilled, haveAssetName, wantAssetName);
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's asset orders from repository", e);
		}
	}

	@Override
	public List<OrderData> getAccountsOrders(byte[] publicKey, long haveAssetId, long wantAssetId, Boolean optIsClosed,
			Boolean optIsFulfilled, Integer limit, Integer offset, Boolean reverse) throws DataException {
		List<OrderData> orders = new ArrayList<>();

		// Cache have & want asset names for later use, which also saves a table join
		AssetData haveAssetData = this.fromAssetId(haveAssetId);
		if (haveAssetData == null)
			return orders;

		AssetData wantAssetData = this.fromAssetId(wantAssetId);
		if (wantAssetData == null)
			return orders;

		StringBuilder sql = new StringBuilder(1024);
		sql.append("SELECT asset_order_id, amount, fulfilled, price, ordered_when, is_closed, is_fulfilled "
				+ "FROM AssetOrders "
				+ "WHERE creator = ? AND have_asset_id = ? AND want_asset_id = ?");

		if (optIsClosed != null) {
			sql.append(" AND is_closed = ");
			sql.append(optIsClosed ? "TRUE" : "FALSE");
		}

		if (optIsFulfilled != null) {
			sql.append(" AND is_fulfilled = ");
			sql.append(optIsFulfilled ? "TRUE" : "FALSE");
		}

		sql.append(" ORDER BY ordered_when");
		if (reverse != null && reverse)
			sql.append(" DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), publicKey, haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return orders;

			do {
				byte[] orderId = resultSet.getBytes(1);
				long amount = resultSet.getLong(2);
				long fulfilled = resultSet.getLong(3);
				long price = resultSet.getLong(4);
				long timestamp = resultSet.getLong(5);
				boolean isClosed = resultSet.getBoolean(6);
				boolean isFulfilled = resultSet.getBoolean(7);

				OrderData order = new OrderData(orderId, publicKey, haveAssetId, wantAssetId, amount, fulfilled,
						price, timestamp, isClosed, isFulfilled, haveAssetData.getName(), wantAssetData.getName());
				orders.add(order);
			} while (resultSet.next());

			return orders;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch account's asset orders from repository", e);
		}
	}

	@Override
	public void save(OrderData orderData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AssetOrders");

		saveHelper.bind("asset_order_id", orderData.getOrderId()).bind("creator", orderData.getCreatorPublicKey())
				.bind("have_asset_id", orderData.getHaveAssetId()).bind("want_asset_id", orderData.getWantAssetId())
				.bind("amount", orderData.getAmount()).bind("fulfilled", orderData.getFulfilled())
				.bind("price", orderData.getPrice()).bind("ordered_when", orderData.getTimestamp())
				.bind("is_closed", orderData.getIsClosed()).bind("is_fulfilled", orderData.getIsFulfilled());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save asset order into repository", e);
		}
	}

	@Override
	public void delete(byte[] orderId) throws DataException {
		try {
			this.repository.delete("AssetOrders", "asset_order_id = ?", orderId);
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset order from repository", e);
		}
	}

	// Trades

	@Override
	public List<TradeData> getTrades(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse)
			throws DataException {
		List<TradeData> trades = new ArrayList<>();

		// Cache have & want asset names for later use, which also saves a table join
		AssetData haveAssetData = this.fromAssetId(haveAssetId);
		if (haveAssetData == null)
			return trades;

		AssetData wantAssetData = this.fromAssetId(wantAssetId);
		if (wantAssetData == null)
			return trades;

		StringBuilder sql = new StringBuilder(512);
		sql.append("SELECT initiating_order_id, target_order_id, target_amount, initiator_amount, initiator_saving, traded_when "
			+ "FROM AssetOrders JOIN AssetTrades ON initiating_order_id = asset_order_id "
			+ "WHERE have_asset_id = ? AND want_asset_id = ? ");

		sql.append("ORDER BY traded_when");
		if (reverse != null && reverse)
			sql.append(" DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), haveAssetId, wantAssetId)) {
			if (resultSet == null)
				return trades;

			do {
				byte[] initiatingOrderId = resultSet.getBytes(1);
				byte[] targetOrderId = resultSet.getBytes(2);
				long targetAmount = resultSet.getLong(3);
				long initiatorAmount = resultSet.getLong(4);
				long initiatorSaving = resultSet.getLong(5);
				long timestamp = resultSet.getLong(6);

				TradeData trade = new TradeData(initiatingOrderId, targetOrderId, targetAmount, initiatorAmount, initiatorSaving,
						timestamp, haveAssetId, haveAssetData.getName(), wantAssetId, wantAssetData.getName());
				trades.add(trade);
			} while (resultSet.next());

			return trades;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset trades from repository", e);
		}
	}

	@Override
	public List<RecentTradeData> getRecentTrades(List<Long> assetIds, List<Long> otherAssetIds, Integer limit,
			Integer offset, Boolean reverse) throws DataException {
		// Find assetID pairs that have actually been traded
		StringBuilder tradedAssetsSubquery = new StringBuilder(1024);
		tradedAssetsSubquery.append("SELECT have_asset_id, want_asset_id "
				+ "FROM AssetTrades JOIN AssetOrders ON asset_order_id = initiating_order_id ");

		// Optionally limit traded assetID pairs
		if (!assetIds.isEmpty()) {
			// longs are safe enough to use literally
			tradedAssetsSubquery.append("WHERE have_asset_id IN (");

			final int assetIdsSize = assetIds.size();
			for (int ai = 0; ai < assetIdsSize; ++ai) {
				if (ai != 0)
					tradedAssetsSubquery.append(", ");

				tradedAssetsSubquery.append(assetIds.get(ai));
			}

			tradedAssetsSubquery.append(")");
		}

		if (!otherAssetIds.isEmpty()) {
			tradedAssetsSubquery.append(assetIds.isEmpty() ? " WHERE " : " AND ");

			// longs are safe enough to use literally
			tradedAssetsSubquery.append("want_asset_id IN (");

			final int otherAssetIdsSize = otherAssetIds.size();
			for (int oai = 0; oai < otherAssetIdsSize; ++oai) {
				if (oai != 0)
					tradedAssetsSubquery.append(", ");

				tradedAssetsSubquery.append(otherAssetIds.get(oai));
			}

			tradedAssetsSubquery.append(")");
		}

		tradedAssetsSubquery.append(" GROUP BY have_asset_id, want_asset_id");

		// Find recent trades using "TradedAssets" assetID pairs
		String recentTradesSubquery = "SELECT AssetTrades.target_amount, AssetTrades.initiator_amount, AssetTrades.traded_when "
				+ "FROM AssetOrders JOIN AssetTrades ON initiating_order_id = asset_order_id "
				+ "WHERE AssetOrders.have_asset_id = TradedAssets.have_asset_id AND AssetOrders.want_asset_id = TradedAssets.want_asset_id "
				+ "ORDER BY traded_when DESC LIMIT 2";

		// Put it all together
		StringBuilder sql = new StringBuilder(4096);
		sql.append("SELECT have_asset_id, want_asset_id, RecentTrades.target_amount, RecentTrades.initiator_amount, RecentTrades.traded_when FROM (");
		sql.append(tradedAssetsSubquery);
		sql.append(") AS TradedAssets, LATERAL (");
		sql.append(recentTradesSubquery);
		sql.append(") AS RecentTrades (target_amount, initiator_amount, traded_when) ORDER BY have_asset_id");
		if (reverse != null && reverse)
			sql.append(" DESC");

		sql.append(", want_asset_id");
		if (reverse != null && reverse)
			sql.append(" DESC");

		sql.append(", RecentTrades.traded_when DESC ");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<RecentTradeData> recentTrades = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString())) {
			if (resultSet == null)
				return recentTrades;

			do {
				long haveAssetId = resultSet.getLong(1);
				long wantAssetId = resultSet.getLong(2);
				long otherAmount = resultSet.getLong(3);
				long amount = resultSet.getLong(4);
				long timestamp = resultSet.getLong(5);

				RecentTradeData recentTrade = new RecentTradeData(haveAssetId, wantAssetId, otherAmount, amount,
						timestamp);
				recentTrades.add(recentTrade);
			} while (resultSet.next());

			return recentTrades;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch recent asset trades from repository", e);
		}
	}

	@Override
	public List<TradeData> getOrdersTrades(byte[] orderId, Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(512);
		sql.append("SELECT initiating_order_id, target_order_id, target_amount, initiator_amount, initiator_saving, traded_when, "
				+ "have_asset_id, HaveAsset.asset_name, want_asset_id, WantAsset.asset_name "
				+ "FROM AssetTrades "
				+ "JOIN AssetOrders ON asset_order_id = initiating_order_id "
				+ "JOIN Assets AS HaveAsset ON HaveAsset.asset_id = have_asset_id "
				+ "JOIN Assets AS WantAsset ON WantAsset.asset_id = want_asset_id "
				+ "WHERE ? IN (initiating_order_id, target_order_id) ");

		sql.append("ORDER BY traded_when");
		if (reverse != null && reverse)
			sql.append(" DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<TradeData> trades = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), orderId)) {
			if (resultSet == null)
				return trades;

			do {
				byte[] initiatingOrderId = resultSet.getBytes(1);
				byte[] targetOrderId = resultSet.getBytes(2);
				long targetAmount = resultSet.getLong(3);
				long initiatorAmount = resultSet.getLong(4);
				long initiatorSaving = resultSet.getLong(5);
				long timestamp = resultSet.getLong(6);

				long haveAssetId = resultSet.getLong(7);
				String haveAssetName = resultSet.getString(8);
				long wantAssetId = resultSet.getLong(9);
				String wantAssetName = resultSet.getString(10);

				TradeData trade = new TradeData(initiatingOrderId, targetOrderId, targetAmount, initiatorAmount, initiatorSaving, timestamp,
						haveAssetId, haveAssetName, wantAssetId, wantAssetName);
				trades.add(trade);
			} while (resultSet.next());

			return trades;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch asset order's trades from repository", e);
		}
	}

	@Override
	public void save(TradeData tradeData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("AssetTrades");

		saveHelper.bind("initiating_order_id", tradeData.getInitiator()).bind("target_order_id", tradeData.getTarget())
				.bind("target_amount", tradeData.getTargetAmount()).bind("initiator_amount", tradeData.getInitiatorAmount())
				.bind("initiator_saving", tradeData.getInitiatorSaving()).bind("traded_when", tradeData.getTimestamp());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save asset trade into repository", e);
		}
	}

	@Override
	public void delete(TradeData tradeData) throws DataException {
		try {
			this.repository.delete("AssetTrades",
					"initiating_order_id = ? AND target_order_id = ? AND target_amount = ? AND initiator_amount = ?",
					tradeData.getInitiator(), tradeData.getTarget(), tradeData.getTargetAmount(),
					tradeData.getInitiatorAmount());
		} catch (SQLException e) {
			throw new DataException("Unable to delete asset trade from repository", e);
		}
	}

}
