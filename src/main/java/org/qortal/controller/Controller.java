package org.qortal.controller;

import java.awt.TrayIcon.MessageType;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.Security;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.account.PublicKeyAccount;
import org.qortal.api.ApiService;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.block.BlockChain.BlockTimingByHeight;
import org.qortal.controller.Synchronizer.SynchronizationResult;
import org.qortal.controller.repository.PruneManager;
import org.qortal.controller.repository.NamesDatabaseIntegrityCheck;
import org.qortal.controller.tradebot.TradeBot;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.MintingAccountData;
import org.qortal.data.account.RewardShareData;
import org.qortal.data.block.BlockData;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.data.network.OnlineAccountData;
import org.qortal.data.network.PeerChainTipData;
import org.qortal.data.network.PeerData;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.ArbitraryTransactionData.DataType;
import org.qortal.event.Event;
import org.qortal.event.EventBus;
import org.qortal.data.transaction.ChatTransactionData;
import org.qortal.globalization.Translator;
import org.qortal.gui.Gui;
import org.qortal.gui.SysTray;
import org.qortal.network.Network;
import org.qortal.network.Peer;
import org.qortal.network.message.ArbitraryDataMessage;
import org.qortal.network.message.BlockSummariesMessage;
import org.qortal.network.message.CachedBlockMessage;
import org.qortal.network.message.GetArbitraryDataMessage;
import org.qortal.network.message.GetBlockMessage;
import org.qortal.network.message.GetBlockSummariesMessage;
import org.qortal.network.message.GetOnlineAccountsMessage;
import org.qortal.network.message.GetPeersMessage;
import org.qortal.network.message.GetSignaturesV2Message;
import org.qortal.network.message.GetTransactionMessage;
import org.qortal.network.message.GetUnconfirmedTransactionsMessage;
import org.qortal.network.message.HeightV2Message;
import org.qortal.network.message.Message;
import org.qortal.network.message.OnlineAccountsMessage;
import org.qortal.network.message.SignaturesMessage;
import org.qortal.network.message.TransactionMessage;
import org.qortal.network.message.TransactionSignaturesMessage;
import org.qortal.repository.*;
import org.qortal.repository.hsqldb.HSQLDBRepositoryFactory;
import org.qortal.settings.Settings;
import org.qortal.transaction.ArbitraryTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.utils.*;

import com.google.common.primitives.Longs;

public class Controller extends Thread {

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("log4j2.formatMsgNoLookups", "true");
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
	}

	/** Controller start-up time (ms) taken using <tt>System.currentTimeMillis()</tt>. */
	public static final long startTime = System.currentTimeMillis();
	public static final String VERSION_PREFIX = "qortal-";

	private static final Logger LOGGER = LogManager.getLogger(Controller.class);
	private static final long MISBEHAVIOUR_COOLOFF = 10 * 60 * 1000L; // ms
	private static final int MAX_BLOCKCHAIN_TIP_AGE = 5; // blocks
	private static final Object shutdownLock = new Object();
	private static final String repositoryUrlTemplate = "jdbc:hsqldb:file:%s" + File.separator + "blockchain;create=true;hsqldb.full_log_replay=true";
	private static final long ARBITRARY_REQUEST_TIMEOUT = 5 * 1000L; // ms
	private static final long NTP_PRE_SYNC_CHECK_PERIOD = 5 * 1000L; // ms
	private static final long NTP_POST_SYNC_CHECK_PERIOD = 5 * 60 * 1000L; // ms
	private static final long DELETE_EXPIRED_INTERVAL = 5 * 60 * 1000L; // ms
	private static final long RECOVERY_MODE_TIMEOUT = 10 * 60 * 1000L; // ms

	// To do with online accounts list
	private static final long ONLINE_ACCOUNTS_TASKS_INTERVAL = 10 * 1000L; // ms
	private static final long ONLINE_ACCOUNTS_BROADCAST_INTERVAL = 1 * 60 * 1000L; // ms
	public static final long ONLINE_TIMESTAMP_MODULUS = 5 * 60 * 1000L;
	private static final long LAST_SEEN_EXPIRY_PERIOD = (ONLINE_TIMESTAMP_MODULUS * 2) + (1 * 60 * 1000L);
	/** How many (latest) blocks' worth of online accounts we cache */
	private static final int MAX_BLOCKS_CACHED_ONLINE_ACCOUNTS = 2;

	private static volatile boolean isStopping = false;
	private static BlockMinter blockMinter = null;
	private static volatile boolean requestSync = false;
	private static volatile boolean requestSysTrayUpdate = true;
	private static Controller instance;

	private final String buildVersion;
	private final long buildTimestamp; // seconds
	private final String[] savedArgs;

	private ExecutorService callbackExecutor = Executors.newFixedThreadPool(3);
	private volatile boolean notifyGroupMembershipChange = false;

	/** Latest blocks on our chain. Note: tail/last is the latest block. */
	private final Deque<BlockData> latestBlocks = new LinkedList<>();

	/** Cache of BlockMessages, indexed by block signature */
	@SuppressWarnings("serial")
	private final LinkedHashMap<ByteArray, CachedBlockMessage> blockMessageCache = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<ByteArray, CachedBlockMessage> eldest) {
			return this.size() > Settings.getInstance().getBlockCacheSize();
		}
	};

	private long repositoryBackupTimestamp = startTime; // ms
	private long repositoryMaintenanceTimestamp = startTime; // ms
	private long repositoryCheckpointTimestamp = startTime; // ms
	private long ntpCheckTimestamp = startTime; // ms
	private long deleteExpiredTimestamp = startTime + DELETE_EXPIRED_INTERVAL; // ms

	private long onlineAccountsTasksTimestamp = startTime + ONLINE_ACCOUNTS_TASKS_INTERVAL; // ms

	/** Whether we can mint new blocks, as reported by BlockMinter. */
	private volatile boolean isMintingPossible = false;

	/** Synchronization object for sync variables below */
	private final Object syncLock = new Object();
	/** Whether we are attempting to synchronize. */
	private volatile boolean isSynchronizing = false;
	/** Temporary estimate of synchronization progress for SysTray use. */
	private volatile int syncPercent = 0;

	/** Latest block signatures from other peers that we know are on inferior chains. */
	List<ByteArray> inferiorChainSignatures = new ArrayList<>();

	/** Recovery mode, which is used to bring back a stalled network */
	private boolean recoveryMode = false;
	private boolean peersAvailable = true; // peersAvailable must default to true
	private long timePeersLastAvailable = 0;

	/**
	 * Map of recent requests for ARBITRARY transaction data payloads.
	 * <p>
	 * Key is original request's message ID<br>
	 * Value is Triple&lt;transaction signature in base58, first requesting peer, first request's timestamp&gt;
	 * <p>
	 * If peer is null then either:<br>
	 * <ul>
	 * <li>we are the original requesting peer</li>
	 * <li>we have already sent data payload to original requesting peer.</li>
	 * </ul>
	 * If signature is null then we have already received the data payload and either:<br>
	 * <ul>
	 * <li>we are the original requesting peer and have saved it locally</li>
	 * <li>we have forwarded the data payload (and maybe also saved it locally)</li>
	 * </ul>
	 */
	private Map<Integer, Triple<String, Peer, Long>> arbitraryDataRequests = Collections.synchronizedMap(new HashMap<>());

	/** Lock for only allowing one blockchain-modifying codepath at a time. e.g. synchronization or newly minted block. */
	private final ReentrantLock blockchainLock = new ReentrantLock();

	/** Cache of current 'online accounts' */
	List<OnlineAccountData> onlineAccounts = new ArrayList<>();
	/** Cache of latest blocks' online accounts */
	Deque<List<OnlineAccountData>> latestBlocksOnlineAccounts = new ArrayDeque<>(MAX_BLOCKS_CACHED_ONLINE_ACCOUNTS);

	// Stats
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class StatsSnapshot {
		public static class GetBlockMessageStats {
			public AtomicLong requests = new AtomicLong();
			public AtomicLong cacheHits = new AtomicLong();
			public AtomicLong unknownBlocks = new AtomicLong();
			public AtomicLong cacheFills = new AtomicLong();

			public GetBlockMessageStats() {
			}
		}
		public GetBlockMessageStats getBlockMessageStats = new GetBlockMessageStats();

		public static class GetBlockSummariesStats {
			public AtomicLong requests = new AtomicLong();
			public AtomicLong cacheHits = new AtomicLong();
			public AtomicLong fullyFromCache = new AtomicLong();

			public GetBlockSummariesStats() {
			}
		}
		public GetBlockSummariesStats getBlockSummariesStats = new GetBlockSummariesStats();

		public static class GetBlockSignaturesV2Stats {
			public AtomicLong requests = new AtomicLong();
			public AtomicLong cacheHits = new AtomicLong();
			public AtomicLong fullyFromCache = new AtomicLong();

			public GetBlockSignaturesV2Stats() {
			}
		}
		public GetBlockSignaturesV2Stats getBlockSignaturesV2Stats = new GetBlockSignaturesV2Stats();

		public AtomicLong latestBlocksCacheRefills = new AtomicLong();

		public StatsSnapshot() {
		}
	}
	private final StatsSnapshot stats = new StatsSnapshot();

	// Constructors

	private Controller(String[] args) {
		Properties properties = new Properties();
		try (InputStream in = this.getClass().getResourceAsStream("/build.properties")) {
			properties.load(in);
		} catch (IOException e) {
			throw new RuntimeException("Can't read build.properties resource", e);
		}

		// Determine build timestamp
		String buildTimestampProperty = properties.getProperty("build.timestamp");
		if (buildTimestampProperty == null) {
			throw new RuntimeException("Can't read build.timestamp from build.properties resource");
		}
		if (buildTimestampProperty.startsWith("$")) {
			// Maven vars haven't been replaced - this was most likely built using an IDE, not via mvn package
			this.buildTimestamp = System.currentTimeMillis();
			buildTimestampProperty = "unknown";
		} else {
			this.buildTimestamp = LocalDateTime.parse(buildTimestampProperty, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toEpochSecond(ZoneOffset.UTC);
		}
		LOGGER.info(String.format("Build timestamp: %s", buildTimestampProperty));

		// Determine build version
		String buildVersionProperty = properties.getProperty("build.version");
		if (buildVersionProperty == null) {
			throw new RuntimeException("Can't read build.version from build.properties resource");
		}
		if (buildVersionProperty.contains("${git.commit.id.abbrev}")) {
			// Maven vars haven't been replaced - this was most likely built using an IDE, not via mvn package
			buildVersionProperty = buildVersionProperty.replace("${git.commit.id.abbrev}", "debug");
		}
		this.buildVersion = VERSION_PREFIX + buildVersionProperty;
		LOGGER.info(String.format("Build version: %s", this.buildVersion));

		this.savedArgs = args;
	}

	private static synchronized Controller newInstance(String[] args) {
		instance = new Controller(args);
		return instance;
	}

	public static synchronized Controller getInstance() {
		if (instance == null)
			instance = new Controller(null);

		return instance;
	}

	// Getters / setters

	public static String getRepositoryUrl() {
		return String.format(repositoryUrlTemplate, Settings.getInstance().getRepositoryPath());
	}

	public long getBuildTimestamp() {
		return this.buildTimestamp;
	}

	public String getVersionString() {
		return this.buildVersion;
	}

	public String getVersionStringWithoutPrefix() {
		return this.buildVersion.replaceFirst(VERSION_PREFIX, "");
	}

	/** Returns current blockchain height, or 0 if it's not available. */
	public int getChainHeight() {
		synchronized (this.latestBlocks) {
			BlockData blockData = this.latestBlocks.peekLast();
			if (blockData == null)
				return 0;

			return blockData.getHeight();
		}
	}

	/** Returns highest block, or null if it's not available. */
	public BlockData getChainTip() {
		synchronized (this.latestBlocks) {
			return this.latestBlocks.peekLast();
		}
	}

	public void refillLatestBlocksCache() throws DataException {
		// Set initial chain height/tip
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().getLastBlock();
			int blockCacheSize = Settings.getInstance().getBlockCacheSize();

			synchronized (this.latestBlocks) {
				this.latestBlocks.clear();

				for (int i = 0; i < blockCacheSize && blockData != null; ++i) {
					this.latestBlocks.addFirst(blockData);
					blockData = repository.getBlockRepository().fromHeight(blockData.getHeight() - 1);
				}
			}
		}
	}

	public ReentrantLock getBlockchainLock() {
		return this.blockchainLock;
	}

	/* package */ String[] getSavedArgs() {
		return this.savedArgs;
	}

	public static boolean isStopping() {
		return isStopping;
	}

	// For API use
	public boolean isMintingPossible() {
		return this.isMintingPossible;
	}

	public boolean isSynchronizing() {
		return this.isSynchronizing;
	}

	public Integer getSyncPercent() {
		synchronized (this.syncLock) {
			return this.isSynchronizing ? this.syncPercent : null;
		}
	}

	public boolean getRecoveryMode() {
		return this.recoveryMode;
	}

	// Entry point

	public static void main(String[] args) {
		LOGGER.info("Starting up...");

		// Potential GUI startup with splash screen, etc.
		Gui.getInstance();

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);

		// Load/check settings, which potentially sets up blockchain config, etc.
		try {
			if (args.length > 0)
				Settings.fileInstance(args[0]);
			else
				Settings.getInstance();
		} catch (Throwable t) {
			Gui.getInstance().fatalError("Settings file", t.getMessage());
			return; // Not System.exit() so that GUI can display error
		}

		Controller.newInstance(args);

		LOGGER.info("Starting NTP");
		Long ntpOffset = Settings.getInstance().getTestNtpOffset();
		if (ntpOffset != null)
			NTP.setFixedOffset(ntpOffset);
		else
			NTP.start(Settings.getInstance().getNtpServers());

		LOGGER.info("Starting repository");
		try {
			RepositoryFactory repositoryFactory = new HSQLDBRepositoryFactory(getRepositoryUrl());
			RepositoryManager.setRepositoryFactory(repositoryFactory);
			RepositoryManager.setRequestedCheckpoint(Boolean.TRUE);

			try (final Repository repository = RepositoryManager.getRepository()) {
				RepositoryManager.archive(repository);
				RepositoryManager.prune(repository);
			}
		} catch (DataException e) {
			// If exception has no cause then repository is in use by some other process.
			if (e.getCause() == null) {
				LOGGER.info("Repository in use by another process?");
				Gui.getInstance().fatalError("Repository issue", "Repository in use by another process?");
			} else {
				LOGGER.error("Unable to start repository", e);
				Gui.getInstance().fatalError("Repository issue", e);
			}

			return; // Not System.exit() so that GUI can display error
		}

		// Rebuild Names table and check database integrity
		NamesDatabaseIntegrityCheck namesDatabaseIntegrityCheck = new NamesDatabaseIntegrityCheck();
		namesDatabaseIntegrityCheck.rebuildAllNames();
		namesDatabaseIntegrityCheck.runIntegrityCheck();

		LOGGER.info("Validating blockchain");
		try {
			BlockChain.validate();

			Controller.getInstance().refillLatestBlocksCache();
			LOGGER.info(String.format("Our chain height at start-up: %d", Controller.getInstance().getChainHeight()));
		} catch (DataException e) {
			LOGGER.error("Couldn't validate blockchain", e);
			Gui.getInstance().fatalError("Blockchain validation issue", e);
			return; // Not System.exit() so that GUI can display error
		}

		// Import current trade bot states and minting accounts if they exist
		Controller.importRepositoryData();

		// Add the initial peers to the repository if we don't have any
		Controller.installInitialPeers();

		LOGGER.info("Starting controller");
		Controller.getInstance().start();

		LOGGER.info(String.format("Starting networking on port %d", Settings.getInstance().getListenPort()));
		try {
			Network network = Network.getInstance();
			network.start();
		} catch (IOException | DataException e) {
			LOGGER.error("Unable to start networking", e);
			Controller.getInstance().shutdown();
			Gui.getInstance().fatalError("Networking failure", e);
			return; // Not System.exit() so that GUI can display error
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Thread.currentThread().setName("Shutdown hook");

				Controller.getInstance().shutdown();
			}
		});

		LOGGER.info("Starting block minter");
		blockMinter = new BlockMinter();
		blockMinter.start();

		LOGGER.info("Starting trade-bot");
		TradeBot.getInstance();

		// Arbitrary transaction data manager
		// LOGGER.info("Starting arbitrary-transaction data manager");
		// ArbitraryDataManager.getInstance().start();

		// Auto-update service?
		if (Settings.getInstance().isAutoUpdateEnabled()) {
			LOGGER.info("Starting auto-update");
			AutoUpdate.getInstance().start();
		}

		LOGGER.info(String.format("Starting API on port %d", Settings.getInstance().getApiPort()));
		try {
			ApiService apiService = ApiService.getInstance();
			apiService.start();
		} catch (Exception e) {
			LOGGER.error("Unable to start API", e);
			Controller.getInstance().shutdown();
			Gui.getInstance().fatalError("API failure", e);
			return; // Not System.exit() so that GUI can display error
		}

		// If GUI is enabled, we're no longer starting up but actually running now
		Gui.getInstance().notifyRunning();
	}

	/** Called by AdvancedInstaller's launch EXE in single-instance mode, when an instance is already running. */
	public static void secondaryMain(String[] args) {
		// Return as we don't want to run more than one instance
	}


	// Main thread

	@Override
	public void run() {
		Thread.currentThread().setName("Controller");

		final long repositoryBackupInterval = Settings.getInstance().getRepositoryBackupInterval();
		final long repositoryCheckpointInterval = Settings.getInstance().getRepositoryCheckpointInterval();
		long repositoryMaintenanceInterval = getRandomRepositoryMaintenanceInterval();

		// Start executor service for trimming or pruning
		PruneManager.getInstance().start();

		try {
			while (!isStopping) {
				// Maybe update SysTray
				if (requestSysTrayUpdate) {
					requestSysTrayUpdate = false;
					updateSysTray();
				}

				Thread.sleep(1000);

				final long now = System.currentTimeMillis();

				// Check NTP status
				if (now >= ntpCheckTimestamp) {
					Long ntpTime = NTP.getTime();

					if (ntpTime != null) {
						if (ntpTime != now)
							// Only log if non-zero offset
							LOGGER.info(String.format("Adjusting system time by NTP offset: %dms", ntpTime - now));

						ntpCheckTimestamp = now + NTP_POST_SYNC_CHECK_PERIOD;
						requestSysTrayUpdate = true;
					} else {
						LOGGER.info(String.format("No NTP offset yet"));
						ntpCheckTimestamp = now + NTP_PRE_SYNC_CHECK_PERIOD;
						// We can't do much without a valid NTP time
						continue;
					}
				}

				if (requestSync) {
					requestSync = false;
					potentiallySynchronize();
				}

				// Clean up arbitrary data request cache
				final long requestMinimumTimestamp = now - ARBITRARY_REQUEST_TIMEOUT;
				arbitraryDataRequests.entrySet().removeIf(entry -> entry.getValue().getC() < requestMinimumTimestamp);

				// Time to 'checkpoint' uncommitted repository writes?
				if (now >= repositoryCheckpointTimestamp + repositoryCheckpointInterval) {
					repositoryCheckpointTimestamp = now + repositoryCheckpointInterval;

					RepositoryManager.setRequestedCheckpoint(Boolean.TRUE);
				}

				// Give repository a chance to backup (if enabled)
				if (repositoryBackupInterval > 0 && now >= repositoryBackupTimestamp + repositoryBackupInterval) {
					repositoryBackupTimestamp = now + repositoryBackupInterval;

					if (Settings.getInstance().getShowBackupNotification())
						SysTray.getInstance().showMessage(Translator.INSTANCE.translate("SysTray", "DB_BACKUP"),
								Translator.INSTANCE.translate("SysTray", "CREATING_BACKUP_OF_DB_FILES"),
								MessageType.INFO);

					try {
						// Timeout if the database isn't ready for backing up after 60 seconds
						long timeout = 60 * 1000L;
						RepositoryManager.backup(true, "backup", timeout);

					} catch (TimeoutException e) {
						LOGGER.info("Attempt to backup repository failed due to timeout: {}", e.getMessage());
					}
				}

				// Give repository a chance to perform maintenance (if enabled)
				if (repositoryMaintenanceInterval > 0 && now >= repositoryMaintenanceTimestamp + repositoryMaintenanceInterval) {
					repositoryMaintenanceTimestamp = now + repositoryMaintenanceInterval;

					if (Settings.getInstance().getShowMaintenanceNotification())
						SysTray.getInstance().showMessage(Translator.INSTANCE.translate("SysTray", "DB_MAINTENANCE"),
								Translator.INSTANCE.translate("SysTray", "PERFORMING_DB_MAINTENANCE"),
								MessageType.INFO);

					LOGGER.info("Starting scheduled repository maintenance. This can take a while...");
					try (final Repository repository = RepositoryManager.getRepository()) {

						// Timeout if the database isn't ready for maintenance after 60 seconds
						long timeout = 60 * 1000L;
						repository.performPeriodicMaintenance(timeout);

						LOGGER.info("Scheduled repository maintenance completed");
					} catch (DataException | TimeoutException e) {
						LOGGER.error("Scheduled repository maintenance failed", e);
					}

					// Get a new random interval
					repositoryMaintenanceInterval = getRandomRepositoryMaintenanceInterval();
				}

				// Prune stuck/slow/old peers
				try {
					Network.getInstance().prunePeers();
				} catch (DataException e) {
					LOGGER.warn(String.format("Repository issue when trying to prune peers: %s", e.getMessage()));
				}

				// Delete expired transactions
				if (now >= deleteExpiredTimestamp) {
					deleteExpiredTimestamp = now + DELETE_EXPIRED_INTERVAL;
					deleteExpiredTransactions();
				}

				// Perform tasks to do with managing online accounts list
				if (now >= onlineAccountsTasksTimestamp) {
					onlineAccountsTasksTimestamp = now + ONLINE_ACCOUNTS_TASKS_INTERVAL;
					performOnlineAccountsTasks();
				}
			}
		} catch (InterruptedException e) {
			// Clear interrupted flag so we can shutdown trim threads
			Thread.interrupted();
			// Fall-through to exit
		} finally {
			PruneManager.getInstance().stop();
		}
	}

	/**
	 * Import current trade bot states and minting accounts.
	 * This is needed because the user may have bootstrapped, or there could be a database inconsistency
	 * if the core crashed when computing the nonce during the start of the trade process.
	 */
	private static void importRepositoryData() {
		try (final Repository repository = RepositoryManager.getRepository()) {

			String exportPath = Settings.getInstance().getExportPath();
			try {
				Path importPath = Paths.get(exportPath, "TradeBotStates.json");
				repository.importDataFromFile(importPath.toString());
			} catch (FileNotFoundException e) {
				// Do nothing, as the files will only exist in certain cases
			}

			try {
				Path importPath = Paths.get(exportPath, "MintingAccounts.json");
				repository.importDataFromFile(importPath.toString());
			} catch (FileNotFoundException e) {
				// Do nothing, as the files will only exist in certain cases
			}
			repository.saveChanges();
		}
		catch (DataException | IOException e) {
			LOGGER.info("Unable to import data into repository: {}", e.getMessage());
		}
	}

	private static void installInitialPeers() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			if (repository.getNetworkRepository().getAllPeers().isEmpty()) {
				Network.installInitialPeers(repository);
			}

		} catch (DataException e) {
			// Fail silently as this is an optional step
		}
	}

	private long getRandomRepositoryMaintenanceInterval() {
		final long minInterval = Settings.getInstance().getRepositoryMaintenanceMinInterval();
		final long maxInterval = Settings.getInstance().getRepositoryMaintenanceMaxInterval();
		if (maxInterval == 0) {
			return 0;
		}
		return (new Random().nextLong() % (maxInterval - minInterval)) + minInterval;
	}

	/**
	 * Export current trade bot states and minting accounts.
	 */
	public void exportRepositoryData() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			repository.exportNodeLocalData();

		} catch (DataException e) {
			// Fail silently as this is an optional step
		}
	}

	public static final Predicate<Peer> hasMisbehaved = peer -> {
		final Long lastMisbehaved = peer.getPeerData().getLastMisbehaved();
		return lastMisbehaved != null && lastMisbehaved > NTP.getTime() - MISBEHAVIOUR_COOLOFF;
	};

	public static final Predicate<Peer> hasNoRecentBlock = peer -> {
		final Long minLatestBlockTimestamp = getMinimumLatestBlockTimestamp();
		final PeerChainTipData peerChainTipData = peer.getChainTipData();
		return peerChainTipData == null || peerChainTipData.getLastBlockTimestamp() == null || peerChainTipData.getLastBlockTimestamp() < minLatestBlockTimestamp;
	};

	public static final Predicate<Peer> hasNoOrSameBlock = peer -> {
		final BlockData latestBlockData = getInstance().getChainTip();
		final PeerChainTipData peerChainTipData = peer.getChainTipData();
		return peerChainTipData == null || peerChainTipData.getLastBlockSignature() == null || Arrays.equals(latestBlockData.getSignature(), peerChainTipData.getLastBlockSignature());
	};

	public static final Predicate<Peer> hasOnlyGenesisBlock = peer -> {
		final PeerChainTipData peerChainTipData = peer.getChainTipData();
		return peerChainTipData == null || peerChainTipData.getLastHeight() == null || peerChainTipData.getLastHeight() == 1;
	};

	public static final Predicate<Peer> hasInferiorChainTip = peer -> {
		final PeerChainTipData peerChainTipData = peer.getChainTipData();
		final List<ByteArray> inferiorChainTips = getInstance().inferiorChainSignatures;
		return peerChainTipData == null || peerChainTipData.getLastBlockSignature() == null || inferiorChainTips.contains(new ByteArray(peerChainTipData.getLastBlockSignature()));
	};

	public static final Predicate<Peer> hasOldVersion = peer -> {
		final String minPeerVersion = Settings.getInstance().getMinPeerVersion();
		return peer.isAtLeastVersion(minPeerVersion) == false;
	};

	private void potentiallySynchronize() throws InterruptedException {
		// Already synchronizing via another thread?
		if (this.isSynchronizing)
			return;

		List<Peer> peers = Network.getInstance().getHandshakedPeers();

		// Disregard peers that have "misbehaved" recently
		peers.removeIf(hasMisbehaved);

		// Disregard peers that only have genesis block
		peers.removeIf(hasOnlyGenesisBlock);

		// Disregard peers that don't have a recent block
		peers.removeIf(hasNoRecentBlock);

		// Disregard peers that are on an old version
		peers.removeIf(hasOldVersion);

		checkRecoveryModeForPeers(peers);
		if (recoveryMode) {
			peers = Network.getInstance().getHandshakedPeers();
			peers.removeIf(hasOnlyGenesisBlock);
			peers.removeIf(hasMisbehaved);
			peers.removeIf(hasOldVersion);
		}

		// Check we have enough peers to potentially synchronize
		if (peers.size() < Settings.getInstance().getMinBlockchainPeers())
			return;

		// Disregard peers that have no block signature or the same block signature as us
		peers.removeIf(hasNoOrSameBlock);

		// Disregard peers that are on the same block as last sync attempt and we didn't like their chain
		peers.removeIf(hasInferiorChainTip);

		final int peersBeforeComparison = peers.size();

		// Request recent block summaries from the remaining peers, and locate our common block with each
		Synchronizer.getInstance().findCommonBlocksWithPeers(peers);

		// Compare the peers against each other, and against our chain, which will return an updated list excluding those without common blocks
		peers = Synchronizer.getInstance().comparePeers(peers);

		// We may have added more inferior chain tips when comparing peers, so remove any peers that are currently on those chains
		peers.removeIf(hasInferiorChainTip);

		final int peersRemoved = peersBeforeComparison - peers.size();
		if (peersRemoved > 0 && peers.size() > 0)
			LOGGER.debug(String.format("Ignoring %d peers on inferior chains. Peers remaining: %d", peersRemoved, peers.size()));

		if (peers.isEmpty())
			return;

		if (peers.size() > 1) {
			StringBuilder finalPeersString = new StringBuilder();
			for (Peer peer : peers)
				finalPeersString = finalPeersString.length() > 0 ? finalPeersString.append(", ").append(peer) : finalPeersString.append(peer);
			LOGGER.debug(String.format("Choosing random peer from: [%s]", finalPeersString.toString()));
		}

		// Pick random peer to sync with
		int index = new SecureRandom().nextInt(peers.size());
		Peer peer = peers.get(index);

		actuallySynchronize(peer, false);
	}

	public SynchronizationResult actuallySynchronize(Peer peer, boolean force) throws InterruptedException {
		boolean hasStatusChanged = false;
		BlockData priorChainTip = this.getChainTip();

		synchronized (this.syncLock) {
			this.syncPercent = (priorChainTip.getHeight() * 100) / peer.getChainTipData().getLastHeight();

			// Only update SysTray if we're potentially changing height
			if (this.syncPercent < 100) {
				this.isSynchronizing = true;
				hasStatusChanged = true;
			}
		}
		peer.setSyncInProgress(true);

		if (hasStatusChanged)
			updateSysTray();

		try {
			SynchronizationResult syncResult = Synchronizer.getInstance().synchronize(peer, force);
			switch (syncResult) {
				case GENESIS_ONLY:
				case NO_COMMON_BLOCK:
				case TOO_DIVERGENT:
				case INVALID_DATA: {
					// These are more serious results that warrant a cool-off
					LOGGER.info(String.format("Failed to synchronize with peer %s (%s) - cooling off", peer, syncResult.name()));

					// Don't use this peer again for a while
					Network.getInstance().peerMisbehaved(peer);
					break;
				}

				case INFERIOR_CHAIN: {
					// Update our list of inferior chain tips
					ByteArray inferiorChainSignature = new ByteArray(peer.getChainTipData().getLastBlockSignature());
					if (!inferiorChainSignatures.contains(inferiorChainSignature))
						inferiorChainSignatures.add(inferiorChainSignature);

					// These are minor failure results so fine to try again
					LOGGER.debug(() -> String.format("Refused to synchronize with peer %s (%s)", peer, syncResult.name()));

					// Notify peer of our superior chain
					if (!peer.sendMessage(Network.getInstance().buildHeightMessage(peer, priorChainTip)))
						peer.disconnect("failed to notify peer of our superior chain");
					break;
				}

				case NO_REPLY:
				case NO_BLOCKCHAIN_LOCK:
				case REPOSITORY_ISSUE:
					// These are minor failure results so fine to try again
					LOGGER.debug(() -> String.format("Failed to synchronize with peer %s (%s)", peer, syncResult.name()));
					break;

				case SHUTTING_DOWN:
					// Just quietly exit
					break;

				case OK:
					// fall-through...
				case NOTHING_TO_DO: {
					// Update our list of inferior chain tips
					ByteArray inferiorChainSignature = new ByteArray(peer.getChainTipData().getLastBlockSignature());
					if (!inferiorChainSignatures.contains(inferiorChainSignature))
						inferiorChainSignatures.add(inferiorChainSignature);

					LOGGER.debug(() -> String.format("Synchronized with peer %s (%s)", peer, syncResult.name()));
					break;
				}
			}

			// Has our chain tip changed?
			BlockData newChainTip;

			try (final Repository repository = RepositoryManager.getRepository()) {
				newChainTip = repository.getBlockRepository().getLastBlock();
			} catch (DataException e) {
				LOGGER.warn(String.format("Repository issue when trying to fetch post-synchronization chain tip: %s", e.getMessage()));
				return syncResult;
			}

			if (!Arrays.equals(newChainTip.getSignature(), priorChainTip.getSignature())) {
				// Reset our cache of inferior chains
				inferiorChainSignatures.clear();

				Network network = Network.getInstance();
				network.broadcast(broadcastPeer -> network.buildHeightMessage(broadcastPeer, newChainTip));
			}

			return syncResult;
		} finally {
			isSynchronizing = false;
			peer.setSyncInProgress(false);
		}
	}

	private boolean checkRecoveryModeForPeers(List<Peer> qualifiedPeers) {
		List<Peer> handshakedPeers = Network.getInstance().getHandshakedPeers();

		if (handshakedPeers.size() > 0) {
			// There is at least one handshaked peer
			if (qualifiedPeers.isEmpty()) {
				// There are no 'qualified' peers - i.e. peers that have a recent block we can sync to
				boolean werePeersAvailable = peersAvailable;
				peersAvailable = false;

				// If peers only just became unavailable, update our record of the time they were last available
				if (werePeersAvailable)
					timePeersLastAvailable = NTP.getTime();

				// If enough time has passed, enter recovery mode, which lifts some restrictions on who we can sync with and when we can mint
				if (NTP.getTime() - timePeersLastAvailable > RECOVERY_MODE_TIMEOUT) {
					if (recoveryMode == false) {
						LOGGER.info(String.format("Peers have been unavailable for %d minutes. Entering recovery mode...", RECOVERY_MODE_TIMEOUT/60/1000));
						recoveryMode = true;
					}
				}
			} else {
				// We now have at least one peer with a recent block, so we can exit recovery mode and sync normally
				peersAvailable = true;
				if (recoveryMode) {
					LOGGER.info("Peers have become available again. Exiting recovery mode...");
					recoveryMode = false;
				}
			}
		}
		return recoveryMode;
	}

	public void addInferiorChainSignature(byte[] inferiorSignature) {
		// Update our list of inferior chain tips
		ByteArray inferiorChainSignature = new ByteArray(inferiorSignature);
		if (!inferiorChainSignatures.contains(inferiorChainSignature))
			inferiorChainSignatures.add(inferiorChainSignature);
	}

	public static class StatusChangeEvent implements Event {
		public StatusChangeEvent() {
		}
	}

	private void updateSysTray() {
		if (NTP.getTime() == null) {
			SysTray.getInstance().setToolTipText(Translator.INSTANCE.translate("SysTray", "SYNCHRONIZING_CLOCK"));
			SysTray.getInstance().setTrayIcon(1);
			return;
		}

		final int numberOfPeers = Network.getInstance().getHandshakedPeers().size();

		final int height = getChainHeight();

		String connectionsText = Translator.INSTANCE.translate("SysTray", numberOfPeers != 1 ? "CONNECTIONS" : "CONNECTION");
		String heightText = Translator.INSTANCE.translate("SysTray", "BLOCK_HEIGHT");

		String actionText;

		synchronized (this.syncLock) {
			if (this.isMintingPossible) {
				actionText = Translator.INSTANCE.translate("SysTray", "MINTING_ENABLED");
				SysTray.getInstance().setTrayIcon(2);
			}
			else if (this.isSynchronizing) {
				actionText = String.format("%s - %d%%", Translator.INSTANCE.translate("SysTray", "SYNCHRONIZING_BLOCKCHAIN"), this.syncPercent);
				SysTray.getInstance().setTrayIcon(3);
			}
			else if (numberOfPeers < Settings.getInstance().getMinBlockchainPeers()) {
				actionText = Translator.INSTANCE.translate("SysTray", "CONNECTING");
				SysTray.getInstance().setTrayIcon(3);
			}
			else {
				actionText = Translator.INSTANCE.translate("SysTray", "MINTING_DISABLED");
				SysTray.getInstance().setTrayIcon(4);
			}
		}

		String tooltip = String.format("%s - %d %s - %s %d", actionText, numberOfPeers, connectionsText, heightText, height) + "\n" + String.format("%s: %s", Translator.INSTANCE.translate("SysTray", "BUILD_VERSION"), this.buildVersion);
		SysTray.getInstance().setToolTipText(tooltip);

		this.callbackExecutor.execute(() -> {
			EventBus.INSTANCE.notify(new StatusChangeEvent());
		});
	}

	public void deleteExpiredTransactions() {
		final Long now = NTP.getTime();
		if (now == null)
			return;

		// This isn't critical so don't block for repository instance.
		try (final Repository repository = RepositoryManager.tryRepository()) {
			if (repository == null)
				return;

			List<TransactionData> transactions = repository.getTransactionRepository().getUnconfirmedTransactions();

			int deletedCount = 0;
			for (TransactionData transactionData : transactions) {
				Transaction transaction = Transaction.fromData(repository, transactionData);

				if (now >= transaction.getDeadline()) {
					LOGGER.debug(() -> String.format("Deleting expired, unconfirmed transaction %s", Base58.encode(transactionData.getSignature())));
					repository.getTransactionRepository().delete(transactionData);
					deletedCount++;
				}
			}
			if (deletedCount > 0) {
				LOGGER.info(String.format("Deleted %d expired, unconfirmed transaction%s", deletedCount, (deletedCount == 1 ? "" : "s")));
			}

			repository.saveChanges();
		} catch (DataException e) {
			if (RepositoryManager.isDeadlockRelated(e))
				LOGGER.info("Couldn't delete some expired, unconfirmed transactions this round");
			else
				LOGGER.error("Repository issue while deleting expired unconfirmed transactions", e);
		}
	}

	// Shutdown

	public void shutdown() {
		synchronized (shutdownLock) {
			if (!isStopping) {
				isStopping = true;

				LOGGER.info("Shutting down API");
				ApiService.getInstance().stop();

				if (Settings.getInstance().isAutoUpdateEnabled()) {
					LOGGER.info("Shutting down auto-update");
					AutoUpdate.getInstance().shutdown();
				}

				// Arbitrary transaction data manager
				// LOGGER.info("Shutting down arbitrary-transaction data manager");
				// ArbitraryDataManager.getInstance().shutdown();

				if (blockMinter != null) {
					LOGGER.info("Shutting down block minter");
					blockMinter.shutdown();
					try {
						blockMinter.join();
					} catch (InterruptedException e) {
						// We were interrupted while waiting for thread to join
					}
				}

				// Export local data
				LOGGER.info("Backing up local data");
				this.exportRepositoryData();

				LOGGER.info("Shutting down networking");
				Network.getInstance().shutdown();

				LOGGER.info("Shutting down controller");
				this.interrupt();
				try {
					this.join();
				} catch (InterruptedException e) {
					// We were interrupted while waiting for thread to join
				}

				try {
					LOGGER.info("Shutting down repository");
					RepositoryManager.closeRepositoryFactory();
				} catch (DataException e) {
					LOGGER.error("Error occurred while shutting down repository", e);
				}

				LOGGER.info("Shutting down NTP");
				NTP.shutdownNow();

				LOGGER.info("Shutdown complete!");
			}
		}
	}

	public void shutdownAndExit() {
		this.shutdown();
		System.exit(0);
	}

	// Callbacks

	public void onGroupMembershipChange(int groupId) {
		/*
		 * We've likely been called in the middle of block processing,
		 * so set a flag for now as other repository sessions won't 'see'
		 * the group membership change until a call to repository.saveChanges().
		 * 
		 * Eventually, onNewBlock() will be executed and queue a callback task.
		 * This callback task will check the flag and notify websocket listeners, etc.
		 * and those listeners will be post-saveChanges() and hence see the new
		 * group membership state.
		 */
		this.notifyGroupMembershipChange = true;
	}

	// Callbacks for/from network

	public void doNetworkBroadcast() {
		Network network = Network.getInstance();

		// Send (if outbound) / Request peer lists
		network.broadcast(peer -> peer.isOutbound() ? network.buildPeersMessage(peer) : new GetPeersMessage());

		// Send our current height
		BlockData latestBlockData = getChainTip();
		network.broadcast(peer -> network.buildHeightMessage(peer, latestBlockData));

		// Request unconfirmed transaction signatures, but only if we're up-to-date.
		// If we're NOT up-to-date then priority is synchronizing first
		if (isUpToDate())
			network.broadcast(network::buildGetUnconfirmedTransactionsMessage);
	}

	public void onMintingPossibleChange(boolean isMintingPossible) {
		this.isMintingPossible = isMintingPossible;
		requestSysTrayUpdate = true;
	}

	public static class NewBlockEvent implements Event {
		private final BlockData blockData;

		public NewBlockEvent(BlockData blockData) {
			this.blockData = blockData;
		}

		public BlockData getBlockData() {
			return this.blockData;
		}
	}

	/**
	 * Callback for when we've received a new block.
	 * <p>
	 * See <b>WARNING</b> for {@link EventBus#notify(Event)}
	 * to prevent deadlocks.
	 */
	public void onNewBlock(BlockData latestBlockData) {
		// Protective copy
		BlockData blockDataCopy = new BlockData(latestBlockData);
		int blockCacheSize = Settings.getInstance().getBlockCacheSize();

		synchronized (this.latestBlocks) {
			BlockData cachedChainTip = this.latestBlocks.peekLast();

			if (cachedChainTip != null && Arrays.equals(cachedChainTip.getSignature(), blockDataCopy.getReference())) {
				// Chain tip is parent for new latest block, so we can safely add new latest block
				this.latestBlocks.addLast(latestBlockData);

				// Trim if necessary
				if (this.latestBlocks.size() >= blockCacheSize)
					this.latestBlocks.pollFirst();
			} else {
				if (cachedChainTip != null)
					// Chain tip didn't match - potentially abnormal behaviour?
					LOGGER.debug(() -> String.format("Cached chain tip %.8s not parent for new latest block %.8s (reference %.8s)",
							Base58.encode(cachedChainTip.getSignature()),
							Base58.encode(blockDataCopy.getSignature()),
							Base58.encode(blockDataCopy.getReference())));

				// Defensively rebuild cache
				try {
					this.stats.latestBlocksCacheRefills.incrementAndGet();

					this.refillLatestBlocksCache();
				} catch (DataException e) {
					LOGGER.warn(() -> "Couldn't refill latest blocks cache?", e);
				}
			}
		}

		this.onNewOrOrphanedBlock(blockDataCopy, NewBlockEvent::new);
	}

	public static class OrphanedBlockEvent implements Event {
		private final BlockData blockData;

		public OrphanedBlockEvent(BlockData blockData) {
			this.blockData = blockData;
		}

		public BlockData getBlockData() {
			return this.blockData;
		}
	}

	/**
	 * Callback for when we've orphaned a block.
	 * <p>
	 * See <b>WARNING</b> for {@link EventBus#notify(Event)}
	 * to prevent deadlocks.
	 */
	public void onOrphanedBlock(BlockData latestBlockData) {
		// Protective copy
		BlockData blockDataCopy = new BlockData(latestBlockData);

		synchronized (this.latestBlocks) {
			BlockData cachedChainTip = this.latestBlocks.pollLast();
			boolean refillNeeded = false;

			if (cachedChainTip != null && Arrays.equals(cachedChainTip.getReference(), blockDataCopy.getSignature())) {
				// Chain tip was parent for new latest block that has been orphaned, so we're good

				// However, if we've emptied the cache then we will need to refill it
				refillNeeded = this.latestBlocks.isEmpty();
			} else {
				if (cachedChainTip != null)
					// Chain tip didn't match - potentially abnormal behaviour?
					LOGGER.debug(() -> String.format("Cached chain tip %.8s (reference %.8s) was not parent for new latest block %.8s",
							Base58.encode(cachedChainTip.getSignature()),
							Base58.encode(cachedChainTip.getReference()),
							Base58.encode(blockDataCopy.getSignature())));

				// Defensively rebuild cache
				refillNeeded = true;
			}

			if (refillNeeded)
				try {
					this.stats.latestBlocksCacheRefills.incrementAndGet();

					this.refillLatestBlocksCache();
				} catch (DataException e) {
					LOGGER.warn(() -> "Couldn't refill latest blocks cache?", e);
				}
		}

		this.onNewOrOrphanedBlock(blockDataCopy, OrphanedBlockEvent::new);
	}

	private void onNewOrOrphanedBlock(BlockData blockDataCopy, Function<BlockData, Event> eventConstructor) {
		requestSysTrayUpdate = true;

		// Notify listeners, trade-bot, etc.
		EventBus.INSTANCE.notify(eventConstructor.apply(blockDataCopy));

		if (this.notifyGroupMembershipChange) {
			this.notifyGroupMembershipChange = false;
			ChatNotifier.getInstance().onGroupMembershipChange();
		}
	}

	public static class NewTransactionEvent implements Event {
		private final TransactionData transactionData;

		public NewTransactionEvent(TransactionData transactionData) {
			this.transactionData = transactionData;
		}

		public TransactionData getTransactionData() {
			return this.transactionData;
		}
	}

	/**
	 * Callback for when we've received a new transaction via API or peer.
	 * <p>
	 * @implSpec performs actions in a new thread
	 */
	public void onNewTransaction(TransactionData transactionData) {
		this.callbackExecutor.execute(() -> {
			// Notify all peers
			Message newTransactionSignatureMessage = new TransactionSignaturesMessage(Arrays.asList(transactionData.getSignature()));
			Network.getInstance().broadcast(broadcastPeer -> newTransactionSignatureMessage);

			// Notify listeners
			EventBus.INSTANCE.notify(new NewTransactionEvent(transactionData));

			// If this is a CHAT transaction, there may be extra listeners to notify
			if (transactionData.getType() == TransactionType.CHAT)
				ChatNotifier.getInstance().onNewChatTransaction((ChatTransactionData) transactionData);
		});
	}

	public void onPeerHandshakeCompleted(Peer peer) {
		// Only send if outbound
		if (peer.isOutbound()) {
			// Request peer's unconfirmed transactions
			Message message = new GetUnconfirmedTransactionsMessage();
			if (!peer.sendMessage(message)) {
				peer.disconnect("failed to send request for unconfirmed transactions");
				return;
			}
		}

		requestSysTrayUpdate = true;
	}

	public void onPeerDisconnect(Peer peer) {
		requestSysTrayUpdate = true;
	}

	public void onNetworkMessage(Peer peer, Message message) {
		LOGGER.trace(() -> String.format("Processing %s message from %s", message.getType().name(), peer));

		// Ordered by message type value
		switch (message.getType()) {
			case GET_BLOCK:
				onNetworkGetBlockMessage(peer, message);
				break;

			case TRANSACTION:
				onNetworkTransactionMessage(peer, message);
				break;

			case GET_BLOCK_SUMMARIES:
				onNetworkGetBlockSummariesMessage(peer, message);
				break;

			case GET_SIGNATURES_V2:
				onNetworkGetSignaturesV2Message(peer, message);
				break;

			case HEIGHT_V2:
				onNetworkHeightV2Message(peer, message);
				break;

			case GET_TRANSACTION:
				onNetworkGetTransactionMessage(peer, message);
				break;

			case GET_UNCONFIRMED_TRANSACTIONS:
				onNetworkGetUnconfirmedTransactionsMessage(peer, message);
				break;

			case TRANSACTION_SIGNATURES:
				onNetworkTransactionSignaturesMessage(peer, message);
				break;

			case GET_ARBITRARY_DATA:
				onNetworkGetArbitraryDataMessage(peer, message);
				break;

			case ARBITRARY_DATA:
				onNetworkArbitraryDataMessage(peer, message);
				break;

			case GET_ONLINE_ACCOUNTS:
				onNetworkGetOnlineAccountsMessage(peer, message);
				break;

			case ONLINE_ACCOUNTS:
				onNetworkOnlineAccountsMessage(peer, message);
				break;

			default:
				LOGGER.debug(() -> String.format("Unhandled %s message [ID %d] from peer %s", message.getType().name(), message.getId(), peer));
				break;
		}
	}

	private void onNetworkGetBlockMessage(Peer peer, Message message) {
		GetBlockMessage getBlockMessage = (GetBlockMessage) message;
		byte[] signature = getBlockMessage.getSignature();
		this.stats.getBlockMessageStats.requests.incrementAndGet();

		ByteArray signatureAsByteArray = new ByteArray(signature);

		CachedBlockMessage cachedBlockMessage = this.blockMessageCache.get(signatureAsByteArray);
		int blockCacheSize = Settings.getInstance().getBlockCacheSize();

		// Check cached latest block message
		if (cachedBlockMessage != null) {
			this.stats.getBlockMessageStats.cacheHits.incrementAndGet();

			// We need to duplicate it to prevent multiple threads setting ID on the same message
			CachedBlockMessage clonedBlockMessage = cachedBlockMessage.cloneWithNewId(message.getId());

			if (!peer.sendMessage(clonedBlockMessage))
				peer.disconnect("failed to send block");

			return;
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().fromSignature(signature);

			if (blockData != null) {
				if (PruneManager.getInstance().isBlockPruned(blockData.getHeight())) {
					// If this is a pruned block, we likely only have partial data, so best not to sent it
					blockData = null;
				}
			}

			// If we have no block data, we should check the archive in case it's there
			if (blockData == null) {
				if (Settings.getInstance().isArchiveEnabled()) {
					byte[] bytes = BlockArchiveReader.getInstance().fetchSerializedBlockBytesForSignature(signature, true, repository);
					if (bytes != null) {
						CachedBlockMessage blockMessage = new CachedBlockMessage(bytes);
						blockMessage.setId(message.getId());

						// This call also causes the other needed data to be pulled in from repository
						if (!peer.sendMessage(blockMessage)) {
							peer.disconnect("failed to send block");
							// Don't fall-through to caching because failure to send might be from failure to build message
							return;
						}

						// Sent successfully from archive, so nothing more to do
						return;
					}
				}
			}

			if (blockData == null) {
				// We don't have this block
				this.stats.getBlockMessageStats.unknownBlocks.getAndIncrement();

				// Send valid, yet unexpected message type in response, so peer's synchronizer doesn't have to wait for timeout
				LOGGER.debug(() -> String.format("Sending 'block unknown' response to peer %s for GET_BLOCK request for unknown block %s", peer, Base58.encode(signature)));

				// We'll send empty block summaries message as it's very short
				Message blockUnknownMessage = new BlockSummariesMessage(Collections.emptyList());
				blockUnknownMessage.setId(message.getId());
				if (!peer.sendMessage(blockUnknownMessage))
					peer.disconnect("failed to send block-unknown response");
				return;
			}

			Block block = new Block(repository, blockData);

			CachedBlockMessage blockMessage = new CachedBlockMessage(block);
			blockMessage.setId(message.getId());

			// This call also causes the other needed data to be pulled in from repository
			if (!peer.sendMessage(blockMessage)) {
				peer.disconnect("failed to send block");
				// Don't fall-through to caching because failure to send might be from failure to build message
				return;
			}

			// If request is for a recent block, cache it
			if (getChainHeight() - blockData.getHeight() <= blockCacheSize) {
				this.stats.getBlockMessageStats.cacheFills.incrementAndGet();

				this.blockMessageCache.put(new ByteArray(blockData.getSignature()), blockMessage);
			}
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while send block %s to peer %s", Base58.encode(signature), peer), e);
		}
	}

	private void onNetworkTransactionMessage(Peer peer, Message message) {
		TransactionMessage transactionMessage = (TransactionMessage) message;
		TransactionData transactionData = transactionMessage.getTransactionData();

		/*
		 *  If we can't obtain blockchain lock immediately,
		 *  e.g. Synchronizer is active, or another transaction is taking a while to validate,
		 *  then we're using up a network thread for ages and clogging things up
		 *  so bail out early
		 */
		ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
		if (!blockchainLock.tryLock()) {
			LOGGER.trace(() -> String.format("Too busy to import %s transaction %s from peer %s", transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));
			return;
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			// Check signature
			if (!transaction.isSignatureValid()) {
				LOGGER.trace(() -> String.format("Ignoring %s transaction %s with invalid signature from peer %s", transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));
				return;
			}

			ValidationResult validationResult = transaction.importAsUnconfirmed();

			if (validationResult == ValidationResult.TRANSACTION_ALREADY_EXISTS) {
				LOGGER.trace(() -> String.format("Ignoring existing transaction %s from peer %s", Base58.encode(transactionData.getSignature()), peer));
				return;
			}

			if (validationResult == ValidationResult.NO_BLOCKCHAIN_LOCK) {
				LOGGER.trace(() -> String.format("Couldn't lock blockchain to import unconfirmed transaction %s from peer %s", Base58.encode(transactionData.getSignature()), peer));
				return;
			}

			if (validationResult != ValidationResult.OK) {
				LOGGER.trace(() -> String.format("Ignoring invalid (%s) %s transaction %s from peer %s", validationResult.name(), transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));
				return;
			}

			LOGGER.debug(() -> String.format("Imported %s transaction %s from peer %s", transactionData.getType().name(), Base58.encode(transactionData.getSignature()), peer));
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while processing transaction %s from peer %s", Base58.encode(transactionData.getSignature()), peer), e);
		} finally {
			blockchainLock.unlock();
		}
	}

	private void onNetworkGetBlockSummariesMessage(Peer peer, Message message) {
		GetBlockSummariesMessage getBlockSummariesMessage = (GetBlockSummariesMessage) message;
		final byte[] parentSignature = getBlockSummariesMessage.getParentSignature();
		this.stats.getBlockSummariesStats.requests.incrementAndGet();

		// If peer's parent signature matches our latest block signature
		// then we can short-circuit with an empty response
		BlockData chainTip = getChainTip();
		if (chainTip != null && Arrays.equals(parentSignature, chainTip.getSignature())) {
			Message blockSummariesMessage = new BlockSummariesMessage(Collections.emptyList());
			blockSummariesMessage.setId(message.getId());
			if (!peer.sendMessage(blockSummariesMessage))
				peer.disconnect("failed to send block summaries");

			return;
		}

		List<BlockSummaryData> blockSummaries = new ArrayList<>();

		// Attempt to serve from our cache of latest blocks
		synchronized (this.latestBlocks) {
			blockSummaries = this.latestBlocks.stream()
					.dropWhile(cachedBlockData -> !Arrays.equals(cachedBlockData.getReference(), parentSignature))
					.map(BlockSummaryData::new)
					.collect(Collectors.toList());
		}

		if (blockSummaries.isEmpty()) {
			try (final Repository repository = RepositoryManager.getRepository()) {
				int numberRequested = Math.min(Network.MAX_BLOCK_SUMMARIES_PER_REPLY, getBlockSummariesMessage.getNumberRequested());

				BlockData blockData = repository.getBlockRepository().fromReference(parentSignature);
				if (blockData == null) {
					// Try the archive
					blockData = repository.getBlockArchiveRepository().fromReference(parentSignature);
				}

				if (blockData != null) {
					if (PruneManager.getInstance().isBlockPruned(blockData.getHeight())) {
						// If this request contains a pruned block, we likely only have partial data, so best not to sent anything
						// We always prune from the oldest first, so it's fine to just check the first block requested
						blockData = null;
					}
				}

				while (blockData != null && blockSummaries.size() < numberRequested) {
					BlockSummaryData blockSummary = new BlockSummaryData(blockData);
					blockSummaries.add(blockSummary);

					byte[] previousSignature = blockData.getSignature();
					blockData = repository.getBlockRepository().fromReference(previousSignature);
					if (blockData == null) {
						// Try the archive
						blockData = repository.getBlockArchiveRepository().fromReference(previousSignature);
					}
				}
			} catch (DataException e) {
				LOGGER.error(String.format("Repository issue while sending block summaries after %s to peer %s", Base58.encode(parentSignature), peer), e);
			}
		} else {
			this.stats.getBlockSummariesStats.cacheHits.incrementAndGet();

			if (blockSummaries.size() >= getBlockSummariesMessage.getNumberRequested())
				this.stats.getBlockSummariesStats.fullyFromCache.incrementAndGet();
		}

		Message blockSummariesMessage = new BlockSummariesMessage(blockSummaries);
		blockSummariesMessage.setId(message.getId());
		if (!peer.sendMessage(blockSummariesMessage))
			peer.disconnect("failed to send block summaries");
	}

	private void onNetworkGetSignaturesV2Message(Peer peer, Message message) {
		GetSignaturesV2Message getSignaturesMessage = (GetSignaturesV2Message) message;
		final byte[] parentSignature = getSignaturesMessage.getParentSignature();
		this.stats.getBlockSignaturesV2Stats.requests.incrementAndGet();

		// If peer's parent signature matches our latest block signature
		// then we can short-circuit with an empty response
		BlockData chainTip = getChainTip();
		if (chainTip != null && Arrays.equals(parentSignature, chainTip.getSignature())) {
			Message signaturesMessage = new SignaturesMessage(Collections.emptyList());
			signaturesMessage.setId(message.getId());
			if (!peer.sendMessage(signaturesMessage))
				peer.disconnect("failed to send signatures (v2)");

			return;
		}

		List<byte[]> signatures = new ArrayList<>();

		// Attempt to serve from our cache of latest blocks
		synchronized (this.latestBlocks) {
			signatures = this.latestBlocks.stream()
					.dropWhile(cachedBlockData -> !Arrays.equals(cachedBlockData.getReference(), parentSignature))
					.map(BlockData::getSignature)
					.collect(Collectors.toList());
		}

		if (signatures.isEmpty()) {
			try (final Repository repository = RepositoryManager.getRepository()) {
				int numberRequested = getSignaturesMessage.getNumberRequested();
				BlockData blockData = repository.getBlockRepository().fromReference(parentSignature);
				if (blockData == null) {
					// Try the archive
					blockData = repository.getBlockArchiveRepository().fromReference(parentSignature);
				}

				while (blockData != null && signatures.size() < numberRequested) {
					signatures.add(blockData.getSignature());

					byte[] previousSignature = blockData.getSignature();
					blockData = repository.getBlockRepository().fromReference(previousSignature);
					if (blockData == null) {
						// Try the archive
						blockData = repository.getBlockArchiveRepository().fromReference(previousSignature);
					}
				}
			} catch (DataException e) {
				LOGGER.error(String.format("Repository issue while sending V2 signatures after %s to peer %s", Base58.encode(parentSignature), peer), e);
			}
		} else {
			this.stats.getBlockSignaturesV2Stats.cacheHits.incrementAndGet();

			if (signatures.size() >= getSignaturesMessage.getNumberRequested())
				this.stats.getBlockSignaturesV2Stats.fullyFromCache.incrementAndGet();
		}

		Message signaturesMessage = new SignaturesMessage(signatures);
		signaturesMessage.setId(message.getId());
		if (!peer.sendMessage(signaturesMessage))
			peer.disconnect("failed to send signatures (v2)");
	}

	private void onNetworkHeightV2Message(Peer peer, Message message) {
		HeightV2Message heightV2Message = (HeightV2Message) message;

		// If peer is inbound and we've not updated their height
		// then this is probably their initial HEIGHT_V2 message
		// so they need a corresponding HEIGHT_V2 message from us
		if (!peer.isOutbound() && (peer.getChainTipData() == null || peer.getChainTipData().getLastHeight() == null))
			peer.sendMessage(Network.getInstance().buildHeightMessage(peer, getChainTip()));

		// Update peer chain tip data
		PeerChainTipData newChainTipData = new PeerChainTipData(heightV2Message.getHeight(), heightV2Message.getSignature(), heightV2Message.getTimestamp(), heightV2Message.getMinterPublicKey());
		peer.setChainTipData(newChainTipData);

		// Potentially synchronize
		requestSync = true;
	}

	private void onNetworkGetTransactionMessage(Peer peer, Message message) {
		GetTransactionMessage getTransactionMessage = (GetTransactionMessage) message;
		byte[] signature = getTransactionMessage.getSignature();

		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
			if (transactionData == null) {
				LOGGER.debug(() -> String.format("Ignoring GET_TRANSACTION request from peer %s for unknown transaction %s", peer, Base58.encode(signature)));
				// Send no response at all???
				return;
			}

			Message transactionMessage = new TransactionMessage(transactionData);
			transactionMessage.setId(message.getId());
			if (!peer.sendMessage(transactionMessage))
				peer.disconnect("failed to send transaction");
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while send transaction %s to peer %s", Base58.encode(signature), peer), e);
		}
	}

	private void onNetworkGetUnconfirmedTransactionsMessage(Peer peer, Message message) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<byte[]> signatures = Collections.emptyList();

			// If we're NOT up-to-date then don't send out unconfirmed transactions
			// as it's possible they are already included in a later block that we don't have.
			if (isUpToDate())
				signatures = repository.getTransactionRepository().getUnconfirmedTransactionSignatures();

			Message transactionSignaturesMessage = new TransactionSignaturesMessage(signatures);
			if (!peer.sendMessage(transactionSignaturesMessage))
				peer.disconnect("failed to send unconfirmed transaction signatures");
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while sending unconfirmed transaction signatures to peer %s", peer), e);
		}
	}

	private void onNetworkTransactionSignaturesMessage(Peer peer, Message message) {
		TransactionSignaturesMessage transactionSignaturesMessage = (TransactionSignaturesMessage) message;
		List<byte[]> signatures = transactionSignaturesMessage.getSignatures();

		try (final Repository repository = RepositoryManager.getRepository()) {
			for (byte[] signature : signatures) {
				// Do we have it already? (Before requesting transaction data itself)
				if (repository.getTransactionRepository().exists(signature)) {
					LOGGER.trace(() -> String.format("Ignoring existing transaction %s from peer %s", Base58.encode(signature), peer));
					continue;
				}

				// Check isInterrupted() here and exit fast
				if (Thread.currentThread().isInterrupted())
					return;

				// Fetch actual transaction data from peer
				Message getTransactionMessage = new GetTransactionMessage(signature);
				if (!peer.sendMessage(getTransactionMessage)) {
					peer.disconnect("failed to request transaction");
					return;
				}
			}
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while processing unconfirmed transactions from peer %s", peer), e);
		}
	}

	private void onNetworkGetArbitraryDataMessage(Peer peer, Message message) {
		GetArbitraryDataMessage getArbitraryDataMessage = (GetArbitraryDataMessage) message;

		byte[] signature = getArbitraryDataMessage.getSignature();
		String signature58 = Base58.encode(signature);
		Long timestamp = NTP.getTime();
		Triple<String, Peer, Long> newEntry = new Triple<>(signature58, peer, timestamp);

		// If we've seen this request recently, then ignore
		if (arbitraryDataRequests.putIfAbsent(message.getId(), newEntry) != null)
			return;

		// Do we even have this transaction?
		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
			if (transactionData == null || transactionData.getType() != TransactionType.ARBITRARY)
				return;

			ArbitraryTransaction transaction = new ArbitraryTransaction(repository, transactionData);

			// If we have the data then send it
			if (transaction.isDataLocal()) {
				byte[] data = transaction.fetchData();
				if (data == null)
					return;

				// Update requests map to reflect that we've sent it
				newEntry = new Triple<>(signature58, null, timestamp);
				arbitraryDataRequests.put(message.getId(), newEntry);

				Message arbitraryDataMessage = new ArbitraryDataMessage(signature, data);
				arbitraryDataMessage.setId(message.getId());
				if (!peer.sendMessage(arbitraryDataMessage))
					peer.disconnect("failed to send arbitrary data");

				return;
			}

			// Ask our other peers if they have it
			Network.getInstance().broadcast(broadcastPeer -> broadcastPeer == peer ? null : message);
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while finding arbitrary transaction data for peer %s", peer), e);
		}
	}

	private void onNetworkArbitraryDataMessage(Peer peer, Message message) {
		ArbitraryDataMessage arbitraryDataMessage = (ArbitraryDataMessage) message;

		// Do we have a pending request for this data?
		Triple<String, Peer, Long> request = arbitraryDataRequests.get(message.getId());
		if (request == null || request.getA() == null)
			return;

		// Does this message's signature match what we're expecting?
		byte[] signature = arbitraryDataMessage.getSignature();
		String signature58 = Base58.encode(signature);
		if (!request.getA().equals(signature58))
			return;

		byte[] data = arbitraryDataMessage.getData();

		// Check transaction exists and payload hash is correct
		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
			if (!(transactionData instanceof ArbitraryTransactionData))
				return;

			ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

			byte[] actualHash = Crypto.digest(data);

			// "data" from repository will always be hash of actual raw data
			if (!Arrays.equals(arbitraryTransactionData.getData(), actualHash))
				return;

			// Update requests map to reflect that we've received it
			Triple<String, Peer, Long> newEntry = new Triple<>(null, null, request.getC());
			arbitraryDataRequests.put(message.getId(), newEntry);

			// Save payload locally
			// TODO: storage policy
			arbitraryTransactionData.setDataType(DataType.RAW_DATA);
			arbitraryTransactionData.setData(data);
			repository.getArbitraryRepository().save(arbitraryTransactionData);
			repository.saveChanges();
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while finding arbitrary transaction data for peer %s", peer), e);
		}

		Peer requestingPeer = request.getB();
		if (requestingPeer != null) {
			// Forward to requesting peer;
			if (!requestingPeer.sendMessage(arbitraryDataMessage))
				requestingPeer.disconnect("failed to forward arbitrary data");
		}
	}

	private void onNetworkGetOnlineAccountsMessage(Peer peer, Message message) {
		GetOnlineAccountsMessage getOnlineAccountsMessage = (GetOnlineAccountsMessage) message;

		List<OnlineAccountData> excludeAccounts = getOnlineAccountsMessage.getOnlineAccounts();

		// Send online accounts info, excluding entries with matching timestamp & public key from excludeAccounts
		List<OnlineAccountData> accountsToSend;
		synchronized (this.onlineAccounts) {
			accountsToSend = new ArrayList<>(this.onlineAccounts);
		}

		Iterator<OnlineAccountData> iterator = accountsToSend.iterator();

		SEND_ITERATOR:
		while (iterator.hasNext()) {
			OnlineAccountData onlineAccountData = iterator.next();

			for (int i = 0; i < excludeAccounts.size(); ++i) {
				OnlineAccountData excludeAccountData = excludeAccounts.get(i);

				if (onlineAccountData.getTimestamp() == excludeAccountData.getTimestamp() && Arrays.equals(onlineAccountData.getPublicKey(), excludeAccountData.getPublicKey())) {
					iterator.remove();
					continue SEND_ITERATOR;
				}
			}
		}

		Message onlineAccountsMessage = new OnlineAccountsMessage(accountsToSend);
		peer.sendMessage(onlineAccountsMessage);

		LOGGER.trace(() -> String.format("Sent %d of our %d online accounts to %s", accountsToSend.size(), this.onlineAccounts.size(), peer));
	}

	private void onNetworkOnlineAccountsMessage(Peer peer, Message message) {
		OnlineAccountsMessage onlineAccountsMessage = (OnlineAccountsMessage) message;

		List<OnlineAccountData> peersOnlineAccounts = onlineAccountsMessage.getOnlineAccounts();
		LOGGER.trace(() -> String.format("Received %d online accounts from %s", peersOnlineAccounts.size(), peer));

		try (final Repository repository = RepositoryManager.getRepository()) {
			for (OnlineAccountData onlineAccountData : peersOnlineAccounts)
				this.verifyAndAddAccount(repository, onlineAccountData);
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while verifying online accounts from peer %s", peer), e);
		}
	}

	// Utilities

	private void verifyAndAddAccount(Repository repository, OnlineAccountData onlineAccountData) throws DataException {
		final Long now = NTP.getTime();
		if (now == null)
			return;

		PublicKeyAccount otherAccount = new PublicKeyAccount(repository, onlineAccountData.getPublicKey());

		// Check timestamp is 'recent' here
		if (Math.abs(onlineAccountData.getTimestamp() - now) > ONLINE_TIMESTAMP_MODULUS * 2) {
			LOGGER.trace(() -> String.format("Rejecting online account %s with out of range timestamp %d", otherAccount.getAddress(), onlineAccountData.getTimestamp()));
			return;
		}

		// Verify
		byte[] data = Longs.toByteArray(onlineAccountData.getTimestamp());
		if (!otherAccount.verify(onlineAccountData.getSignature(), data)) {
			LOGGER.trace(() -> String.format("Rejecting invalid online account %s", otherAccount.getAddress()));
			return;
		}

		// Qortal: check online account is actually reward-share
		RewardShareData rewardShareData = repository.getAccountRepository().getRewardShare(onlineAccountData.getPublicKey());
		if (rewardShareData == null) {
			// Reward-share doesn't even exist - probably not a good sign
			LOGGER.trace(() -> String.format("Rejecting unknown online reward-share public key %s", Base58.encode(onlineAccountData.getPublicKey())));
			return;
		}

		Account mintingAccount = new Account(repository, rewardShareData.getMinter());
		if (!mintingAccount.canMint()) {
			// Minting-account component of reward-share can no longer mint - disregard
			LOGGER.trace(() -> String.format("Rejecting online reward-share with non-minting account %s", mintingAccount.getAddress()));
			return;
		}

		synchronized (this.onlineAccounts) {
			OnlineAccountData existingAccountData = this.onlineAccounts.stream().filter(account -> Arrays.equals(account.getPublicKey(), onlineAccountData.getPublicKey())).findFirst().orElse(null);

			if (existingAccountData != null) {
				if (existingAccountData.getTimestamp() < onlineAccountData.getTimestamp()) {
					this.onlineAccounts.remove(existingAccountData);

					LOGGER.trace(() -> String.format("Updated online account %s with timestamp %d (was %d)", otherAccount.getAddress(), onlineAccountData.getTimestamp(), existingAccountData.getTimestamp()));
				} else {
					LOGGER.trace(() -> String.format("Not updating existing online account %s", otherAccount.getAddress()));

					return;
				}
			} else {
				LOGGER.trace(() -> String.format("Added online account %s with timestamp %d", otherAccount.getAddress(), onlineAccountData.getTimestamp()));
			}

			this.onlineAccounts.add(onlineAccountData);
		}
	}

	public void ensureTestingAccountsOnline(PrivateKeyAccount... onlineAccounts) {
		if (!BlockChain.getInstance().isTestChain()) {
			LOGGER.warn("Ignoring attempt to ensure test account is online for non-test chain!");
			return;
		}

		final Long now = NTP.getTime();
		if (now == null)
			return;

		final long onlineAccountsTimestamp = Controller.toOnlineAccountTimestamp(now);
		byte[] timestampBytes = Longs.toByteArray(onlineAccountsTimestamp);

		synchronized (this.onlineAccounts) {
			this.onlineAccounts.clear();

			for (PrivateKeyAccount onlineAccount : onlineAccounts) {
				// Check mintingAccount is actually reward-share?

				byte[] signature = onlineAccount.sign(timestampBytes);
				byte[] publicKey = onlineAccount.getPublicKey();

				OnlineAccountData ourOnlineAccountData = new OnlineAccountData(onlineAccountsTimestamp, signature, publicKey);
				this.onlineAccounts.add(ourOnlineAccountData);
			}
		}
	}

	private void performOnlineAccountsTasks() {
		final Long now = NTP.getTime();
		if (now == null)
			return;

		// Expire old entries
		final long cutoffThreshold = now - LAST_SEEN_EXPIRY_PERIOD;
		synchronized (this.onlineAccounts) {
			Iterator<OnlineAccountData> iterator = this.onlineAccounts.iterator();
			while (iterator.hasNext()) {
				OnlineAccountData onlineAccountData = iterator.next();

				if (onlineAccountData.getTimestamp() < cutoffThreshold) {
					iterator.remove();

					LOGGER.trace(() -> {
						PublicKeyAccount otherAccount = new PublicKeyAccount(null, onlineAccountData.getPublicKey());
						return String.format("Removed expired online account %s with timestamp %d", otherAccount.getAddress(), onlineAccountData.getTimestamp());
					});
				}
			}
		}

		// Request data from other peers?
		if ((this.onlineAccountsTasksTimestamp % ONLINE_ACCOUNTS_BROADCAST_INTERVAL) < ONLINE_ACCOUNTS_TASKS_INTERVAL) {
			Message message;
			synchronized (this.onlineAccounts) {
				message = new GetOnlineAccountsMessage(this.onlineAccounts);
			}
			Network.getInstance().broadcast(peer -> message);
		}

		// Refresh our online accounts signatures?
		sendOurOnlineAccountsInfo();
	}

	private void sendOurOnlineAccountsInfo() {
		final Long now = NTP.getTime();
		if (now == null)
			return;

		List<MintingAccountData> mintingAccounts;
		try (final Repository repository = RepositoryManager.getRepository()) {
			mintingAccounts = repository.getAccountRepository().getMintingAccounts();

			// We have no accounts, but don't reset timestamp
			if (mintingAccounts.isEmpty())
				return;

			// Only reward-share accounts allowed
			Iterator<MintingAccountData> iterator = mintingAccounts.iterator();
			while (iterator.hasNext()) {
				MintingAccountData mintingAccountData = iterator.next();

				RewardShareData rewardShareData = repository.getAccountRepository().getRewardShare(mintingAccountData.getPublicKey());
				if (rewardShareData == null) {
					// Reward-share doesn't even exist - probably not a good sign
					iterator.remove();
					continue;
				}

				Account mintingAccount = new Account(repository, rewardShareData.getMinter());
				if (!mintingAccount.canMint()) {
					// Minting-account component of reward-share can no longer mint - disregard
					iterator.remove();
					continue;
				}
			}
		} catch (DataException e) {
			LOGGER.warn(String.format("Repository issue trying to fetch minting accounts: %s", e.getMessage()));
			return;
		}

		// 'current' timestamp
		final long onlineAccountsTimestamp = Controller.toOnlineAccountTimestamp(now);
		boolean hasInfoChanged = false;

		byte[] timestampBytes = Longs.toByteArray(onlineAccountsTimestamp);
		List<OnlineAccountData> ourOnlineAccounts = new ArrayList<>();

		MINTING_ACCOUNTS:
		for (MintingAccountData mintingAccountData : mintingAccounts) {
			PrivateKeyAccount mintingAccount = new PrivateKeyAccount(null, mintingAccountData.getPrivateKey());

			byte[] signature = mintingAccount.sign(timestampBytes);
			byte[] publicKey = mintingAccount.getPublicKey();

			// Our account is online
			OnlineAccountData ourOnlineAccountData = new OnlineAccountData(onlineAccountsTimestamp, signature, publicKey);
			synchronized (this.onlineAccounts) {
				Iterator<OnlineAccountData> iterator = this.onlineAccounts.iterator();
				while (iterator.hasNext()) {
					OnlineAccountData existingOnlineAccountData = iterator.next();

					if (Arrays.equals(existingOnlineAccountData.getPublicKey(), ourOnlineAccountData.getPublicKey())) {
						// If our online account is already present, with same timestamp, then move on to next mintingAccount
						if (existingOnlineAccountData.getTimestamp() == onlineAccountsTimestamp)
							continue MINTING_ACCOUNTS;

						// If our online account is already present, but with older timestamp, then remove it
						iterator.remove();
						break;
					}
				}

				this.onlineAccounts.add(ourOnlineAccountData);
			}

			LOGGER.trace(() -> String.format("Added our online account %s with timestamp %d", mintingAccount.getAddress(), onlineAccountsTimestamp));
			ourOnlineAccounts.add(ourOnlineAccountData);
			hasInfoChanged = true;
		}

		if (!hasInfoChanged)
			return;

		Message message = new OnlineAccountsMessage(ourOnlineAccounts);
		Network.getInstance().broadcast(peer -> message);

		LOGGER.trace(()-> String.format("Broadcasted %d online account%s with timestamp %d", ourOnlineAccounts.size(), (ourOnlineAccounts.size() != 1 ? "s" : ""), onlineAccountsTimestamp));
	}

	public static long toOnlineAccountTimestamp(long timestamp) {
		return (timestamp / ONLINE_TIMESTAMP_MODULUS) * ONLINE_TIMESTAMP_MODULUS;
	}

	/** Returns list of online accounts with timestamp recent enough to be considered currently online. */
	public List<OnlineAccountData> getOnlineAccounts() {
		final long onlineTimestamp = Controller.toOnlineAccountTimestamp(NTP.getTime());

		synchronized (this.onlineAccounts) {
			return this.onlineAccounts.stream().filter(account -> account.getTimestamp() == onlineTimestamp).collect(Collectors.toList());
		}
	}

	/** Returns cached, unmodifiable list of latest block's online accounts. */
	public List<OnlineAccountData> getLatestBlocksOnlineAccounts() {
		synchronized (this.latestBlocksOnlineAccounts) {
			return this.latestBlocksOnlineAccounts.peekFirst();
		}
	}

	/** Caches list of latest block's online accounts. Typically called by Block.process() */
	public void pushLatestBlocksOnlineAccounts(List<OnlineAccountData> latestBlocksOnlineAccounts) {
		synchronized (this.latestBlocksOnlineAccounts) {
			if (this.latestBlocksOnlineAccounts.size() == MAX_BLOCKS_CACHED_ONLINE_ACCOUNTS)
				this.latestBlocksOnlineAccounts.pollLast();

			this.latestBlocksOnlineAccounts.addFirst(latestBlocksOnlineAccounts == null
					? Collections.emptyList()
					: Collections.unmodifiableList(latestBlocksOnlineAccounts));
		}
	}

	/** Reverts list of latest block's online accounts. Typically called by Block.orphan() */
	public void popLatestBlocksOnlineAccounts() {
		synchronized (this.latestBlocksOnlineAccounts) {
			this.latestBlocksOnlineAccounts.pollFirst();
		}
	}

	public byte[] fetchArbitraryData(byte[] signature) throws InterruptedException {
		// Build request
		Message getArbitraryDataMessage = new GetArbitraryDataMessage(signature);

		// Save our request into requests map
		String signature58 = Base58.encode(signature);
		Triple<String, Peer, Long> requestEntry = new Triple<>(signature58, null, NTP.getTime());

		// Assign random ID to this message
		int id;
		do {
			id = new Random().nextInt(Integer.MAX_VALUE - 1) + 1;

			// Put queue into map (keyed by message ID) so we can poll for a response
			// If putIfAbsent() doesn't return null, then this ID is already taken
		} while (arbitraryDataRequests.put(id, requestEntry) != null);
		getArbitraryDataMessage.setId(id);

		// Broadcast request
		Network.getInstance().broadcast(peer -> getArbitraryDataMessage);

		// Poll to see if data has arrived
		final long singleWait = 100;
		long totalWait = 0;
		while (totalWait < ARBITRARY_REQUEST_TIMEOUT) {
			Thread.sleep(singleWait);

			requestEntry = arbitraryDataRequests.get(id);
			if (requestEntry == null)
				return null;

			if (requestEntry.getA() == null)
				break;

			totalWait += singleWait;
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getArbitraryRepository().fetchData(signature);
		} catch (DataException e) {
			LOGGER.error(String.format("Repository issue while fetching arbitrary transaction data"), e);
			return null;
		}
	}

	/** Returns a list of peers that are not misbehaving, and have a recent block. */
	public List<Peer> getRecentBehavingPeers() {
		final Long minLatestBlockTimestamp = getMinimumLatestBlockTimestamp();
		if (minLatestBlockTimestamp == null)
			return null;

		List<Peer> peers = Network.getInstance().getHandshakedPeers();

		// Filter out unsuitable peers
		Iterator<Peer> iterator = peers.iterator();
		while (iterator.hasNext()) {
			final Peer peer = iterator.next();

			final PeerData peerData = peer.getPeerData();
			if (peerData == null) {
				iterator.remove();
				continue;
			}

			// Disregard peers that have "misbehaved" recently
			if (hasMisbehaved.test(peer)) {
				iterator.remove();
				continue;
			}

			final PeerChainTipData peerChainTipData = peer.getChainTipData();
			if (peerChainTipData == null) {
				iterator.remove();
				continue;
			}

			// Disregard peers that don't have a recent block
			if (peerChainTipData.getLastBlockTimestamp() == null || peerChainTipData.getLastBlockTimestamp() < minLatestBlockTimestamp) {
				iterator.remove();
				continue;
			}
		}

		return peers;
	}

	/** Returns whether we think our node has up-to-date blockchain based on our info about other peers. */
	public boolean isUpToDate() {
		// Do we even have a vaguely recent block?
		final Long minLatestBlockTimestamp = getMinimumLatestBlockTimestamp();
		if (minLatestBlockTimestamp == null)
			return false;

		final BlockData latestBlockData = getChainTip();
		if (latestBlockData == null || latestBlockData.getTimestamp() < minLatestBlockTimestamp)
			return false;

		List<Peer> peers = Network.getInstance().getHandshakedPeers();
		if (peers == null)
			return false;

		// Disregard peers that have "misbehaved" recently
		peers.removeIf(hasMisbehaved);

		// Disregard peers that don't have a recent block
		peers.removeIf(hasNoRecentBlock);

		// Check we have enough peers to potentially synchronize/mint
		if (peers.size() < Settings.getInstance().getMinBlockchainPeers())
			return false;

		// If we don't have any peers left then can't synchronize, therefore consider ourself not up to date
		return !peers.isEmpty();
	}

	/** Returns minimum block timestamp for block to be considered 'recent', or <tt>null</tt> if NTP not synced. */
	public static Long getMinimumLatestBlockTimestamp() {
		Long now = NTP.getTime();
		if (now == null)
			return null;

		int height = getInstance().getChainHeight();
		if (height == 0)
			return null;

		long offset = 0;
		for (int ai = 0; height >= 1 && ai < MAX_BLOCKCHAIN_TIP_AGE; ++ai, --height) {
			BlockTimingByHeight blockTiming = BlockChain.getInstance().getBlockTimingByHeight(height);
			offset += blockTiming.target + blockTiming.deviation;
		}

		return now - offset;
	}

	public StatsSnapshot getStatsSnapshot() {
		return this.stats;
	}

}
