package org.qortal.test;

import org.apache.commons.io.FileUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.ECKey;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PublicKeyAccount;
import org.qortal.controller.tradebot.LitecoinACCTv1TradeBot;
import org.qortal.controller.tradebot.TradeBot;
import org.qortal.crosschain.Litecoin;
import org.qortal.crosschain.LitecoinACCTv1;
import org.qortal.crosschain.SupportedBlockchain;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.MintingAccountData;
import org.qortal.data.crosschain.TradeBotData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.hsqldb.HSQLDBImportExport;
import org.qortal.settings.Settings;
import org.qortal.test.common.Common;
import org.qortal.utils.NTP;
import org.qortal.utils.Triple;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ImportExportTests extends Common {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
        this.deleteExportDirectory();
    }

    @After
    public void afterTest() throws DataException {
        this.deleteExportDirectory();
    }


    @Test
    public void testExportAndImportTradeBotStates() throws DataException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Ensure no trade bots exist
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // Create some trade bots
            List<TradeBotData> tradeBots = new ArrayList<>();
            for (int i=0; i<10; i++) {
                TradeBotData tradeBotData = this.createTradeBotData(repository);
                repository.getCrossChainRepository().save(tradeBotData);
                tradeBots.add(tradeBotData);
            }

            // Ensure they have been added
            assertEquals(10, repository.getCrossChainRepository().getAllTradeBotData().size());

            // Export them
            HSQLDBImportExport.backupTradeBotStates(repository, null);

            // Delete them from the repository
            for (TradeBotData tradeBotData : tradeBots) {
                repository.getCrossChainRepository().delete(tradeBotData.getTradePrivateKey());
            }

            // Ensure they have been deleted
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // Import them
            Path exportPath = HSQLDBImportExport.getExportDirectory(false);
            Path filePath = Paths.get(exportPath.toString(), "TradeBotStates.json");
            HSQLDBImportExport.importDataFromFile(filePath.toString(), repository);

            // Ensure they have been imported
            assertEquals(10, repository.getCrossChainRepository().getAllTradeBotData().size());

            // Ensure all the data matches
            for (TradeBotData tradeBotData : tradeBots) {
                byte[] tradePrivateKey = tradeBotData.getTradePrivateKey();
                TradeBotData repositoryTradeBotData = repository.getCrossChainRepository().getTradeBotData(tradePrivateKey);
                assertNotNull(repositoryTradeBotData);
                assertEquals(tradeBotData.toJson().toString(), repositoryTradeBotData.toJson().toString());
            }

            repository.saveChanges();
        }
    }

    @Test
    public void testExportAndImportCurrentTradeBotStates() throws DataException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Ensure no trade bots exist
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // Create some trade bots
            List<TradeBotData> tradeBots = new ArrayList<>();
            for (int i=0; i<10; i++) {
                TradeBotData tradeBotData = this.createTradeBotData(repository);
                repository.getCrossChainRepository().save(tradeBotData);
                tradeBots.add(tradeBotData);
            }

            // Ensure they have been added
            assertEquals(10, repository.getCrossChainRepository().getAllTradeBotData().size());

            // Export them
            HSQLDBImportExport.backupTradeBotStates(repository, null);

            // Delete them from the repository
            for (TradeBotData tradeBotData : tradeBots) {
                repository.getCrossChainRepository().delete(tradeBotData.getTradePrivateKey());
            }

            // Ensure they have been deleted
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // Add some more trade bots
            List<TradeBotData> additionalTradeBots = new ArrayList<>();
            for (int i=0; i<5; i++) {
                TradeBotData tradeBotData = this.createTradeBotData(repository);
                repository.getCrossChainRepository().save(tradeBotData);
                additionalTradeBots.add(tradeBotData);
            }

            // Export again
            HSQLDBImportExport.backupTradeBotStates(repository, null);

            // Import current states only
            Path exportPath = HSQLDBImportExport.getExportDirectory(false);
            Path filePath = Paths.get(exportPath.toString(), "TradeBotStates.json");
            HSQLDBImportExport.importDataFromFile(filePath.toString(), repository);

            // Ensure they have been imported
            assertEquals(5, repository.getCrossChainRepository().getAllTradeBotData().size());

            // Ensure that only the additional trade bots have been imported and that the data matches
            for (TradeBotData tradeBotData : additionalTradeBots) {
                byte[] tradePrivateKey = tradeBotData.getTradePrivateKey();
                TradeBotData repositoryTradeBotData = repository.getCrossChainRepository().getTradeBotData(tradePrivateKey);
                assertNotNull(repositoryTradeBotData);
                assertEquals(tradeBotData.toJson().toString(), repositoryTradeBotData.toJson().toString());
            }

            // None of the original trade bots should exist in the repository
            for (TradeBotData tradeBotData : tradeBots) {
                byte[] tradePrivateKey = tradeBotData.getTradePrivateKey();
                TradeBotData repositoryTradeBotData = repository.getCrossChainRepository().getTradeBotData(tradePrivateKey);
                assertNull(repositoryTradeBotData);
            }

            repository.saveChanges();
        }
    }

    @Test
    public void testExportAndImportAllTradeBotStates() throws DataException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Ensure no trade bots exist
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // Create some trade bots
            List<TradeBotData> tradeBots = new ArrayList<>();
            for (int i=0; i<10; i++) {
                TradeBotData tradeBotData = this.createTradeBotData(repository);
                repository.getCrossChainRepository().save(tradeBotData);
                tradeBots.add(tradeBotData);
            }

            // Ensure they have been added
            assertEquals(10, repository.getCrossChainRepository().getAllTradeBotData().size());

            // Export them
            HSQLDBImportExport.backupTradeBotStates(repository, null);

            // Delete them from the repository
            for (TradeBotData tradeBotData : tradeBots) {
                repository.getCrossChainRepository().delete(tradeBotData.getTradePrivateKey());
            }

            // Ensure they have been deleted
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // Add some more trade bots
            List<TradeBotData> additionalTradeBots = new ArrayList<>();
            for (int i=0; i<5; i++) {
                TradeBotData tradeBotData = this.createTradeBotData(repository);
                repository.getCrossChainRepository().save(tradeBotData);
                additionalTradeBots.add(tradeBotData);
            }

            // Export again
            HSQLDBImportExport.backupTradeBotStates(repository, null);

            // Import all states from the archive
            Path exportPath = HSQLDBImportExport.getExportDirectory(false);
            Path filePath = Paths.get(exportPath.toString(), "TradeBotStatesArchive.json");
            HSQLDBImportExport.importDataFromFile(filePath.toString(), repository);

            // Ensure they have been imported
            assertEquals(15, repository.getCrossChainRepository().getAllTradeBotData().size());

            // Ensure that all known trade bots have been imported and that the data matches
            tradeBots.addAll(additionalTradeBots);

            for (TradeBotData tradeBotData : tradeBots) {
                byte[] tradePrivateKey = tradeBotData.getTradePrivateKey();
                TradeBotData repositoryTradeBotData = repository.getCrossChainRepository().getTradeBotData(tradePrivateKey);
                assertNotNull(repositoryTradeBotData);
                assertEquals(tradeBotData.toJson().toString(), repositoryTradeBotData.toJson().toString());
            }

            repository.saveChanges();
        }
    }

    @Test
    public void testExportAndImportLegacyTradeBotStates() throws DataException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Create some trade bots, but don't save them in the repository
            List<TradeBotData> tradeBots = new ArrayList<>();
            for (int i=0; i<10; i++) {
                TradeBotData tradeBotData = this.createTradeBotData(repository);
                tradeBots.add(tradeBotData);
            }

            // Create a legacy format TradeBotStates.json backup file
            this.exportLegacyTradeBotStatesJson(tradeBots);

            // Ensure no trade bots exist in repository
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // Import the legacy format file
            Path exportPath = HSQLDBImportExport.getExportDirectory(false);
            Path filePath = Paths.get(exportPath.toString(), "TradeBotStates.json");
            HSQLDBImportExport.importDataFromFile(filePath.toString(), repository);

            // Ensure they have been imported
            assertEquals(10, repository.getCrossChainRepository().getAllTradeBotData().size());

            for (TradeBotData tradeBotData : tradeBots) {
                byte[] tradePrivateKey = tradeBotData.getTradePrivateKey();
                TradeBotData repositoryTradeBotData = repository.getCrossChainRepository().getTradeBotData(tradePrivateKey);
                assertNotNull(repositoryTradeBotData);
                assertEquals(tradeBotData.toJson().toString(), repositoryTradeBotData.toJson().toString());
            }

            repository.saveChanges();
        }
    }

    @Test
    public void testArchiveTradeBotStateOnTradeFailure() throws DataException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Create a trade bot and save it in the repository
            TradeBotData tradeBotData = this.createTradeBotData(repository);

            // Ensure it doesn't exist in the repository
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // Export trade bot states, passing in the newly created trade bot as an additional parameter
            // This is needed because it hasn't been saved to the db yet
            HSQLDBImportExport.backupTradeBotStates(repository, Arrays.asList(tradeBotData));

            // Ensure it is still not present in the repository
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // Export all local node data again, but this time without including the trade bot data
            // This simulates the behaviour of a node shutdown
            repository.exportNodeLocalData();

            // The TradeBotStates.json file should contain no entries
            Path backupDirectory = HSQLDBImportExport.getExportDirectory(false);
            Path tradeBotStatesBackup = Paths.get(backupDirectory.toString(), "TradeBotStates.json");
            assertTrue(Files.exists(tradeBotStatesBackup));
            String jsonString = new String(Files.readAllBytes(tradeBotStatesBackup));
            Triple<String, String, JSONArray> parsedJSON = HSQLDBImportExport.parseJSONString(jsonString);
            JSONArray tradeBotDataJson = parsedJSON.getC();
            assertTrue(tradeBotDataJson.isEmpty());

            // .. but the TradeBotStatesArchive.json should contain the trade bot data
            Path tradeBotStatesArchiveBackup = Paths.get(backupDirectory.toString(), "TradeBotStatesArchive.json");
            assertTrue(Files.exists(tradeBotStatesArchiveBackup));
            jsonString = new String(Files.readAllBytes(tradeBotStatesArchiveBackup));
            parsedJSON = HSQLDBImportExport.parseJSONString(jsonString);
            JSONObject tradeBotDataJsonObject = (JSONObject) parsedJSON.getC().get(0);
            assertEquals(tradeBotData.toJson().toString(), tradeBotDataJsonObject.toString());

            // Now try importing local data (to simulate a node startup)
            String exportPath = Settings.getInstance().getExportPath();
            Path importPath = Paths.get(exportPath, "TradeBotStates.json");
            repository.importDataFromFile(importPath.toString());

            // The trade should be missing since it's not present in TradeBotStates.json
            assertTrue(repository.getCrossChainRepository().getAllTradeBotData().isEmpty());

            // The user now imports TradeBotStatesArchive.json
            Path archiveImportPath = Paths.get(exportPath, "TradeBotStatesArchive.json");
            repository.importDataFromFile(archiveImportPath.toString());

            // The trade should be present in the database
            assertEquals(1, repository.getCrossChainRepository().getAllTradeBotData().size());

            // The trade bot data in the repository should match the one that was originally created
            byte[] tradePrivateKey = tradeBotData.getTradePrivateKey();
            TradeBotData repositoryTradeBotData = repository.getCrossChainRepository().getTradeBotData(tradePrivateKey);
            assertNotNull(repositoryTradeBotData);
            assertEquals(tradeBotData.toJson().toString(), repositoryTradeBotData.toJson().toString());
        }
    }

    @Test
    public void testExportAndImportMintingAccountData() throws DataException, IOException {
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Ensure no minting accounts exist
            assertTrue(repository.getAccountRepository().getMintingAccounts().isEmpty());

            // Create some minting accounts
            List<MintingAccountData> mintingAccounts = new ArrayList<>();
            for (int i=0; i<10; i++) {
                MintingAccountData mintingAccountData = this.createMintingAccountData();
                repository.getAccountRepository().save(mintingAccountData);
                mintingAccounts.add(mintingAccountData);
            }

            // Ensure they have been added
            assertEquals(10, repository.getAccountRepository().getMintingAccounts().size());

            // Export them
            HSQLDBImportExport.backupMintingAccounts(repository);

            // Delete them from the repository
            for (MintingAccountData mintingAccountData : mintingAccounts) {
                repository.getAccountRepository().delete(mintingAccountData.getPrivateKey());
            }

            // Ensure they have been deleted
            assertTrue(repository.getAccountRepository().getMintingAccounts().isEmpty());

            // Import them
            Path exportPath = HSQLDBImportExport.getExportDirectory(false);
            Path filePath = Paths.get(exportPath.toString(), "MintingAccounts.json");
            HSQLDBImportExport.importDataFromFile(filePath.toString(), repository);

            // Ensure they have been imported
            assertEquals(10, repository.getAccountRepository().getMintingAccounts().size());

            // Ensure all the data matches
            for (MintingAccountData mintingAccountData : mintingAccounts) {
                byte[] privateKey = mintingAccountData.getPrivateKey();
                MintingAccountData repositoryMintingAccountData = repository.getAccountRepository().getMintingAccount(privateKey);
                assertNotNull(repositoryMintingAccountData);
                assertEquals(mintingAccountData.toJson().toString(), repositoryMintingAccountData.toJson().toString());
            }

            repository.saveChanges();
        }
    }


    private TradeBotData createTradeBotData(Repository repository) throws DataException {
        byte[] tradePrivateKey = TradeBot.generateTradePrivateKey();

        byte[] tradeNativePublicKey = TradeBot.deriveTradeNativePublicKey(tradePrivateKey);
        byte[] tradeNativePublicKeyHash = Crypto.hash160(tradeNativePublicKey);
        String tradeNativeAddress = Crypto.toAddress(tradeNativePublicKey);

        byte[] tradeForeignPublicKey = TradeBot.deriveTradeForeignPublicKey(tradePrivateKey);
        byte[] tradeForeignPublicKeyHash = Crypto.hash160(tradeForeignPublicKey);

        String receivingAddress = "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";

        // Convert Litecoin receiving address into public key hash (we only support P2PKH at this time)
        Address litecoinReceivingAddress;
        try {
            litecoinReceivingAddress = Address.fromString(Litecoin.getInstance().getNetworkParameters(), receivingAddress);
        } catch (AddressFormatException e) {
            throw new DataException("Unsupported Litecoin receiving address: " + receivingAddress);
        }

        byte[] litecoinReceivingAccountInfo = litecoinReceivingAddress.getHash();

        byte[] creatorPublicKey = new byte[32];
        PublicKeyAccount creator = new PublicKeyAccount(repository, creatorPublicKey);

        long timestamp = NTP.getTime();
        String atAddress = "AT_ADDRESS";
        long foreignAmount = 1234;
        long qortAmount= 5678;

        TradeBotData tradeBotData =  new TradeBotData(tradePrivateKey, LitecoinACCTv1.NAME,
                LitecoinACCTv1TradeBot.State.BOB_WAITING_FOR_AT_CONFIRM.name(), LitecoinACCTv1TradeBot.State.BOB_WAITING_FOR_AT_CONFIRM.value,
                creator.getAddress(), atAddress, timestamp, qortAmount,
                tradeNativePublicKey, tradeNativePublicKeyHash, tradeNativeAddress,
                null, null,
                SupportedBlockchain.LITECOIN.name(),
                tradeForeignPublicKey, tradeForeignPublicKeyHash,
                foreignAmount, null, null, null, litecoinReceivingAccountInfo);

        return tradeBotData;
    }

    private MintingAccountData createMintingAccountData() {
        // These don't need to be valid keys - just 32 byte strings for the purposes of testing
        byte[] privateKey = new ECKey().getPrivKeyBytes();
        byte[] publicKey = new ECKey().getPrivKeyBytes();

        return new MintingAccountData(privateKey, publicKey);
    }

    private void exportLegacyTradeBotStatesJson(List<TradeBotData> allTradeBotData) throws IOException, DataException {
        JSONArray allTradeBotDataJson = new JSONArray();
        for (TradeBotData tradeBotData : allTradeBotData) {
            JSONObject tradeBotDataJson = tradeBotData.toJson();
            allTradeBotDataJson.put(tradeBotDataJson);
        }

        Path backupDirectory = HSQLDBImportExport.getExportDirectory(true);
        String fileName = Paths.get(backupDirectory.toString(), "TradeBotStates.json").toString();
        FileWriter writer = new FileWriter(fileName);
        writer.write(allTradeBotDataJson.toString());
        writer.close();
    }

    private void deleteExportDirectory() {
        // Delete archive directory if exists
        Path archivePath = Paths.get(Settings.getInstance().getExportPath());
        try {
            FileUtils.deleteDirectory(archivePath.toFile());
        } catch (IOException e) {

        }
    }

}
