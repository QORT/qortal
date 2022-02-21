package org.qortal.controller.arbitrary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.Controller;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.network.Peer;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.utils.ArbitraryTransactionUtils;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;
import org.qortal.utils.Triple;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class ArbitraryDataFileRequestThread implements Runnable {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataFileRequestThread.class);

    public ArbitraryDataFileRequestThread() {

    }

    @Override
    public void run() {
        Thread.currentThread().setName("Arbitrary Data File Request Thread");

        try {
            while (!Controller.isStopping()) {
                Long now = NTP.getTime();
                this.processFileHashes(now);
            }
        } catch (InterruptedException e) {
            // Fall-through to exit thread...
        }
    }

    private void processFileHashes(Long now) throws InterruptedException {
		if (Controller.isStopping()) {
            return;
        }

        ArbitraryDataFileManager arbitraryDataFileManager = ArbitraryDataFileManager.getInstance();
        String signature58 = null;
        String hash58 = null;
        Peer peer = null;
        boolean shouldProcess = false;

        synchronized (arbitraryDataFileManager.arbitraryDataFileHashResponses) {
            Iterator iterator = arbitraryDataFileManager.arbitraryDataFileHashResponses.entrySet().iterator();
            while (iterator.hasNext()) {
                if (Controller.isStopping()) {
                    return;
                }

                Map.Entry entry = (Map.Entry) iterator.next();
                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                    iterator.remove();
                    continue;
                }

                hash58 = (String) entry.getKey();
                Triple<Peer, String, Long> value = (Triple<Peer, String, Long>) entry.getValue();
                if (value == null) {
                    iterator.remove();
                    continue;
                }

                peer = value.getA();
                signature58 = value.getB();
                Long timestamp = value.getC();

                if (now - timestamp >= ArbitraryDataManager.ARBITRARY_RELAY_TIMEOUT || signature58 == null || peer == null) {
                    // Ignore - to be deleted
                    iterator.remove();
                    continue;
                }

                // We want to process this file
                shouldProcess = true;
                iterator.remove();
                break;
            }
        }

        if (!shouldProcess) {
            // Nothing to do
            Thread.sleep(1000L);
            return;
        }

        byte[] hash = Base58.decode(hash58);
        byte[] signature = Base58.decode(signature58);

        // Fetch the transaction data
        try (final Repository repository = RepositoryManager.getRepository()) {
            ArbitraryTransactionData arbitraryTransactionData = ArbitraryTransactionUtils.fetchTransactionData(repository, signature);
            if (arbitraryTransactionData == null) {
                return;
            }

            if (signature == null || hash == null || peer == null || arbitraryTransactionData == null) {
                return;
            }

            LOGGER.debug("Fetching file {} from peer {} via request thread...", hash58, peer);
            arbitraryDataFileManager.fetchArbitraryDataFiles(repository, peer, signature, arbitraryTransactionData, Arrays.asList(hash));

        } catch (DataException e) {
            LOGGER.debug("Unable to process file hashes: {}", e.getMessage());
        }
    }
}
