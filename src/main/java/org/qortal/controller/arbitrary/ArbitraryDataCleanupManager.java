package org.qortal.controller.arbitrary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.api.resource.TransactionsResource.ConfirmationStatus;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.utils.ArbitraryTransactionUtils;
import org.qortal.utils.Base58;
import org.qortal.utils.FilesystemUtils;
import org.qortal.utils.NTP;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;

import static org.qortal.controller.arbitrary.ArbitraryDataStorageManager.DELETION_THRESHOLD;

public class ArbitraryDataCleanupManager extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataCleanupManager.class);
	private static final List<TransactionType> ARBITRARY_TX_TYPE = Arrays.asList(TransactionType.ARBITRARY);

	private static ArbitraryDataCleanupManager instance;

	private volatile boolean isStopping = false;

	/**
	 * The amount of time that must pass before a file is treated as stale / not recent.
	 * We can safely delete files created/accessed longer ago that this, if we have a means of
	 * rebuilding them. The main purpose of this is to avoid deleting files that are currently
	 * being used by other parts of the system.
	 */
	private static final long STALE_FILE_TIMEOUT = 60*60*1000L; // 1 hour

	/**
	 * The number of chunks to delete in a batch when over the capacity limit.
	 * Storage limits are re-checked after each batch, and there could be a significant
	 * delay between the processing of each batch as it only occurs after a complete
	 * cleanup cycle (to allow unwanted chunks to be deleted first).
	 */
	private static final int CHUNK_DELETION_BATCH_SIZE = 10;


	/*
	TODO:
	- Delete files from the _misc folder once they reach a certain age
	 */


	private ArbitraryDataCleanupManager() {
	}

	public static ArbitraryDataCleanupManager getInstance() {
		if (instance == null)
			instance = new ArbitraryDataCleanupManager();

		return instance;
	}

	@Override
	public void run() {
		Thread.currentThread().setName("Arbitrary Data Cleanup Manager");

		// Paginate queries when fetching arbitrary transactions
		final int limit = 100;
		int offset = 0;

		try {
			while (!isStopping) {
				Thread.sleep(30000);

				// Don't run if QDN is disabled
				if (!Settings.getInstance().isQdnEnabled()) {
					Thread.sleep(60 * 60 * 1000L);
					continue;
				}

				Long now = NTP.getTime();
				if (now == null) {
					// Don't attempt to make decisions if we haven't synced our time yet
					continue;
				}

				ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();

				// Wait until storage capacity has been calculated
				if (!storageManager.isStorageCapacityCalculated()) {
					continue;
				}

				// Periodically delete any unnecessary files from the temp directory
				if (offset == 0 || offset % (limit * 10) == 0) {
					this.cleanupTempDirectory(now);
				}

				// Any arbitrary transactions we want to fetch data for?
				try (final Repository repository = RepositoryManager.getRepository()) {
					List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, null, null, ARBITRARY_TX_TYPE, null, null, null, ConfirmationStatus.BOTH, limit, offset, true);
					// LOGGER.info("Found {} arbitrary transactions at offset: {}, limit: {}", signatures.size(), offset, limit);
					if (isStopping) {
						return;
					}

					if (signatures == null || signatures.isEmpty()) {
						offset = 0;
						continue;
					}
					offset += limit;
					now = NTP.getTime();

					// Loop through the signatures in this batch
					for (int i=0; i<signatures.size(); i++) {
						if (isStopping) {
							return;
						}

						byte[] signature = signatures.get(i);
						if (signature == null) {
							continue;
						}

						// Don't interfere with the filesystem whilst a build is in progress
						if (ArbitraryDataBuildManager.getInstance().getBuildInProgress()) {
							Thread.sleep(5000);
						}

						// Fetch the transaction data
						ArbitraryTransactionData arbitraryTransactionData = ArbitraryTransactionUtils.fetchTransactionData(repository, signature);
						if (arbitraryTransactionData == null) {
							continue;
						}

						// Raw data doesn't have any associated files to clean up
						if (arbitraryTransactionData.getDataType() == ArbitraryTransactionData.DataType.RAW_DATA) {
							continue;
						}

						// Check if we have the complete file
						boolean completeFileExists = ArbitraryTransactionUtils.completeFileExists(arbitraryTransactionData);

						// Check if we have any of the chunks
						boolean anyChunksExist = ArbitraryTransactionUtils.anyChunksExist(arbitraryTransactionData);
						boolean transactionHasChunks = (arbitraryTransactionData.getMetadataHash() != null);

						if (!completeFileExists && !anyChunksExist) {
							// We don't have any files at all for this transaction - nothing to do
							continue;
						}

						// We have at least 1 chunk or file for this transaction, so we might need to delete them...


						// Check to see if we should be hosting data for this transaction at all
						if (!storageManager.canStoreData(arbitraryTransactionData)) {
							LOGGER.info("Deleting transaction {} because we can't host its data",
									Base58.encode(arbitraryTransactionData.getSignature()));
							ArbitraryTransactionUtils.deleteCompleteFileAndChunks(arbitraryTransactionData);
							continue;
						}

						// Check to see if we have had a more recent PUT
						boolean hasMoreRecentPutTransaction = ArbitraryTransactionUtils.hasMoreRecentPutTransaction(repository, arbitraryTransactionData);
						if (hasMoreRecentPutTransaction) {
							// There is a more recent PUT transaction than the one we are currently processing.
							// When a PUT is issued, it replaces any layers that would have been there before.
							// Therefore any data relating to this older transaction is no longer needed.
							LOGGER.info(String.format("Newer PUT found for %s %s since transaction %s. " +
											"Deleting all files associated with the earlier transaction.", arbitraryTransactionData.getService(),
									arbitraryTransactionData.getName(), Base58.encode(signature)));

							ArbitraryTransactionUtils.deleteCompleteFileAndChunks(arbitraryTransactionData);

							// We should also remove peers for this transaction from the lookup table to save space
							this.removePeersHostingTransactionData(repository, arbitraryTransactionData);
							continue;
						}

						if (completeFileExists && !transactionHasChunks) {
							// This file doesn't have any chunks because it is too small.
							// We must not delete anything.
							continue;
						}

						// Check if we have all of the chunks
						boolean allChunksExist = ArbitraryTransactionUtils.allChunksExist(arbitraryTransactionData);

						if (completeFileExists && allChunksExist) {
							// We have the complete file and all the chunks, so we can delete
							// the complete file if it has reached a certain age.
							LOGGER.debug(String.format("Transaction %s has complete file and all chunks",
									Base58.encode(arbitraryTransactionData.getSignature())));

							ArbitraryTransactionUtils.deleteCompleteFile(arbitraryTransactionData, now, STALE_FILE_TIMEOUT);
							continue;
						}

						if (completeFileExists && !allChunksExist) {
							// We have the complete file but not the chunks, so let's convert it
							LOGGER.info(String.format("Transaction %s has complete file but no chunks",
									Base58.encode(arbitraryTransactionData.getSignature())));

							ArbitraryTransactionUtils.convertFileToChunks(arbitraryTransactionData, now, STALE_FILE_TIMEOUT);
							continue;
						}
					}

				} catch (DataException e) {
					LOGGER.error("Repository issue when fetching arbitrary transaction data", e);
				}

				try (final Repository repository = RepositoryManager.getRepository()) {

					// Check if there are any hosted files that don't have matching transactions
					this.checkForExpiredTransactions(repository);

					// Delete additional data at random if we're over our storage limit
					// Use the DELETION_THRESHOLD so that we only start deleting once the hard limit is reached
					// This also allows some headroom between the regular threshold (90%) and the hard
					// limit, to avoid data getting into a fetch/delete loop.
					if (!storageManager.isStorageSpaceAvailable(DELETION_THRESHOLD)) {

						// Rate limit, to avoid repeated calls to calculateDirectorySize()
						Thread.sleep(60000);
						// Now delete some data at random
						this.storageLimitReached(repository);
					}

					// Delete random data associated with name if we're over our storage limit for this name
					// Use the DELETION_THRESHOLD, for the same reasons as above
					for (String followedName : storageManager.followedNames()) {
						if (isStopping) {
							return;
						}
						if (!storageManager.isStorageSpaceAvailableForName(repository, followedName, DELETION_THRESHOLD)) {
							this.storageLimitReachedForName(repository, followedName);
						}
					}

				} catch (DataException e) {
					LOGGER.error("Repository issue when cleaning up arbitrary transaction data", e);
				}
			}
		} catch (InterruptedException e) {
			// Fall-through to exit thread...
		}
	}

	public List<Path> findPathsWithNoAssociatedTransaction(Repository repository) {
		List<Path> pathList = new ArrayList<>();

		// Find all hosted paths
		List<Path> allPaths = ArbitraryDataStorageManager.getInstance().findAllHostedPaths();

		// Loop through each path and find those without matching signatures
		for (Path path : allPaths) {
			if (isStopping) {
				break;
			}
			try {
				String[] contents = path.toFile().list();
				if (contents == null || contents.length == 0) {
					// Ignore empty directories
					continue;
				}

				String signature58 = path.getFileName().toString();
				byte[] signature = Base58.decode(signature58);
				TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
				if (transactionData == null) {
					// No transaction data, and no DataException, so we can assume that this data relates to an expired transaction
					pathList.add(path);
				}

			} catch (DataException e) {
				continue;
			}
		}

		return pathList;
	}

	private void checkForExpiredTransactions(Repository repository) {
		List<Path> expiredPaths = this.findPathsWithNoAssociatedTransaction(repository);
		for (Path expiredPath : expiredPaths) {
			if (isStopping) {
				return;
			}
			LOGGER.info("Found path with no associated transaction: {}", expiredPath.toString());
			this.safeDeleteDirectory(expiredPath.toFile(), "no matching transaction");
		}
	}

	private void storageLimitReached(Repository repository) throws InterruptedException {
		// We think that the storage limit has been reached

		// Now calculate the used/total storage again, as a safety precaution
		Long now = NTP.getTime();
		ArbitraryDataStorageManager.getInstance().calculateDirectorySize(now);
		if (ArbitraryDataStorageManager.getInstance().isStorageSpaceAvailable(DELETION_THRESHOLD)) {
			// We have space available, so don't delete anything
			return;
		}

		// Delete a batch of random chunks
		// This reduces the chance of too many nodes deleting the same chunk
		// when they reach their storage limit
		Path dataPath = Paths.get(Settings.getInstance().getDataPath());
		for (int i=0; i<CHUNK_DELETION_BATCH_SIZE; i++) {
			if (isStopping) {
				return;
			}
			this.deleteRandomFile(repository, dataPath.toFile(), null);
		}

		// FUTURE: consider reducing the expiry time of the reader cache
	}

	public void storageLimitReachedForName(Repository repository, String name) throws InterruptedException {
		// We think that the storage limit has been reached for supplied name - but we should double check
		if (ArbitraryDataStorageManager.getInstance().isStorageSpaceAvailableForName(repository, name, DELETION_THRESHOLD)) {
			// We have space available for this name, so don't delete anything
			return;
		}

		// Delete a batch of random chunks associated with this name
		// This reduces the chance of too many nodes deleting the same chunk
		// when they reach their storage limit
		Path dataPath = Paths.get(Settings.getInstance().getDataPath());
		for (int i=0; i<CHUNK_DELETION_BATCH_SIZE; i++) {
			if (isStopping) {
				return;
			}
			this.deleteRandomFile(repository, dataPath.toFile(), name);
		}
	}

	/**
	 * Iteratively walk through given directory and delete a single random file
	 *
	 * @param directory - the base directory
	 * @return boolean - whether a file was deleted
	 */
	private boolean deleteRandomFile(Repository repository, File directory, String name) {
		Path tempDataPath = Paths.get(Settings.getInstance().getTempDataPath());

		// Pick a random directory
		final File[] contentsList = directory.listFiles();
		if (contentsList != null) {
			SecureRandom random = new SecureRandom();

			// If the directory is empty, there's nothing to do
			if (contentsList.length == 0) {
				return false;
			}

			File randomItem = contentsList[random.nextInt(contentsList.length)];

			// Skip anything relating to the temp directory
			if (FilesystemUtils.isChild(randomItem.toPath(), tempDataPath)) {
				return false;
			}
			// Make sure it exists
			if (!randomItem.exists()) {
				return false;
			}

			// If it's a directory, iteratively repeat the process
			if (randomItem.isDirectory()) {
				return this.deleteRandomFile(repository, randomItem, name);
			}

			// If it's a file, we might be able to delete it
			if (randomItem.isFile()) {

				// If the parent directory contains an ".original" file, don't delete anything
				// This indicates that the content was originally updated by this node and so
				// could be the only copy that exists.
				Path originalCopyIndicatorPath = Paths.get(randomItem.getParent(), ".original");
				if (Files.exists(originalCopyIndicatorPath)) {
					// This is an original seed copy and so shouldn't be deleted
					return false;
				}

				if (name != null) {
					// A name has been specified, so we need to make sure this file relates to
					// the name we want to delete. The signature should be the name of parent directory.
					try {
						Path parentFileNamePath = randomItem.toPath().toAbsolutePath().getParent().getFileName();
						if (parentFileNamePath != null) {
							String signature58 = parentFileNamePath.toString();
							byte[] signature = Base58.decode(signature58);
							TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
							if (transactionData == null || transactionData.getType() != Transaction.TransactionType.ARBITRARY) {
								// Not what we were expecting, so don't delete it
								return false;
							}
							ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;
							if (!Objects.equals(arbitraryTransactionData.getName(), name)) {
								// Relates to a different name - don't delete it
								return false;
							}
						}

					} catch (DataException e) {
						// Something went wrong and we weren't able to make a decision - so it's best not to delete this file
						return false;
					}
				}

				LOGGER.info("Deleting random file {} because we have reached max storage capacity...", randomItem.toString());
				boolean success = randomItem.delete();
				if (success) {
					try {
						FilesystemUtils.safeDeleteEmptyParentDirectories(randomItem.toPath().getParent());
					} catch (IOException e) {
						// Ignore cleanup failure
					}
				}
				return success;
			}
		}
		return false;
	}

	private void removePeersHostingTransactionData(Repository repository, ArbitraryTransactionData transactionData) {
		byte[] signature = transactionData.getSignature();
		try {
			repository.getArbitraryRepository().deleteArbitraryPeersWithSignature(signature);
			repository.saveChanges();
		} catch (DataException e) {
			LOGGER.debug("Unable to delete peers from lookup table for signature: {}", Base58.encode(signature));
		}
	}

	private void cleanupTempDirectory(String folder, long now, long minAge) {
		String baseDir = Settings.getInstance().getTempDataPath();
		Path tempDir = Paths.get(baseDir, folder);
		int contentsCount = 0;

		// Loop through the contents and check each one
		final File[] directories = tempDir.toFile().listFiles();
		if (directories != null) {
			for (final File directory : directories) {
				if (isStopping) {
					return;
				}
				contentsCount++;

				// We're expecting the contents of each subfolder to be a directory
				if (directory.isDirectory()) {
					if (!ArbitraryTransactionUtils.isFileRecent(directory.toPath(), now, minAge)) {
						// File isn't recent, so can be deleted
						this.safeDeleteDirectory(directory, "not recent");
					}
				}
			}
		}

		// If the directory is empty, we still need to delete its parent folder
		if (contentsCount == 0 && tempDir.toFile().isDirectory() && tempDir.toFile().exists()) {
			try {
				LOGGER.debug("Parent directory {} is empty, so deleting it", tempDir);
				FilesystemUtils.safeDeleteDirectory(tempDir, false);
			} catch(IOException e){
				LOGGER.info("Unable to delete parent directory: {}", tempDir);
			}
		}
	}

	private void cleanupReaderCache(Long now) {
		ArbitraryDataStorageManager storageManager = ArbitraryDataStorageManager.getInstance();
		String baseDir = Settings.getInstance().getTempDataPath();
		Path readerCachePath = Paths.get(baseDir, "reader");

		// Clean up names
		Path readerCacheNamesPath = Paths.get(readerCachePath.toString(), "NAME");

		// Loop through the contents and check each one
		final File[] directories = readerCacheNamesPath.toFile().listFiles();
		if (directories != null) {
			for (final File directory : directories) {
				if (isStopping) {
					return;
				}

				// Delete data relating to blocked names
				String name = directory.getName();
				if (name != null && storageManager.isNameBlocked(name)) {
					this.safeDeleteDirectory(directory, "blocked name");
				}

				// Delete cached reader data that has reached its expiry
				this.cleanupReaderCacheForName(name, now);
			}
		}
	}

	private void cleanupReaderCacheForName(String name, Long now) {
		if (name == null) {
			return;
		}

		String baseDir = Settings.getInstance().getTempDataPath();
		Path readerNameCachePath = Paths.get(baseDir, "reader", "NAME", name);

		// Loop through the contents and check each one
		final File[] directories = readerNameCachePath.toFile().listFiles();
		if (directories != null) {
			for (final File directory : directories) {
				if (isStopping) {
					return;
				}
				// Each directory is a "service" type
				String service = directory.getName();
				this.cleanupReaderCacheForNameAndService(name, service, now);
			}
		}
	}

	private void cleanupReaderCacheForNameAndService(String name, String service, Long now) {
		if (name == null || service == null) {
			return;
		}

		Path readerNameServiceCachePath = Paths.get("reader", "NAME", name, service);
		Long expiry = Settings.getInstance().getBuiltDataExpiryInterval();
		this.cleanupTempDirectory(readerNameServiceCachePath.toString(), now, expiry);
	}

	private void cleanupTempDirectory(long now) {

		// Use the "stale file timeout" for the intermediate directories.
		// These aren't used for serving content - only for building it.
		// Once the files have become stale, it's safe to delete them.
		this.cleanupTempDirectory("diff",  now, STALE_FILE_TIMEOUT);
		this.cleanupTempDirectory("join",  now, STALE_FILE_TIMEOUT);
		this.cleanupTempDirectory("merge",  now, STALE_FILE_TIMEOUT);
		this.cleanupTempDirectory("writer",  now, STALE_FILE_TIMEOUT);

		// Built resources are served out of the "reader" directory so these
		// need to be kept around for much longer.
		// Purging currently disabled, as it's not very helpful. Will revisit
		// once we implement local storage limits.
		this.cleanupReaderCache(now);

	}

	private boolean safeDeleteDirectory(File directory, String reason) {
		LOGGER.info("Deleting directory {} due to reason: {}", directory, reason);
		try {
			FilesystemUtils.safeDeleteDirectory(directory.toPath(), true);
			return true;
		} catch (IOException e) {
			LOGGER.debug("Unable to delete directory: {}", directory);
		}
		return false;
	}


	public void shutdown() {
		isStopping = true;
		this.interrupt();
	}

}
