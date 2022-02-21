package org.qortal.block;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.persistence.exceptions.XMLMarshalException;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.qortal.controller.Controller;
import org.qortal.data.block.BlockData;
import org.qortal.network.Network;
import org.qortal.repository.*;
import org.qortal.settings.Settings;
import org.qortal.utils.Base58;
import org.qortal.utils.StringLongMapXmlAdapter;

/**
 * Class representing the blockchain as a whole.
 *
 */
// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class BlockChain {

	private static final Logger LOGGER = LogManager.getLogger(BlockChain.class);

	private static BlockChain instance = null;

	// Properties

	private boolean isTestChain = false;

	/** Transaction expiry period, starting from transaction's timestamp, in milliseconds. */
	private long transactionExpiryPeriod;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long unitFee;

	private int maxBytesPerUnitFee;

	/** Maximum acceptable timestamp disagreement offset in milliseconds. */
	private long blockTimestampMargin;

	/** Maximum block size, in bytes. */
	private int maxBlockSize;

	/** Whether transactions with txGroupId of NO_GROUP are allowed */
	private boolean requireGroupForApproval;

	private GenesisBlock.GenesisInfo genesisInfo;

	public enum FeatureTrigger {
		atFindNextTransactionFix,
		newBlockSigHeight,
		shareBinFix,
		calcChainWeightTimestamp,
		transactionV5Timestamp;
	}

	// Custom transaction fees
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long nameRegistrationUnitFee;
	private long nameRegistrationUnitFeeTimestamp;

	/** Map of which blockchain features are enabled when (height/timestamp) */
	@XmlJavaTypeAdapter(StringLongMapXmlAdapter.class)
	private Map<String, Long> featureTriggers;

	/** Whether to use legacy, broken RIPEMD160 implementation when converting public keys to addresses. */
	private boolean useBrokenMD160ForAddresses = false;

	/** Whether only one registered name is allowed per account. */
	private boolean oneNamePerAccount = false;

	/** Block rewards by block height */
	public static class RewardByHeight {
		public int height;
		@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
		public long reward;
	}
	private List<RewardByHeight> rewardsByHeight;

	/** Share of block reward/fees by account level */
	public static class AccountLevelShareBin {
		public List<Integer> levels;
		@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
		public long share;
	}
	private List<AccountLevelShareBin> sharesByLevel;
	/** Generated lookup of share-bin by account level */
	private AccountLevelShareBin[] shareBinsByLevel;

	/** Share of block reward/fees to legacy QORA coin holders */
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private Long qoraHoldersShare;

	/** How many legacy QORA per 1 QORT of block reward. */
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private Long qoraPerQortReward;

	/**
	 * Number of minted blocks required to reach next level from previous.
	 * <p>
	 * Use account's current level as index.<br>
	 * If account's level isn't valid as an index, then account's level is at maximum.
	 * <p>
	 * Example: if <tt>blocksNeededByLevel[3]</tt> is 200,<br>
	 * then level 3 accounts need to mint 200 blocks to reach level 4.
	 */
	private List<Integer> blocksNeededByLevel;

	/**
	 * Cumulative number of minted blocks required to reach next level from scratch.
	 * <p>
	 * Use target level as index. <tt>cumulativeBlocksByLevel[0]</tt> should be 0.
	 * <p>
	 * Example; if <tt>cumulativeBlocksByLevel[2</tt>] is 1800,<br>
	 * the a <b>new</b> account will need to mint 1800 blocks to reach level 2.
	 * <p>
	 * Generated just after blockchain config is parsed and validated.
	 * <p>
	 * Should NOT be present in blockchain config file!
	 */
	private List<Integer> cumulativeBlocksByLevel;

	/** Block times by block height */
	public static class BlockTimingByHeight {
		public int height;
		public long target; // ms
		public long deviation; // ms
		public double power;
	}
	private List<BlockTimingByHeight> blockTimingsByHeight;

	private int minAccountLevelToMint;
	private int minAccountLevelForBlockSubmissions;
	private int minAccountLevelToRewardShare;
	private int maxRewardSharesPerMintingAccount;
	private int founderEffectiveMintingLevel;

	/** Minimum time to retain online account signatures (ms) for block validity checks. */
	private long onlineAccountSignaturesMinLifetime;
	/** Maximum time to retain online account signatures (ms) for block validity checks, to allow for clock variance. */
	private long onlineAccountSignaturesMaxLifetime;

	/** Settings relating to CIYAM AT feature. */
	public static class CiyamAtSettings {
		/** Fee per step/op-code executed. */
		@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
		public long feePerStep;
		/** Maximum number of steps per execution round, before AT is forced to sleep until next block. */
		public int maxStepsPerRound;
		/** How many steps for calling a function. */
		public int stepsPerFunctionCall;
		/** Roughly how many minutes per block. */
		public int minutesPerBlock;
	}
	private CiyamAtSettings ciyamAtSettings;

	// Constructors, etc.

	private BlockChain() {
	}

	public static BlockChain getInstance() {
		if (instance == null)
			// This will call BlockChain.fromJSON in turn
			Settings.getInstance(); // synchronized

		return instance;
	}

	/** Use blockchain config read from <tt>path</tt> + <tt>filename</tt>, or use resources-based default if <tt>filename</tt> is <tt>null</tt>. */
	public static void fileInstance(String path, String filename) {
		JAXBContext jc;
		Unmarshaller unmarshaller;

		try {
			// Create JAXB context aware of Settings
			jc = JAXBContextFactory.createContext(new Class[] {
				BlockChain.class, GenesisBlock.GenesisInfo.class
			}, null);

			// Create unmarshaller
			unmarshaller = jc.createUnmarshaller();

			// Set the unmarshaller media type to JSON
			unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");

			// Tell unmarshaller that there's no JSON root element in the JSON input
			unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);

		} catch (JAXBException e) {
			String message = "Failed to setup unmarshaller to process blockchain config file";
			LOGGER.error(message, e);
			throw new RuntimeException(message, e);
		}

		BlockChain blockchain = null;
		StreamSource jsonSource;

		if (filename != null) {
			LOGGER.info(String.format("Using blockchain config file: %s%s", path, filename));

			File jsonFile = new File(path + filename);

			if (!jsonFile.exists()) {
				String message = "Blockchain config file not found: " + path + filename;
				LOGGER.error(message);
				throw new RuntimeException(message, new FileNotFoundException(message));
			}

			jsonSource = new StreamSource(jsonFile);
		} else {
			LOGGER.info("Using default, resources-based blockchain config");

			ClassLoader classLoader = BlockChain.class.getClassLoader();
			InputStream in = classLoader.getResourceAsStream("blockchain.json");
			jsonSource = new StreamSource(in);
		}

		try  {
			// Attempt to unmarshal JSON stream to BlockChain config
			blockchain = unmarshaller.unmarshal(jsonSource, BlockChain.class).getValue();
		} catch (UnmarshalException e) {
			Throwable linkedException = e.getLinkedException();
			if (linkedException instanceof XMLMarshalException) {
				String message = ((XMLMarshalException) linkedException).getInternalException().getLocalizedMessage();

				if (message == null && linkedException.getCause() != null && linkedException.getCause().getCause() != null )
					message = linkedException.getCause().getCause().getLocalizedMessage();

				if (message == null && linkedException.getCause() != null)
					message = linkedException.getCause().getLocalizedMessage();

				if (message == null)
					message = linkedException.getLocalizedMessage();

				if (message == null)
					message = e.getLocalizedMessage();

				LOGGER.error(message);
				throw new RuntimeException(message, e);
			}

			String message = "Failed to parse blockchain config file";
			LOGGER.error(message, e);
			throw new RuntimeException(message, e);
		} catch (JAXBException e) {
			String message = "Unexpected JAXB issue while processing blockchain config file";
			LOGGER.error(message, e);
			throw new RuntimeException(message, e);
		}

		// Validate config
		blockchain.validateConfig();

		// Minor fix-up
		blockchain.fixUp();

		// Successfully read config now in effect
		instance = blockchain;

		// Pass genesis info to GenesisBlock
		GenesisBlock.newInstance(blockchain.genesisInfo);
	}

	// Getters / setters

	public boolean isTestChain() {
		return this.isTestChain;
	}

	public long getUnitFee() {
		return this.unitFee;
	}

	public int getMaxBytesPerUnitFee() {
		return this.maxBytesPerUnitFee;
	}

	public long getTransactionExpiryPeriod() {
		return this.transactionExpiryPeriod;
	}

	public long getBlockTimestampMargin() {
		return this.blockTimestampMargin;
	}

	public int getMaxBlockSize() {
		return this.maxBlockSize;
	}

	// Custom transaction fees
	public long getNameRegistrationUnitFee() {
		return this.nameRegistrationUnitFee;
	}

	public long getNameRegistrationUnitFeeTimestamp() {
		// FUTURE: we could use a separate structure to indicate fee adjustments for different transaction types
		return this.nameRegistrationUnitFeeTimestamp;
	}

	/** Returns true if approval-needing transaction types require a txGroupId other than NO_GROUP. */
	public boolean getRequireGroupForApproval() {
		return this.requireGroupForApproval;
	}

	public boolean getUseBrokenMD160ForAddresses() {
		return this.useBrokenMD160ForAddresses;
	}

	public boolean oneNamePerAccount() {
		return this.oneNamePerAccount;
	}

	public List<RewardByHeight> getBlockRewardsByHeight() {
		return this.rewardsByHeight;
	}

	public List<AccountLevelShareBin> getAccountLevelShareBins() {
		return this.sharesByLevel;
	}

	public AccountLevelShareBin[] getShareBinsByAccountLevel() {
		return this.shareBinsByLevel;
	}

	public List<Integer> getBlocksNeededByLevel() {
		return this.blocksNeededByLevel;
	}

	public List<Integer> getCumulativeBlocksByLevel() {
		return this.cumulativeBlocksByLevel;
	}

	public long getQoraHoldersShare() {
		return this.qoraHoldersShare;
	}

	public long getQoraPerQortReward() {
		return this.qoraPerQortReward;
	}

	public int getMinAccountLevelToMint() {
		return this.minAccountLevelToMint;
	}

	public int getMinAccountLevelForBlockSubmissions() {
		return this.minAccountLevelForBlockSubmissions;
	}

	public int getMinAccountLevelToRewardShare() {
		return this.minAccountLevelToRewardShare;
	}

	public int getMaxRewardSharesPerMintingAccount() {
		return this.maxRewardSharesPerMintingAccount;
	}

	public int getFounderEffectiveMintingLevel() {
		return this.founderEffectiveMintingLevel;
	}

	public long getOnlineAccountSignaturesMinLifetime() {
		return this.onlineAccountSignaturesMinLifetime;
	}

	public long getOnlineAccountSignaturesMaxLifetime() {
		return this.onlineAccountSignaturesMaxLifetime;
	}

	public CiyamAtSettings getCiyamAtSettings() {
		return this.ciyamAtSettings;
	}

	// Convenience methods for specific blockchain feature triggers

	public int getAtFindNextTransactionFixHeight() {
		return this.featureTriggers.get(FeatureTrigger.atFindNextTransactionFix.name()).intValue();
	}

	public int getNewBlockSigHeight() {
		return this.featureTriggers.get(FeatureTrigger.newBlockSigHeight.name()).intValue();
	}

	public int getShareBinFixHeight() {
		return this.featureTriggers.get(FeatureTrigger.shareBinFix.name()).intValue();
	}

	public long getCalcChainWeightTimestamp() {
		return this.featureTriggers.get(FeatureTrigger.calcChainWeightTimestamp.name()).longValue();
	}

	public long getTransactionV5Timestamp() {
		return this.featureTriggers.get(FeatureTrigger.transactionV5Timestamp.name()).longValue();
	}

	// More complex getters for aspects that change by height or timestamp

	public long getRewardAtHeight(int ourHeight) {
		// Scan through for reward at our height
		for (int i = rewardsByHeight.size() - 1; i >= 0; --i)
			if (rewardsByHeight.get(i).height <= ourHeight)
				return rewardsByHeight.get(i).reward;

		return 0;
	}

	public BlockTimingByHeight getBlockTimingByHeight(int ourHeight) {
		for (int i = blockTimingsByHeight.size() - 1; i >= 0; --i)
			if (blockTimingsByHeight.get(i).height <= ourHeight)
				return blockTimingsByHeight.get(i);

		throw new IllegalStateException(String.format("No block timing info available for height %d", ourHeight));
	}

	/** Validate blockchain config read from JSON */
	private void validateConfig() {
		if (this.genesisInfo == null)
			Settings.throwValidationError("No \"genesisInfo\" entry found in blockchain config");

		if (this.rewardsByHeight == null)
			Settings.throwValidationError("No \"rewardsByHeight\" entry found in blockchain config");

		if (this.sharesByLevel == null)
			Settings.throwValidationError("No \"sharesByLevel\" entry found in blockchain config");

		if (this.qoraHoldersShare == null)
			Settings.throwValidationError("No \"qoraHoldersShare\" entry found in blockchain config");

		if (this.qoraPerQortReward == null)
			Settings.throwValidationError("No \"qoraPerQortReward\" entry found in blockchain config");

		if (this.blocksNeededByLevel == null)
			Settings.throwValidationError("No \"blocksNeededByLevel\" entry found in blockchain config");

		if (this.blockTimingsByHeight == null)
			Settings.throwValidationError("No \"blockTimingsByHeight\" entry found in blockchain config");

		if (this.blockTimestampMargin <= 0)
			Settings.throwValidationError("Invalid \"blockTimestampMargin\" in blockchain config");

		if (this.transactionExpiryPeriod <= 0)
			Settings.throwValidationError("Invalid \"transactionExpiryPeriod\" in blockchain config");

		if (this.maxBlockSize <= 0)
			Settings.throwValidationError("Invalid \"maxBlockSize\" in blockchain config");

		if (this.minAccountLevelToRewardShare <= 0)
			Settings.throwValidationError("Invalid/missing \"minAccountLevelToRewardShare\" in blockchain config");

		if (this.founderEffectiveMintingLevel <= 0)
			Settings.throwValidationError("Invalid/missing \"founderEffectiveMintingLevel\" in blockchain config");

		if (this.ciyamAtSettings == null)
			Settings.throwValidationError("No \"ciyamAtSettings\" entry found in blockchain config");

		if (this.featureTriggers == null)
			Settings.throwValidationError("No \"featureTriggers\" entry found in blockchain config");

		// Check all featureTriggers are present
		for (FeatureTrigger featureTrigger : FeatureTrigger.values())
			if (!this.featureTriggers.containsKey(featureTrigger.name()))
				Settings.throwValidationError(String.format("Missing feature trigger \"%s\" in blockchain config", featureTrigger.name()));

		// Check block reward share bounds
		long totalShare = this.qoraHoldersShare;
		// Add share percents for account-level-based rewards
		for (AccountLevelShareBin accountLevelShareBin : this.sharesByLevel)
			totalShare += accountLevelShareBin.share;

		if (totalShare < 0 || totalShare > 1_00000000L)
			Settings.throwValidationError("Total non-founder share out of bounds (0<x<1e8)");
	}

	/** Minor normalization, cached value generation, etc. */
	private void fixUp() {
		// Calculate cumulative blocks required for each level
		int cumulativeBlocks = 0;
		this.cumulativeBlocksByLevel = new ArrayList<>(this.blocksNeededByLevel.size() + 1);
		for (int level = 0; level <= this.blocksNeededByLevel.size(); ++level) {
			this.cumulativeBlocksByLevel.add(cumulativeBlocks);

			if (level < this.blocksNeededByLevel.size())
				cumulativeBlocks += this.blocksNeededByLevel.get(level);
		}

		// Generate lookup-array for account-level share bins
		AccountLevelShareBin lastAccountLevelShareBin = this.sharesByLevel.get(this.sharesByLevel.size() - 1);
		final int lastLevel = lastAccountLevelShareBin.levels.get(lastAccountLevelShareBin.levels.size() - 1);
		this.shareBinsByLevel = new AccountLevelShareBin[lastLevel];

		for (AccountLevelShareBin accountLevelShareBin : this.sharesByLevel)
			for (int level : accountLevelShareBin.levels)
				// level 1 stored at index 0, level 2 stored at index 1, etc.
				// level 0 not allowed
				this.shareBinsByLevel[level - 1] = accountLevelShareBin;

		// Convert collections to unmodifiable form
		this.rewardsByHeight = Collections.unmodifiableList(this.rewardsByHeight);
		this.sharesByLevel = Collections.unmodifiableList(this.sharesByLevel);
		this.blocksNeededByLevel = Collections.unmodifiableList(this.blocksNeededByLevel);
		this.cumulativeBlocksByLevel = Collections.unmodifiableList(this.cumulativeBlocksByLevel);
		this.blockTimingsByHeight = Collections.unmodifiableList(this.blockTimingsByHeight);
	}

	/**
	 * Some sort of start-up/initialization/checking method.
	 * 
	 * @throws SQLException
	 */
	public static void validate() throws DataException {

		boolean isTopOnly = Settings.getInstance().isTopOnly();
		boolean archiveEnabled = Settings.getInstance().isArchiveEnabled();
		boolean canBootstrap = Settings.getInstance().getBootstrap();
		boolean needsArchiveRebuild = false;
		BlockData chainTip;

		try (final Repository repository = RepositoryManager.getRepository()) {
			chainTip = repository.getBlockRepository().getLastBlock();

			// Ensure archive is (at least partially) intact, and force a bootstrap if it isn't
			if (!isTopOnly && archiveEnabled && canBootstrap) {
				needsArchiveRebuild = (repository.getBlockArchiveRepository().fromHeight(2) == null);
				if (needsArchiveRebuild) {
					LOGGER.info("Couldn't retrieve block 2 from archive. Bootstrapping...");

					// If there are minting accounts, make sure to back them up
					// Don't backup if there are no minting accounts, as this can cause problems
					if (!repository.getAccountRepository().getMintingAccounts().isEmpty()) {
						Controller.getInstance().exportRepositoryData();
					}
				}
			}
		}

		boolean hasBlocks = (chainTip != null && chainTip.getHeight() > 1);

		if (isTopOnly && hasBlocks) {
			// Top-only mode is enabled and we have blocks, so it's possible that the genesis block has been pruned
			// It's best not to validate it, and there's no real need to
		} else {
			// Check first block is Genesis Block
			if (!isGenesisBlockValid() || needsArchiveRebuild) {
				try {
					rebuildBlockchain();

				} catch (InterruptedException e) {
					throw new DataException(String.format("Interrupted when trying to rebuild blockchain: %s", e.getMessage()));
				}
			}
		}

		// We need to create a new connection, as the previous repository and its connections may be been
		// closed by rebuildBlockchain() if a bootstrap was applied
		try (final Repository repository = RepositoryManager.getRepository()) {
			repository.checkConsistency();

			// Set the number of blocks to validate based on the pruned state of the chain
			// If pruned, subtract an extra 10 to allow room for error
			int blocksToValidate = (isTopOnly || archiveEnabled) ? Settings.getInstance().getPruneBlockLimit() - 10 : 1440;

			int startHeight = Math.max(repository.getBlockRepository().getBlockchainHeight() - blocksToValidate, 1);
			BlockData detachedBlockData = repository.getBlockRepository().getDetachedBlockSignature(startHeight);

			if (detachedBlockData != null) {
				LOGGER.error(String.format("Block %d's reference does not match any block's signature",
						detachedBlockData.getHeight()));
				LOGGER.error(String.format("Your chain may be invalid and you should consider bootstrapping" +
						" or re-syncing from genesis."));
			}
		}
	}

	/**
	 * More thorough blockchain validation method. Useful for validating bootstraps.
	 * A DataException is thrown if anything is invalid.
	 *
	 * @throws DataException
	 */
	public static void validateAllBlocks() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData chainTip = repository.getBlockRepository().getLastBlock();
			final int chainTipHeight = chainTip.getHeight();
			final int oldestBlock = 1; // TODO: increase if in pruning mode
			byte[] lastReference = null;

			for (int height = chainTipHeight; height > oldestBlock; height--) {
				BlockData blockData = repository.getBlockRepository().fromHeight(height);
				if (blockData == null) {
					blockData = repository.getBlockArchiveRepository().fromHeight(height);
				}

				if (blockData == null) {
					String error = String.format("Missing block at height %d", height);
					LOGGER.error(error);
					throw new DataException(error);
				}

				if (height != chainTipHeight) {
					// Check reference
					if (!Arrays.equals(blockData.getSignature(), lastReference)) {
						String error = String.format("Invalid reference for block at height %d: %s (should be %s)",
								height, Base58.encode(blockData.getReference()), Base58.encode(lastReference));
						LOGGER.error(error);
						throw new DataException(error);
					}
				}

				lastReference = blockData.getReference();
			}
		}
	}

	private static boolean isGenesisBlockValid() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockRepository blockRepository = repository.getBlockRepository();

			int blockchainHeight = blockRepository.getBlockchainHeight();
			if (blockchainHeight < 1)
				return false;

			BlockData blockData = blockRepository.fromHeight(1);
			if (blockData == null)
				return false;

			return GenesisBlock.isGenesisBlock(blockData);
		} catch (DataException e) {
			return false;
		}
	}

	private static void rebuildBlockchain() throws DataException, InterruptedException {
		boolean shouldBootstrap = Settings.getInstance().getBootstrap();
		if (shouldBootstrap) {
			// Settings indicate that we should apply a bootstrap rather than rebuilding and syncing from genesis
			Bootstrap bootstrap = new Bootstrap();
			bootstrap.startImport();
			return;
		}

		// (Re)build repository
		if (!RepositoryManager.wasPristineAtOpen())
			RepositoryManager.rebuild();

		try (final Repository repository = RepositoryManager.getRepository()) {
			GenesisBlock genesisBlock = GenesisBlock.getInstance(repository);

			// Add Genesis Block to blockchain
			genesisBlock.process();

			repository.saveChanges();

			// Give Network a chance to install initial seed peers
			Network.installInitialPeers(repository);
		}
	}

	public static boolean orphan(int targetHeight) throws DataException {
		ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
		if (!blockchainLock.tryLock())
			return false;

		try {
			try (final Repository repository = RepositoryManager.getRepository()) {
				int height = repository.getBlockRepository().getBlockchainHeight();
				BlockData orphanBlockData = repository.getBlockRepository().fromHeight(height);

				while (height > targetHeight) {
					LOGGER.info(String.format("Forcably orphaning block %d", height));

					Block block = new Block(repository, orphanBlockData);
					block.orphan();

					repository.saveChanges();

					--height;
					orphanBlockData = repository.getBlockRepository().fromHeight(height);

					repository.discardChanges(); // clear transaction status to prevent deadlocks
					Controller.getInstance().onOrphanedBlock(orphanBlockData);
				}

				return true;
			}
		} finally {
			blockchainLock.unlock();
		}
	}

}
