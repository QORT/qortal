package org.qortal.crosschain;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.libdohj.params.DogecoinMainNetParams;
//import org.libdohj.params.DogecoinRegTestParams;
import org.libdohj.params.DogecoinTestNet3Params;
import org.qortal.crosschain.ElectrumX.Server;
import org.qortal.crosschain.ElectrumX.Server.ConnectionType;
import org.qortal.settings.Settings;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public class Dogecoin extends Bitcoiny {

	public static final String CURRENCY_CODE = "DOGE";

	private static final Coin DEFAULT_FEE_PER_KB = Coin.valueOf(500000000); // 5 DOGE per 1000 bytes

	private static final long MINIMUM_ORDER_AMOUNT = 300000000L; // 3 DOGE minimum order. The RPC dust threshold is around 2 DOGE

	// Temporary values until a dynamic fee system is written.
	private static final long MAINNET_FEE = 110000000L;
	private static final long NON_MAINNET_FEE = 10000L; // TODO: calibrate this

	private static final Map<ConnectionType, Integer> DEFAULT_ELECTRUMX_PORTS = new EnumMap<>(ConnectionType.class);
	static {
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.TCP, 50001);
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.SSL, 50002);
	}

	public enum DogecoinNet {
		MAIN {
			@Override
			public NetworkParameters getParams() {
				return DogecoinMainNetParams.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(
						// Servers chosen on NO BASIS WHATSOEVER from various sources!
						new Server("electrum1.cipig.net", ConnectionType.TCP, 10060),
						new Server("electrum2.cipig.net", ConnectionType.TCP, 10060),
						new Server("electrum3.cipig.net", ConnectionType.TCP, 10060));
						// TODO: add more mainnet servers. It's too centralized.
			}

			@Override
			public String getGenesisHash() {
				return "1a91e3dace36e2be3bf030a65679fe821aa1d6ef92e7c9902eb318182c355691";
			}

			@Override
			public long getP2shFee(Long timestamp) {
				// TODO: This will need to be replaced with something better in the near future!
				return MAINNET_FEE;
			}
		},
		TEST3 {
			@Override
			public NetworkParameters getParams() {
				return DogecoinTestNet3Params.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(); // TODO: find testnet servers
			}

			@Override
			public String getGenesisHash() {
				return "4966625a4b2851d9fdee139e56211a0d88575f59ed816ff5e6a63deb4e3e29a0";
			}

			@Override
			public long getP2shFee(Long timestamp) {
				return NON_MAINNET_FEE;
			}
		},
		REGTEST {
			@Override
			public NetworkParameters getParams() {
				return null; // TODO: DogecoinRegTestParams.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(
						new Server("localhost", ConnectionType.TCP, 50001),
						new Server("localhost", ConnectionType.SSL, 50002));
			}

			@Override
			public String getGenesisHash() {
				// This is unique to each regtest instance
				return null;
			}

			@Override
			public long getP2shFee(Long timestamp) {
				return NON_MAINNET_FEE;
			}
		};

		public abstract NetworkParameters getParams();
		public abstract Collection<Server> getServers();
		public abstract String getGenesisHash();
		public abstract long getP2shFee(Long timestamp) throws ForeignBlockchainException;
	}

	private static Dogecoin instance;

	private final DogecoinNet dogecoinNet;

	// Constructors and instance

	private Dogecoin(DogecoinNet dogecoinNet, BitcoinyBlockchainProvider blockchain, Context bitcoinjContext, String currencyCode) {
		super(blockchain, bitcoinjContext, currencyCode);
		this.dogecoinNet = dogecoinNet;

		LOGGER.info(() -> String.format("Starting Dogecoin support using %s", this.dogecoinNet.name()));
	}

	public static synchronized Dogecoin getInstance() {
		if (instance == null) {
			DogecoinNet dogecoinNet = Settings.getInstance().getDogecoinNet();

			BitcoinyBlockchainProvider electrumX = new ElectrumX("Dogecoin-" + dogecoinNet.name(), dogecoinNet.getGenesisHash(), dogecoinNet.getServers(), DEFAULT_ELECTRUMX_PORTS);
			Context bitcoinjContext = new Context(dogecoinNet.getParams());

			instance = new Dogecoin(dogecoinNet, electrumX, bitcoinjContext, CURRENCY_CODE);
		}

		return instance;
	}

	// Getters & setters

	public static synchronized void resetForTesting() {
		instance = null;
	}

	// Actual useful methods for use by other classes

	@Override
	public Coin getFeePerKb() {
		return DEFAULT_FEE_PER_KB;
	}

	@Override
	public long getMinimumOrderAmount() {
		return MINIMUM_ORDER_AMOUNT;
	}

	/**
	 * Returns estimated DOGE fee, in sats per 1000bytes, optionally for historic timestamp.
	 * 
	 * @param timestamp optional milliseconds since epoch, or null for 'now'
	 * @return sats per 1000bytes, or throws ForeignBlockchainException if something went wrong
	 */
	@Override
	public long getP2shFee(Long timestamp) throws ForeignBlockchainException {
		return this.dogecoinNet.getP2shFee(timestamp);
	}

}
