package org.qortal.test.network;

import com.google.common.primitives.Ints;
import io.druid.extendedset.intset.ConciseSet;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.controller.BlockMinter;
import org.qortal.data.network.OnlineAccountData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.test.common.AccountUtils;
import org.qortal.test.common.Common;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class OnlineAccountsTests extends Common {

    private static final Random RANDOM = new Random();
    static {
        // This must go before any calls to LogManager/Logger
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

        Security.insertProviderAt(new BouncyCastleProvider(), 0);
        Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);
    }

    @Before
    public void beforeTest() throws DataException, IOException {
        Common.useSettingsAndDb("test-settings-v2.json", false);
        NTP.setFixedOffset(Settings.getInstance().getTestNtpOffset());
    }


    @Test
    public void testOnlineAccountsModulusV1() throws IllegalAccessException, DataException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Set feature trigger timestamp to MAX long so that it is inactive
            FieldUtils.writeField(BlockChain.getInstance(), "onlineAccountsModulusV2Timestamp", Long.MAX_VALUE, true);

            List<String> onlineAccountSignatures = new ArrayList<>();
            long fakeNTPOffset = 0L;

            // Mint a block and store its timestamp
            Block block = BlockMinter.mintTestingBlock(repository, Common.getTestAccount(repository, "alice-reward-share"));
            long lastBlockTimestamp = block.getBlockData().getTimestamp();

            // Mint some blocks and keep track of the different online account signatures
            for (int i = 0; i < 30; i++) {
                block = BlockMinter.mintTestingBlock(repository, Common.getTestAccount(repository, "alice-reward-share"));

                // Increase NTP fixed offset by the block time, to simulate time passing
                long blockTimeDelta = block.getBlockData().getTimestamp() - lastBlockTimestamp;
                lastBlockTimestamp = block.getBlockData().getTimestamp();
                fakeNTPOffset += blockTimeDelta;
                NTP.setFixedOffset(fakeNTPOffset);

                String lastOnlineAccountSignatures58 = Base58.encode(block.getBlockData().getOnlineAccountsSignatures());
                if (!onlineAccountSignatures.contains(lastOnlineAccountSignatures58)) {
                    onlineAccountSignatures.add(lastOnlineAccountSignatures58);
                }
            }

            // We expect at least 6 unique signatures over 30 blocks (generally 6-8, but could be higher due to block time differences)
            System.out.println(String.format("onlineAccountSignatures count: %d", onlineAccountSignatures.size()));
            assertTrue(onlineAccountSignatures.size() >= 6);
        }
    }

    @Test
    @Ignore(value = "For informational use")
    public void testOnlineAccountNonceCompression() throws IOException {
        List<OnlineAccountData> onlineAccounts = AccountUtils.generateOnlineAccounts(5000);

        // Build array of nonce values
        List<Integer> accountNonces = new ArrayList<>();
        for (OnlineAccountData onlineAccountData : onlineAccounts) {
            accountNonces.add(onlineAccountData.getNonce());
        }

        // Write nonces into ConciseSet
        ConciseSet nonceSet = new ConciseSet();
        nonceSet = nonceSet.convert(accountNonces);
        byte[] conciseEncodedNonces = nonceSet.toByteBuffer().array();

        // Also write to regular byte array of ints, for comparison
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Integer nonce : accountNonces) {
            bytes.write(Ints.toByteArray(nonce));
        }
        byte[] standardEncodedNonces = bytes.toByteArray();

        System.out.println(String.format("Standard: %d", standardEncodedNonces.length));
        System.out.println(String.format("Concise: %d", conciseEncodedNonces.length));
    }
}
