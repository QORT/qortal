package org.qortal.controller.arbitrary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.controller.Controller;
import org.qortal.data.arbitrary.ArbitraryRelayInfo;
import org.qortal.data.network.ArbitraryPeerData;
import org.qortal.data.network.PeerData;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.network.Network;
import org.qortal.network.Peer;
import org.qortal.network.message.*;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.utils.ArbitraryTransactionUtils;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;
import org.qortal.utils.Triple;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ArbitraryDataFileManager extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataFileManager.class);

    private static ArbitraryDataFileManager instance;
    private volatile boolean isStopping = false;


    /**
     * Map to keep track of our in progress (outgoing) arbitrary data file requests
     */
    public Map<String, Long> arbitraryDataFileRequests = Collections.synchronizedMap(new HashMap<>());

    /**
     * Map to keep track of hashes that we might need to relay
     */
    public List<ArbitraryRelayInfo> arbitraryRelayMap = Collections.synchronizedList(new ArrayList<>());

    /**
     * Map to keep track of any arbitrary data file hash responses
     * Key: string - the hash encoded in base58
     * Value: Triple<respondingPeer, signature58, timeResponded>
     */
    public Map<String, Triple<Peer, String, Long>> arbitraryDataFileHashResponses = Collections.synchronizedMap(new HashMap<>());


    private ArbitraryDataFileManager() {
    }

    public static ArbitraryDataFileManager getInstance() {
        if (instance == null)
            instance = new ArbitraryDataFileManager();

        return instance;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Arbitrary Data File Manager");

        try {
            // Use a fixed thread pool to execute the arbitrary data file requests
            int threadCount = 10;
            ExecutorService arbitraryDataFileRequestExecutor = Executors.newFixedThreadPool(threadCount);
            for (int i = 0; i < threadCount; i++) {
                arbitraryDataFileRequestExecutor.execute(new ArbitraryDataFileRequestThread());
            }

            while (!isStopping) {
                // Nothing to do yet
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            // Fall-through to exit thread...
        }
    }

    public void shutdown() {
        isStopping = true;
        this.interrupt();
    }


    public void cleanupRequestCache(Long now) {
        if (now == null) {
            return;
        }
        final long requestMinimumTimestamp = now - ArbitraryDataManager.getInstance().ARBITRARY_REQUEST_TIMEOUT;
        arbitraryDataFileRequests.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < requestMinimumTimestamp);

        final long relayMinimumTimestamp = now - ArbitraryDataManager.getInstance().ARBITRARY_RELAY_TIMEOUT;
        arbitraryRelayMap.removeIf(entry -> entry == null || entry.getTimestamp() == null || entry.getTimestamp() < relayMinimumTimestamp);
        arbitraryDataFileHashResponses.entrySet().removeIf(entry -> entry.getValue().getC() == null || entry.getValue().getC() < relayMinimumTimestamp);
    }



    // Fetch data files by hash

    public boolean fetchArbitraryDataFiles(Repository repository,
                                           Peer peer,
                                           byte[] signature,
                                           ArbitraryTransactionData arbitraryTransactionData,
                                           List<byte[]> hashes) throws DataException {

        // Load data file(s)
        ArbitraryDataFile arbitraryDataFile = ArbitraryDataFile.fromHash(arbitraryTransactionData.getData(), signature);
        byte[] metadataHash = arbitraryTransactionData.getMetadataHash();
        arbitraryDataFile.setMetadataHash(metadataHash);
        boolean receivedAtLeastOneFile = false;

        // Now fetch actual data from this peer
        for (byte[] hash : hashes) {
            if (isStopping) {
                return false;
            }
            String hash58 = Base58.encode(hash);
            if (!arbitraryDataFile.chunkExists(hash)) {
                // Only request the file if we aren't already requesting it from someone else
                if (!arbitraryDataFileRequests.containsKey(Base58.encode(hash))) {
                    LOGGER.debug("Requesting data file {} from peer {}", hash58, peer);
                    Long startTime = NTP.getTime();
                    ArbitraryDataFileMessage receivedArbitraryDataFileMessage = fetchArbitraryDataFile(peer, null, signature, hash, null);
                    Long endTime = NTP.getTime();
                    if (receivedArbitraryDataFileMessage != null) {
                        LOGGER.debug("Received data file {} from peer {}. Time taken: {} ms", receivedArbitraryDataFileMessage.getArbitraryDataFile().getHash58(), peer, (endTime-startTime));
                        receivedAtLeastOneFile = true;

                        // Remove this hash from arbitraryDataFileHashResponses now that we have received it
                        arbitraryDataFileHashResponses.remove(hash58);
                    }
                    else {
                        LOGGER.debug("Peer {} didn't respond with data file {} for signature {}. Time taken: {} ms", peer, Base58.encode(hash), Base58.encode(signature), (endTime-startTime));

                        // Remove this hash from arbitraryDataFileHashResponses now that we have failed to receive it
                        arbitraryDataFileHashResponses.remove(hash58);

                        // Stop asking for files from this peer
                        break;
                    }
                }
                else {
                    LOGGER.trace("Already requesting data file {} for signature {} from peer {}", arbitraryDataFile, Base58.encode(signature), peer);
                }
            }
            else {
                // Remove this hash from arbitraryDataFileHashResponses because we have a local copy
                arbitraryDataFileHashResponses.remove(hash58);
            }
        }

        if (receivedAtLeastOneFile) {
            // Update our lookup table to indicate that this peer holds data for this signature
            String peerAddress = peer.getPeerData().getAddress().toString();
            ArbitraryPeerData arbitraryPeerData = new ArbitraryPeerData(signature, peer);
            repository.discardChanges();
            if (arbitraryPeerData.isPeerAddressValid()) {
                LOGGER.debug("Adding arbitrary peer: {} for signature {}", peerAddress, Base58.encode(signature));
                repository.getArbitraryRepository().save(arbitraryPeerData);
                repository.saveChanges();
            }

            // Invalidate the hosted transactions cache as we are now hosting something new
            ArbitraryDataStorageManager.getInstance().invalidateHostedTransactionsCache();

            // Check if we have all the files we need for this transaction
            if (arbitraryDataFile.allFilesExist()) {

                // We have all the chunks for this transaction, so we should invalidate the transaction's name's
                // data cache so that it is rebuilt the next time we serve it
                ArbitraryDataManager.getInstance().invalidateCache(arbitraryTransactionData);

                // We may also need to broadcast to the network that we are now hosting files for this transaction,
                // but only if these files are in accordance with our storage policy
                if (ArbitraryDataStorageManager.getInstance().canStoreData(arbitraryTransactionData)) {
                    // Use a null peer address to indicate our own
                    Message newArbitrarySignatureMessage = new ArbitrarySignaturesMessage(null, 0, Arrays.asList(signature));
                    Network.getInstance().broadcast(broadcastPeer -> newArbitrarySignatureMessage);
                }
            }

        }

        return receivedAtLeastOneFile;
    }

    private ArbitraryDataFileMessage fetchArbitraryDataFile(Peer peer, Peer requestingPeer, byte[] signature, byte[] hash, Message originalMessage) throws DataException {
        ArbitraryDataFile existingFile = ArbitraryDataFile.fromHash(hash, signature);
        boolean fileAlreadyExists = existingFile.exists();
        String hash58 = Base58.encode(hash);
        Message message = null;

        // Fetch the file if it doesn't exist locally
        if (!fileAlreadyExists) {
            LOGGER.debug(String.format("Fetching data file %.8s from peer %s", hash58, peer));
            arbitraryDataFileRequests.put(hash58, NTP.getTime());
            Message getArbitraryDataFileMessage = new GetArbitraryDataFileMessage(signature, hash);

            try {
                message = peer.getResponseWithTimeout(getArbitraryDataFileMessage, (int) ArbitraryDataManager.ARBITRARY_REQUEST_TIMEOUT);
            } catch (InterruptedException e) {
                // Will return below due to null message
            }
            arbitraryDataFileRequests.remove(hash58);
            LOGGER.trace(String.format("Removed hash %.8s from arbitraryDataFileRequests", hash58));

            // We may need to remove the file list request, if we have all the files for this transaction
            this.handleFileListRequests(signature);

            if (message == null) {
                LOGGER.debug("Received null message from peer {}", peer);
                return null;
            }
            if (message.getType() != Message.MessageType.ARBITRARY_DATA_FILE) {
                LOGGER.debug("Received message with invalid type: {} from peer {}", message.getType(), peer);
                return null;
            }
        }
        else {
            LOGGER.debug(String.format("File hash %s already exists, so skipping the request", hash58));
        }
        ArbitraryDataFileMessage arbitraryDataFileMessage = (ArbitraryDataFileMessage) message;

        // We might want to forward the request to the peer that originally requested it
        this.handleArbitraryDataFileForwarding(requestingPeer, message, originalMessage);

        boolean isRelayRequest = (requestingPeer != null);
        if (isRelayRequest) {
            if (!fileAlreadyExists) {
                // File didn't exist locally before the request, and it's a forwarding request, so delete it
                LOGGER.debug("Deleting file {} because it was needed for forwarding only", Base58.encode(hash));
                ArbitraryDataFile dataFile = arbitraryDataFileMessage.getArbitraryDataFile();

                // Keep trying to delete the data until it is deleted, or we reach 10 attempts
                dataFile.delete(10);
            }
        }

        return arbitraryDataFileMessage;
    }

    private void handleFileListRequests(byte[] signature) {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Fetch the transaction data
            ArbitraryTransactionData arbitraryTransactionData = ArbitraryTransactionUtils.fetchTransactionData(repository, signature);
            if (arbitraryTransactionData == null) {
                return;
            }

            boolean allChunksExist = ArbitraryTransactionUtils.allChunksExist(arbitraryTransactionData);

            if (allChunksExist) {
                // Update requests map to reflect that we've received all chunks
                ArbitraryDataFileListManager.getInstance().deleteFileListRequestsForSignature(signature);
            }

        } catch (DataException e) {
            LOGGER.debug("Unable to handle file list requests: {}", e.getMessage());
        }
    }

    public void handleArbitraryDataFileForwarding(Peer requestingPeer, Message message, Message originalMessage) {
        // Return if there is no originally requesting peer to forward to
        if (requestingPeer == null) {
            return;
        }

        // Return if we're not in relay mode or if this request doesn't need forwarding
        if (!Settings.getInstance().isRelayModeEnabled()) {
            return;
        }

        LOGGER.debug("Received arbitrary data file - forwarding is needed");

        // The ID needs to match that of the original request
        message.setId(originalMessage.getId());

        if (!requestingPeer.sendMessage(message)) {
            LOGGER.debug("Failed to forward arbitrary data file to peer {}", requestingPeer);
            requestingPeer.disconnect("failed to forward arbitrary data file");
        }
        else {
            LOGGER.debug("Forwarded arbitrary data file to peer {}", requestingPeer);
        }
    }


    // Fetch data directly from peers

    public boolean fetchDataFilesFromPeersForSignature(byte[] signature) {
        String signature58 = Base58.encode(signature);
        ArbitraryDataFileListManager.getInstance().addToSignatureRequests(signature58, false, true);

        // Firstly fetch peers that claim to be hosting files for this signature
        try (final Repository repository = RepositoryManager.getRepository()) {

            List<ArbitraryPeerData> peers = repository.getArbitraryRepository().getArbitraryPeerDataForSignature(signature);
            if (peers == null || peers.isEmpty()) {
                LOGGER.debug("No peers found for signature {}", signature58);
                return false;
            }

            LOGGER.debug("Attempting a direct peer connection for signature {}...", signature58);

            // Peers found, so pick a random one and request data from it
            int index = new SecureRandom().nextInt(peers.size());
            ArbitraryPeerData arbitraryPeerData = peers.get(index);
            String peerAddressString = arbitraryPeerData.getPeerAddress();
            boolean success = Network.getInstance().requestDataFromPeer(peerAddressString, signature);

            // Parse the peer address to find the host and port
            String host = null;
            int port = -1;
            String[] parts = peerAddressString.split(":");
            if (parts.length > 1) {
                host = parts[0];
                port = Integer.parseInt(parts[1]);
            }

            // If unsuccessful, and using a non-standard port, try a second connection with the default listen port,
            // since almost all nodes use that. This is a workaround to account for any ephemeral ports that may
            // have made it into the dataset.
            if (!success) {
                if (host != null && port > 0) {
                    int defaultPort = Settings.getInstance().getDefaultListenPort();
                    if (port != defaultPort) {
                        String newPeerAddressString = String.format("%s:%d", host, defaultPort);
                        success = Network.getInstance().requestDataFromPeer(newPeerAddressString, signature);
                    }
                }
            }

            // If _still_ unsuccessful, try matching the peer's IP address with some known peers, and then connect
            // to each of those in turn until one succeeds.
            if (!success) {
                if (host != null) {
                    final String finalHost = host;
                    List<PeerData> knownPeers = Network.getInstance().getAllKnownPeers().stream()
                            .filter(knownPeerData -> knownPeerData.getAddress().getHost().equals(finalHost))
                            .collect(Collectors.toList());
                    // Loop through each match and attempt a connection
                    for (PeerData matchingPeer : knownPeers) {
                        String matchingPeerAddress = matchingPeer.getAddress().toString();
                        success = Network.getInstance().requestDataFromPeer(matchingPeerAddress, signature);
                        if (success) {
                            // Successfully connected, so stop making connections
                            break;
                        }
                    }
                }
            }

            // Keep track of the success or failure
            arbitraryPeerData.markAsAttempted();
            if (success) {
                arbitraryPeerData.markAsRetrieved();
                arbitraryPeerData.incrementSuccesses();
            }
            else {
                arbitraryPeerData.incrementFailures();
            }
            repository.discardChanges();
            repository.getArbitraryRepository().save(arbitraryPeerData);
            repository.saveChanges();

            return success;

        } catch (DataException e) {
            LOGGER.debug("Unable to fetch peer list from repository");
        }

        return false;
    }


    // Relays

    private List<ArbitraryRelayInfo> getRelayInfoListForHash(String hash58) {
        synchronized (arbitraryRelayMap) {
            return arbitraryRelayMap.stream()
                    .filter(relayInfo -> Objects.equals(relayInfo.getHash58(), hash58))
                    .collect(Collectors.toList());
        }
    }

    private ArbitraryRelayInfo getRandomRelayInfoEntryForHash(String hash58) {
        LOGGER.trace("Fetching random relay info for hash: {}", hash58);
        List<ArbitraryRelayInfo> relayInfoList = this.getRelayInfoListForHash(hash58);
        if (relayInfoList != null && !relayInfoList.isEmpty()) {

            // Pick random item
            int index = new SecureRandom().nextInt(relayInfoList.size());
            LOGGER.trace("Returning random relay info for hash: {} (index {})", hash58, index);
            return relayInfoList.get(index);
        }
        LOGGER.trace("No relay info exists for hash: {}", hash58);
        return null;
    }

    public void addToRelayMap(ArbitraryRelayInfo newEntry) {
        if (newEntry == null || !newEntry.isValid()) {
            return;
        }

        // Remove existing entry for this peer if it exists, to renew the timestamp
        this.removeFromRelayMap(newEntry);

        // Re-add
        arbitraryRelayMap.add(newEntry);
        LOGGER.debug("Added entry to relay map: {}", newEntry);
    }

    private void removeFromRelayMap(ArbitraryRelayInfo entry) {
        arbitraryRelayMap.removeIf(relayInfo -> relayInfo.equals(entry));
    }


    // Network handlers

    public void onNetworkGetArbitraryDataFileMessage(Peer peer, Message message) {
        // Don't respond if QDN is disabled
        if (!Settings.getInstance().isQdnEnabled()) {
            return;
        }

        GetArbitraryDataFileMessage getArbitraryDataFileMessage = (GetArbitraryDataFileMessage) message;
        byte[] hash = getArbitraryDataFileMessage.getHash();
        String hash58 = Base58.encode(hash);
        byte[] signature = getArbitraryDataFileMessage.getSignature();
        Controller.getInstance().stats.getArbitraryDataFileMessageStats.requests.incrementAndGet();

        LOGGER.debug("Received GetArbitraryDataFileMessage from peer {} for hash {}", peer, Base58.encode(hash));

        try {
            ArbitraryDataFile arbitraryDataFile = ArbitraryDataFile.fromHash(hash, signature);
            ArbitraryRelayInfo relayInfo = this.getRandomRelayInfoEntryForHash(hash58);

            if (arbitraryDataFile.exists()) {
                LOGGER.trace("Hash {} exists", hash58);

                // We can serve the file directly as we already have it
                ArbitraryDataFileMessage arbitraryDataFileMessage = new ArbitraryDataFileMessage(signature, arbitraryDataFile);
                arbitraryDataFileMessage.setId(message.getId());
                if (!peer.sendMessage(arbitraryDataFileMessage)) {
                    LOGGER.debug("Couldn't sent file");
                    peer.disconnect("failed to send file");
                }
                LOGGER.debug("Sent file {}", arbitraryDataFile);
            }
            else if (relayInfo != null) {
                LOGGER.debug("We have relay info for hash {}", Base58.encode(hash));
                // We need to ask this peer for the file
                Peer peerToAsk = relayInfo.getPeer();
                if (peerToAsk != null) {

                    // Forward the message to this peer
                    LOGGER.debug("Asking peer {} for hash {}", peerToAsk, hash58);
                    this.fetchArbitraryDataFile(peerToAsk, peer, signature, hash, message);
                }
                else {
                    LOGGER.debug("Peer {} not found in relay info", peer);
                }
            }
            else {
                LOGGER.debug("Hash {} doesn't exist and we don't have relay info", hash58);

                // We don't have this file
                Controller.getInstance().stats.getArbitraryDataFileMessageStats.unknownFiles.getAndIncrement();

                // Send valid, yet unexpected message type in response, so peer's synchronizer doesn't have to wait for timeout
                LOGGER.debug(String.format("Sending 'file unknown' response to peer %s for GET_FILE request for unknown file %s", peer, arbitraryDataFile));

                // We'll send empty block summaries message as it's very short
                // TODO: use a different message type here
                Message fileUnknownMessage = new BlockSummariesMessage(Collections.emptyList());
                fileUnknownMessage.setId(message.getId());
                if (!peer.sendMessage(fileUnknownMessage)) {
                    LOGGER.debug("Couldn't sent file-unknown response");
                    peer.disconnect("failed to send file-unknown response");
                }
                else {
                    LOGGER.debug("Sent file-unknown response for file {}", arbitraryDataFile);
                }
            }
        }
        catch (DataException e) {
            LOGGER.debug("Unable to handle request for arbitrary data file: {}", hash58);
        }
    }

}
