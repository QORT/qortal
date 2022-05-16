package org.qortal.test.arbitrary;

import org.junit.Before;
import org.junit.Test;
import org.qortal.crypto.Crypto;
import org.qortal.data.network.ArbitraryPeerData;
import org.qortal.data.network.PeerData;
import org.qortal.network.Peer;
import org.qortal.network.PeerAddress;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.Common;
import org.qortal.utils.NTP;

import java.util.Random;

import static org.junit.Assert.*;

public class ArbitraryPeerTests extends Common {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @Test
    public void testSaveArbitraryPeerData() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            String peerAddress = "123.124.125.126:12392";
            String host = peerAddress.split(":")[0];

            // Create random bytes to represent a signature
            byte[] signature = new byte[64];
            new Random().nextBytes(signature);

            // Make sure we don't have an entry for this hash/peer combination
            assertNull(repository.getArbitraryRepository().getArbitraryPeerDataForSignatureAndHost(signature, host));

            // Now add this mapping to the db
            Peer peer = new Peer(new PeerData(PeerAddress.fromString(peerAddress)));
            ArbitraryPeerData arbitraryPeerData = new ArbitraryPeerData(signature, peer);
            assertTrue(arbitraryPeerData.isPeerAddressValid());
            repository.getArbitraryRepository().save(arbitraryPeerData);

            // We should now have an entry for this hash/peer combination
            ArbitraryPeerData retrievedArbitraryPeerData = repository.getArbitraryRepository()
                    .getArbitraryPeerDataForSignatureAndHost(signature, host);
            assertNotNull(retrievedArbitraryPeerData);

            // .. and its data should match what was saved
            assertArrayEquals(Crypto.digest(signature), retrievedArbitraryPeerData.getHash());
            assertEquals(peerAddress, retrievedArbitraryPeerData.getPeerAddress());

        }
    }

    @Test
    public void testUpdateArbitraryPeerData() throws DataException, InterruptedException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            String peerAddress = "123.124.125.126:12392";
            String host = peerAddress.split(":")[0];

            // Create random bytes to represent a signature
            byte[] signature = new byte[64];
            new Random().nextBytes(signature);

            // Make sure we don't have an entry for this hash/peer combination
            assertNull(repository.getArbitraryRepository().getArbitraryPeerDataForSignatureAndHost(signature, host));

            // Now add this mapping to the db
            Peer peer = new Peer(new PeerData(PeerAddress.fromString(peerAddress)));
            ArbitraryPeerData arbitraryPeerData = new ArbitraryPeerData(signature, peer);
            assertTrue(arbitraryPeerData.isPeerAddressValid());
            repository.getArbitraryRepository().save(arbitraryPeerData);

            // We should now have an entry for this hash/peer combination
            ArbitraryPeerData retrievedArbitraryPeerData = repository.getArbitraryRepository()
                    .getArbitraryPeerDataForSignatureAndHost(signature, host);
            assertNotNull(retrievedArbitraryPeerData);

            // .. and its data should match what was saved
            assertArrayEquals(Crypto.digest(signature), retrievedArbitraryPeerData.getHash());
            assertEquals(peerAddress, retrievedArbitraryPeerData.getPeerAddress());

            // All stats should be zero
            assertEquals(Integer.valueOf(0), retrievedArbitraryPeerData.getSuccesses());
            assertEquals(Integer.valueOf(0), retrievedArbitraryPeerData.getFailures());
            assertEquals(Long.valueOf(0), retrievedArbitraryPeerData.getLastAttempted());
            assertEquals(Long.valueOf(0), retrievedArbitraryPeerData.getLastRetrieved());

            // Now modify some values and re-save
            retrievedArbitraryPeerData.incrementSuccesses(); retrievedArbitraryPeerData.incrementSuccesses(); // Twice
            retrievedArbitraryPeerData.incrementFailures(); // Once
            retrievedArbitraryPeerData.markAsAttempted();
            Thread.sleep(100);
            retrievedArbitraryPeerData.markAsRetrieved();
            assertTrue(arbitraryPeerData.isPeerAddressValid());
            repository.getArbitraryRepository().save(retrievedArbitraryPeerData);

            // Retrieve data once again
            ArbitraryPeerData updatedArbitraryPeerData = repository.getArbitraryRepository()
                    .getArbitraryPeerDataForSignatureAndHost(signature, host);
            assertNotNull(updatedArbitraryPeerData);

            // Check the values
            assertArrayEquals(Crypto.digest(signature), updatedArbitraryPeerData.getHash());
            assertEquals(peerAddress, updatedArbitraryPeerData.getPeerAddress());
            assertEquals(Integer.valueOf(2), updatedArbitraryPeerData.getSuccesses());
            assertEquals(Integer.valueOf(1), updatedArbitraryPeerData.getFailures());
            assertTrue(updatedArbitraryPeerData.getLastRetrieved().longValue() > 0L);
            assertTrue(updatedArbitraryPeerData.getLastAttempted().longValue() > 0L);
            assertTrue(updatedArbitraryPeerData.getLastRetrieved() > updatedArbitraryPeerData.getLastAttempted());
            assertTrue(NTP.getTime() - updatedArbitraryPeerData.getLastRetrieved() < 1000);
            assertTrue(NTP.getTime() - updatedArbitraryPeerData.getLastAttempted() < 1000);
        }
    }

    @Test
    public void testDuplicatePeerHost() throws DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            String peerAddress1 = "123.124.125.126:12392";
            String peerAddress2 = "123.124.125.126:62392";
            String host1 = peerAddress1.split(":")[0];
            String host2 = peerAddress2.split(":")[0];

            // Create random bytes to represent a signature
            byte[] signature = new byte[64];
            new Random().nextBytes(signature);

            // Make sure we don't have an entry for these hash/peer combinations
            assertNull(repository.getArbitraryRepository().getArbitraryPeerDataForSignatureAndHost(signature, host1));
            assertNull(repository.getArbitraryRepository().getArbitraryPeerDataForSignatureAndHost(signature, host2));

            // Now add this mapping to the db
            Peer peer = new Peer(new PeerData(PeerAddress.fromString(peerAddress1)));
            ArbitraryPeerData arbitraryPeerData = new ArbitraryPeerData(signature, peer);
            assertTrue(arbitraryPeerData.isPeerAddressValid());
            repository.getArbitraryRepository().save(arbitraryPeerData);

            // We should now have an entry for this hash/peer combination
            ArbitraryPeerData retrievedArbitraryPeerData = repository.getArbitraryRepository()
                    .getArbitraryPeerDataForSignatureAndHost(signature, host1);
            assertNotNull(retrievedArbitraryPeerData);

            // And we should also have an entry for the similar peerAddress string with a matching host
            ArbitraryPeerData retrievedArbitraryPeerData2 = repository.getArbitraryRepository()
                    .getArbitraryPeerDataForSignatureAndHost(signature, host2);
            assertNotNull(retrievedArbitraryPeerData2);
        }
    }
}
