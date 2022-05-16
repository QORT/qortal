package org.qortal.arbitrary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.arbitrary.ArbitraryDataFile.ResourceIdType;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.arbitrary.ArbitraryDataBuildManager;
import org.qortal.controller.arbitrary.ArbitraryDataManager;
import org.qortal.controller.arbitrary.ArbitraryDataStorageManager;
import org.qortal.data.arbitrary.ArbitraryResourceStatus;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.list.ResourceListManager;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.utils.ArbitraryTransactionUtils;
import org.qortal.utils.FilesystemUtils;
import org.qortal.utils.NTP;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.qortal.data.arbitrary.ArbitraryResourceStatus.Status;

public class ArbitraryDataResource {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataResource.class);

    protected final String resourceId;
    protected final ResourceIdType resourceIdType;
    protected final Service service;
    protected final String identifier;

    private List<ArbitraryTransactionData> transactions;
    private ArbitraryTransactionData latestPutTransaction;
    private int layerCount;
    private Integer localChunkCount = null;
    private Integer totalChunkCount = null;

    public ArbitraryDataResource(String resourceId, ResourceIdType resourceIdType, Service service, String identifier) {
        this.resourceId = resourceId.toLowerCase();
        this.resourceIdType = resourceIdType;
        this.service = service;

        // If identifier is a blank string, or reserved keyword "default", treat it as null
        if (identifier == null || identifier.equals("") || identifier.equals("default")) {
            identifier = null;
        }
        this.identifier = identifier;
    }

    public ArbitraryResourceStatus getStatus(boolean quick) {
        // Calculate the chunk counts
        // Avoid this for "quick" statuses, to speed things up
        if (!quick) {
            this.calculateChunkCounts();
        }

        if (resourceIdType != ResourceIdType.NAME) {
            // We only support statuses for resources with a name
            return new ArbitraryResourceStatus(Status.UNSUPPORTED, this.localChunkCount, this.totalChunkCount);
        }

        // Check if the name is blocked
        if (ResourceListManager.getInstance()
                .listContains("blockedNames", this.resourceId, false)) {
            return new ArbitraryResourceStatus(Status.BLOCKED, this.localChunkCount, this.totalChunkCount);
        }

        // Check if a build has failed
        ArbitraryDataBuildQueueItem queueItem =
                new ArbitraryDataBuildQueueItem(resourceId, resourceIdType, service, identifier);
        if (ArbitraryDataBuildManager.getInstance().isInFailedBuildsList(queueItem)) {
            return new ArbitraryResourceStatus(Status.BUILD_FAILED, this.localChunkCount, this.totalChunkCount);
        }

        // Firstly check the cache to see if it's already built
        ArbitraryDataReader arbitraryDataReader = new ArbitraryDataReader(
                resourceId, resourceIdType, service, identifier);
        if (arbitraryDataReader.isCachedDataAvailable()) {
            return new ArbitraryResourceStatus(Status.READY, this.localChunkCount, this.totalChunkCount);
        }

        // Check if we have all data locally for this resource
        if (!this.allFilesDownloaded()) {
            if (this.isDownloading()) {
                return new ArbitraryResourceStatus(Status.DOWNLOADING, this.localChunkCount, this.totalChunkCount);
            }
            else if (this.isDataPotentiallyAvailable()) {
                return new ArbitraryResourceStatus(Status.PUBLISHED, this.localChunkCount, this.totalChunkCount);
            }
            return new ArbitraryResourceStatus(Status.MISSING_DATA, this.localChunkCount, this.totalChunkCount);
        }

        // Check if there's a build in progress
        if (ArbitraryDataBuildManager.getInstance().isInBuildQueue(queueItem)) {
            return new ArbitraryResourceStatus(Status.BUILDING, this.localChunkCount, this.totalChunkCount);
        }

        // We have all data locally
        return new ArbitraryResourceStatus(Status.DOWNLOADED, this.localChunkCount, this.totalChunkCount);
    }

    public boolean delete() {
        try {
            this.fetchTransactions();

            List<ArbitraryTransactionData> transactionDataList = new ArrayList<>(this.transactions);

            for (ArbitraryTransactionData transactionData : transactionDataList) {
                byte[] hash = transactionData.getData();
                byte[] metadataHash = transactionData.getMetadataHash();
                byte[] signature = transactionData.getSignature();
                ArbitraryDataFile arbitraryDataFile = ArbitraryDataFile.fromHash(hash, signature);
                arbitraryDataFile.setMetadataHash(metadataHash);

                // Delete any chunks or complete files from each transaction
                arbitraryDataFile.deleteAll();
            }

            // Also delete cached data for the entire resource
            this.deleteCache();

            // Invalidate the hosted transactions cache as we have removed an item
            ArbitraryDataStorageManager.getInstance().invalidateHostedTransactionsCache();

            return true;

        } catch (DataException | IOException e) {
            return false;
        }
    }

    public void deleteCache() throws IOException {
        // Don't delete anything if there's a build in progress
        ArbitraryDataBuildQueueItem queueItem =
                new ArbitraryDataBuildQueueItem(resourceId, resourceIdType, service, identifier);
        if (ArbitraryDataBuildManager.getInstance().isInBuildQueue(queueItem)) {
            return;
        }

        String baseDir = Settings.getInstance().getTempDataPath();
        String identifier = this.identifier != null ?  this.identifier : "default";
        Path cachePath = Paths.get(baseDir, "reader", this.resourceIdType.toString(), this.resourceId, this.service.toString(), identifier);
        if (cachePath.toFile().exists()) {
            boolean success = FilesystemUtils.safeDeleteDirectory(cachePath, true);
            if (success) {
                LOGGER.info("Cleared cache for resource {}", this.toString());
            }
        }
    }

    private boolean allFilesDownloaded() {
        // Use chunk counts to speed things up if we can
        if (this.localChunkCount != null && this.totalChunkCount != null &&
                this.localChunkCount >= this.totalChunkCount) {
            return true;
        }

        try {
            this.fetchTransactions();

            List<ArbitraryTransactionData> transactionDataList = new ArrayList<>(this.transactions);

            for (ArbitraryTransactionData transactionData : transactionDataList) {
                if (!ArbitraryTransactionUtils.completeFileExists(transactionData) ||
                    !ArbitraryTransactionUtils.allChunksExist(transactionData)) {
                    return false;
                }
            }
            return true;

        } catch (DataException e) {
            return false;
        }
    }

    private void calculateChunkCounts() {
        try {
            this.fetchTransactions();

            List<ArbitraryTransactionData> transactionDataList = new ArrayList<>(this.transactions);
            int localChunkCount = 0;
            int totalChunkCount = 0;

            for (ArbitraryTransactionData transactionData : transactionDataList) {
                localChunkCount += ArbitraryTransactionUtils.ourChunkCount(transactionData);
                totalChunkCount += ArbitraryTransactionUtils.totalChunkCount(transactionData);
            }

            this.localChunkCount = localChunkCount;
            this.totalChunkCount = totalChunkCount;

        } catch (DataException e) {}
    }

    private boolean isRateLimited() {
        try {
            this.fetchTransactions();

            List<ArbitraryTransactionData> transactionDataList = new ArrayList<>(this.transactions);

            for (ArbitraryTransactionData transactionData : transactionDataList) {
                if (ArbitraryDataManager.getInstance().isSignatureRateLimited(transactionData.getSignature())) {
                    return true;
                }
            }
            return true;

        } catch (DataException e) {
            return false;
        }
    }

    /**
     * Best guess as to whether data might be available
     * This is only used to give an indication to the user of progress
     * @return - whether data might be available on the network
     */
    private boolean isDataPotentiallyAvailable() {
        try {
            this.fetchTransactions();
            Long now = NTP.getTime();
            if (now == null) {
                return false;
            }

            List<ArbitraryTransactionData> transactionDataList = new ArrayList<>(this.transactions);

            for (ArbitraryTransactionData transactionData : transactionDataList) {
                long lastRequestTime = ArbitraryDataManager.getInstance().lastRequestForSignature(transactionData.getSignature());
                // If we haven't requested yet, or requested in the last 30 seconds, there's still a
                // chance that data is on its way but hasn't arrived yet
                if (lastRequestTime == 0 || now - lastRequestTime < 30 * 1000L) {
                    return true;
                }
            }
            return false;

        } catch (DataException e) {
            return false;
        }
    }


    /**
     * Best guess as to whether we are currently downloading a resource
     * This is only used to give an indication to the user of progress
     * @return - whether we are trying to download the resource
     */
    private boolean isDownloading() {
        try {
            this.fetchTransactions();
            Long now = NTP.getTime();
            if (now == null) {
                return false;
            }

            List<ArbitraryTransactionData> transactionDataList = new ArrayList<>(this.transactions);

            for (ArbitraryTransactionData transactionData : transactionDataList) {
                long lastRequestTime = ArbitraryDataManager.getInstance().lastRequestForSignature(transactionData.getSignature());
                // If were have requested data in the last 30 seconds, treat it as "downloading"
                if (lastRequestTime > 0 && now - lastRequestTime < 30 * 1000L) {
                    return true;
                }
            }

            // FUTURE: we may want to check for file hashes (including the metadata file hash) in
            // ArbitraryDataManager.arbitraryDataFileRequests and return true if one is found.

            return false;

        } catch (DataException e) {
            return false;
        }
    }



    private void fetchTransactions() throws DataException {
        if (this.transactions != null && !this.transactions.isEmpty()) {
            // Already fetched
            return;
        }

        try (final Repository repository = RepositoryManager.getRepository()) {

            // Get the most recent PUT
            ArbitraryTransactionData latestPut = repository.getArbitraryRepository()
                    .getLatestTransaction(this.resourceId, this.service, ArbitraryTransactionData.Method.PUT, this.identifier);
            if (latestPut == null) {
                String message = String.format("Couldn't find PUT transaction for name %s, service %s and identifier %s",
                        this.resourceId, this.service, this.identifierString());
                throw new DataException(message);
            }
            this.latestPutTransaction = latestPut;

            // Load all transactions since the latest PUT
            List<ArbitraryTransactionData> transactionDataList = repository.getArbitraryRepository()
                    .getArbitraryTransactions(this.resourceId, this.service, this.identifier, latestPut.getTimestamp());

            this.transactions = transactionDataList;
            this.layerCount = transactionDataList.size();
        }
    }

    private String resourceIdString() {
        return resourceId != null ? resourceId : "";
    }

    private String resourceIdTypeString() {
        return resourceIdType != null ? resourceIdType.toString() : "";
    }

    private String serviceString() {
        return service != null ? service.toString() : "";
    }

    private String identifierString() {
        return identifier != null ? identifier : "";
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", this.serviceString(), this.resourceIdString(), this.identifierString());
    }


    /**
     * @return unique key used to identify this resource
     */
    public String getUniqueKey() {
        return String.format("%s-%s-%s", this.service, this.resourceId, this.identifier).toLowerCase();
    }

    public String getResourceId() {
        return this.resourceId;
    }

    public Service getService() {
        return this.service;
    }

    public String getIdentifier() {
        return this.identifier;
    }
}
