package org.qortal.test.network;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.Test;
import org.qortal.controller.OnlineAccountsManager;
import org.qortal.data.network.OnlineAccountData;
import org.qortal.network.message.GetOnlineAccountsV3Message;
import org.qortal.network.message.Message;
import org.qortal.network.message.MessageException;
import org.qortal.transform.Transformer;

import java.nio.ByteBuffer;
import java.security.Security;
import java.util.*;

import static org.junit.Assert.*;

public class OnlineAccountsV3Tests {

    private static final Random RANDOM = new Random();
    static {
        // This must go before any calls to LogManager/Logger
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

        Security.insertProviderAt(new BouncyCastleProvider(), 0);
        Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);
    }

    private Map<Long, Map<Byte, byte[]>> convertToHashMaps(List<OnlineAccountData> onlineAccounts) {
        // How many of each timestamp and leading byte (of public key)
        Map<Long, Map<Byte, byte[]>> hashesByTimestampThenByte = new HashMap<>();

        for (OnlineAccountData onlineAccountData : onlineAccounts) {
            Long timestamp = onlineAccountData.getTimestamp();
            Byte leadingByte = onlineAccountData.getPublicKey()[0];

            hashesByTimestampThenByte
                    .computeIfAbsent(timestamp, k -> new HashMap<>())
                    .compute(leadingByte, (k, v) -> OnlineAccountsManager.xorByteArrayInPlace(v, onlineAccountData.getPublicKey()));
        }

        return hashesByTimestampThenByte;
    }

    @Test
    public void testOnGetOnlineAccountsV3() {
        List<OnlineAccountData> ourOnlineAccounts = generateOnlineAccounts(false);
        List<OnlineAccountData> peersOnlineAccounts = generateOnlineAccounts(false);

        Map<Long, Map<Byte, byte[]>> ourConvertedHashes = convertToHashMaps(ourOnlineAccounts);
        Map<Long, Map<Byte, byte[]>> peersConvertedHashes = convertToHashMaps(peersOnlineAccounts);

        List<String> mockReply = new ArrayList<>();

        // Warning: no double-checking/fetching - we must be ConcurrentMap compatible!
        // So no contains()-then-get() or multiple get()s on the same key/map.
        for (var ourOuterMapEntry : ourConvertedHashes.entrySet()) {
            Long timestamp = ourOuterMapEntry.getKey();

            var ourInnerMap = ourOuterMapEntry.getValue();
            var peersInnerMap = peersConvertedHashes.get(timestamp);

            if (peersInnerMap == null) {
                // Peer doesn't have this timestamp, so if it's valid (i.e. not too old) then we'd have to send all of ours
                for (Byte leadingByte : ourInnerMap.keySet())
                    mockReply.add(timestamp + ":" + leadingByte);
            } else {
                // We have entries for this timestamp so compare against peer's entries
                for (var ourInnerMapEntry : ourInnerMap.entrySet()) {
                    Byte leadingByte = ourInnerMapEntry.getKey();
                    byte[] peersHash = peersInnerMap.get(leadingByte);

                    if (!Arrays.equals(ourInnerMapEntry.getValue(), peersHash)) {
                        // We don't match peer, or peer doesn't have - send all online accounts for this timestamp and leading byte
                        mockReply.add(timestamp + ":" + leadingByte);
                    }
                }
            }
        }

        int numOurTimestamps = ourConvertedHashes.size();
        System.out.printf("We have %d accounts split across %d timestamp%s%n",
                ourOnlineAccounts.size(),
                numOurTimestamps,
                numOurTimestamps != 1 ? "s" : ""
        );

        int numPeerTimestamps = peersConvertedHashes.size();
        System.out.printf("Peer sent %d accounts split across %d timestamp%s%n",
                peersOnlineAccounts.size(),
                numPeerTimestamps,
                numPeerTimestamps != 1 ? "s" : ""
        );

        System.out.printf("We need to send: %d%n%s%n", mockReply.size(), String.join(", ", mockReply));
    }

    @Test
    public void testSerialization() throws MessageException {
        List<OnlineAccountData> onlineAccountsOut = generateOnlineAccounts(true);
        Map<Long, Map<Byte, byte[]>> hashesByTimestampThenByteOut = convertToHashMaps(onlineAccountsOut);

        validateSerialization(hashesByTimestampThenByteOut);
    }

    @Test
    public void testEmptySerialization() throws MessageException {
        Map<Long, Map<Byte, byte[]>> hashesByTimestampThenByteOut = Collections.emptyMap();
        validateSerialization(hashesByTimestampThenByteOut);

        hashesByTimestampThenByteOut = new HashMap<>();
        validateSerialization(hashesByTimestampThenByteOut);
    }

    private void validateSerialization(Map<Long, Map<Byte, byte[]>> hashesByTimestampThenByteOut) throws MessageException {
        Message messageOut = new GetOnlineAccountsV3Message(hashesByTimestampThenByteOut);
        byte[] messageBytes = messageOut.toBytes();

        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes).asReadOnlyBuffer();

        GetOnlineAccountsV3Message messageIn = (GetOnlineAccountsV3Message) Message.fromByteBuffer(byteBuffer);

        Map<Long, Map<Byte, byte[]>> hashesByTimestampThenByteIn = messageIn.getHashesByTimestampThenByte();

        Set<Long> timestampsIn = hashesByTimestampThenByteIn.keySet();
        Set<Long> timestampsOut = hashesByTimestampThenByteOut.keySet();
        assertEquals("timestamp count mismatch", timestampsOut.size(), timestampsIn.size());
        assertTrue("timestamps mismatch", timestampsIn.containsAll(timestampsOut));

        for (Long timestamp : timestampsIn) {
            Map<Byte, byte[]> hashesByByteIn = hashesByTimestampThenByteIn.get(timestamp);
            Map<Byte, byte[]> hashesByByteOut = hashesByTimestampThenByteOut.get(timestamp);
            assertNotNull("timestamp entry missing", hashesByByteOut);

            Set<Byte> leadingBytesIn = hashesByByteIn.keySet();
            Set<Byte> leadingBytesOut = hashesByByteOut.keySet();
            assertEquals("leading byte entry count mismatch", leadingBytesOut.size(), leadingBytesIn.size());
            assertTrue("leading byte entry mismatch", leadingBytesIn.containsAll(leadingBytesOut));

            for (Byte leadingByte : leadingBytesOut) {
                byte[] bytesIn = hashesByByteIn.get(leadingByte);
                byte[] bytesOut = hashesByByteOut.get(leadingByte);

                assertTrue("pubkey hash mismatch", Arrays.equals(bytesOut, bytesIn));
            }
        }
    }

    private List<OnlineAccountData> generateOnlineAccounts(boolean withSignatures) {
        List<OnlineAccountData> onlineAccounts = new ArrayList<>();

        int numTimestamps = RANDOM.nextInt(2) + 1; // 1 or 2

        for (int t = 0; t < numTimestamps; ++t) {
            long timestamp = 1 << 31 + (t + 1) << 12;
            int numAccounts = RANDOM.nextInt(3000);

            for (int a = 0; a < numAccounts; ++a) {
                byte[] sig = null;
                if (withSignatures) {
                    sig = new byte[Transformer.SIGNATURE_LENGTH];
                    RANDOM.nextBytes(sig);
                }

                byte[] pubkey = new byte[Transformer.PUBLIC_KEY_LENGTH];
                RANDOM.nextBytes(pubkey);

                Integer nonce = RANDOM.nextInt();

                onlineAccounts.add(new OnlineAccountData(timestamp, sig, pubkey, nonce));
            }
        }

        return onlineAccounts;
    }

}
