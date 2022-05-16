package org.qortal.test.network;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.Test;
import org.qortal.data.network.OnlineAccountData;
import org.qortal.network.message.*;
import org.qortal.transform.Transformer;

import java.nio.ByteBuffer;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OnlineAccountsTests {

    private static final Random RANDOM = new Random();
    static {
        // This must go before any calls to LogManager/Logger
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

        Security.insertProviderAt(new BouncyCastleProvider(), 0);
        Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);
    }


    @Test
    public void testGetOnlineAccountsV2() throws Message.MessageException {
        List<OnlineAccountData> onlineAccountsOut = generateOnlineAccounts(false);

        Message messageOut = new GetOnlineAccountsV2Message(onlineAccountsOut);

        byte[] messageBytes = messageOut.toBytes();
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);

        GetOnlineAccountsV2Message messageIn = (GetOnlineAccountsV2Message) Message.fromByteBuffer(byteBuffer);

        List<OnlineAccountData> onlineAccountsIn = messageIn.getOnlineAccounts();

        assertEquals("size mismatch", onlineAccountsOut.size(), onlineAccountsIn.size());
        assertTrue("accounts mismatch", onlineAccountsIn.containsAll(onlineAccountsOut));

        Message oldMessageOut = new GetOnlineAccountsMessage(onlineAccountsOut);
        byte[] oldMessageBytes = oldMessageOut.toBytes();

        long numTimestamps = onlineAccountsOut.stream().mapToLong(OnlineAccountData::getTimestamp).sorted().distinct().count();

        System.out.println(String.format("For %d accounts split across %d timestamp%s: old size %d vs new size %d",
                onlineAccountsOut.size(),
                numTimestamps,
                numTimestamps != 1 ? "s" : "",
                oldMessageBytes.length,
                messageBytes.length));
    }

    @Test
    public void testOnlineAccountsV2() throws Message.MessageException {
        List<OnlineAccountData> onlineAccountsOut = generateOnlineAccounts(true);

        Message messageOut = new OnlineAccountsV2Message(onlineAccountsOut);

        byte[] messageBytes = messageOut.toBytes();
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);

        OnlineAccountsV2Message messageIn = (OnlineAccountsV2Message) Message.fromByteBuffer(byteBuffer);

        List<OnlineAccountData> onlineAccountsIn = messageIn.getOnlineAccounts();

        assertEquals("size mismatch", onlineAccountsOut.size(), onlineAccountsIn.size());
        assertTrue("accounts mismatch", onlineAccountsIn.containsAll(onlineAccountsOut));

        Message oldMessageOut = new OnlineAccountsMessage(onlineAccountsOut);
        byte[] oldMessageBytes = oldMessageOut.toBytes();

        long numTimestamps = onlineAccountsOut.stream().mapToLong(OnlineAccountData::getTimestamp).sorted().distinct().count();

        System.out.println(String.format("For %d accounts split across %d timestamp%s: old size %d vs new size %d",
                onlineAccountsOut.size(),
                numTimestamps,
                numTimestamps != 1 ? "s" : "",
                oldMessageBytes.length,
                messageBytes.length));
    }

    private List<OnlineAccountData> generateOnlineAccounts(boolean withSignatures) {
        List<OnlineAccountData> onlineAccounts = new ArrayList<>();

        int numTimestamps = RANDOM.nextInt(2) + 1; // 1 or 2

        for (int t = 0; t < numTimestamps; ++t) {
            int numAccounts = RANDOM.nextInt(3000);

            for (int a = 0; a < numAccounts; ++a) {
                byte[] sig = null;
                if (withSignatures) {
                    sig = new byte[Transformer.SIGNATURE_LENGTH];
                    RANDOM.nextBytes(sig);
                }

                byte[] pubkey = new byte[Transformer.PUBLIC_KEY_LENGTH];
                RANDOM.nextBytes(pubkey);

                onlineAccounts.add(new OnlineAccountData(t << 32, sig, pubkey));
            }
        }

        return onlineAccounts;
    }

}
