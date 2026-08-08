package org.qortal.account;

import org.qortal.api.resource.TransactionsResource;
import org.qortal.asset.Asset;
import org.qortal.block.BlockChain;
import org.qortal.data.account.AccountData;
import org.qortal.data.transaction.*;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transaction.Transaction.TransactionType;

import java.util.*;

public class SelfSponsorshipAlgoV2 {

    private final long snapshotTimestampV1 = BlockChain.getInstance().getSelfSponsorshipAlgoV1SnapshotTimestamp();
    private final long snapshotTimestampV2 = BlockChain.getInstance().getSelfSponsorshipAlgoV2SnapshotTimestamp();
    private final long referenceTimestamp = BlockChain.getInstance().getReferenceTimestampBlock();

    private final boolean override;
    private final Repository repository;
    private final String address;

    private int recentAssetSendCount  = 0;
    private int recentSponsorshipCount = 0;

    private final Set<String> assetAddresses = new LinkedHashSet<>();
    private final Set<String> penaltyAddresses = new LinkedHashSet<>();
    private final Set<String> sponsorAddresses = new LinkedHashSet<>();

    private List<RewardShareTransactionData> sponsorshipRewardShares = new ArrayList<>();
    private List<TransferAssetTransactionData> transferAssetForAddress = new ArrayList<>();

    public SelfSponsorshipAlgoV2(Repository repository, String address, boolean override) {
        this.repository = repository;
        this.address = address;
        this.override = override;
    }

    public String getAddress() {
        return this.address;
    }

    public Set<String> getPenaltyAddresses() {
        return this.penaltyAddresses;
    }

    public void run() throws DataException {
        if (!override) {
            this.getAccountPrivs(this.address);
        }

        if (override) {
            this.fetchTransferAssetForAddress(this.address);
            this.findRecentAssetSendCount();

            if (this.recentAssetSendCount >= 6) {
                this.penaltyAddresses.add(this.address);
                this.penaltyAddresses.addAll(this.assetAddresses);
            }
        }
    }

    private void getAccountPrivs(String address) throws DataException {
        AccountData accountData = this.repository.getAccountRepository().getAccount(address);
        List<TransactionData> transferPrivsTransactions = fetchTransferPrivsForAddress(address);
        transferPrivsTransactions.removeIf(t -> t.getTimestamp() > this.referenceTimestamp || accountData.getAddress().equals(t.getRecipient()));

        if (transferPrivsTransactions.isEmpty()) {
            // Nothing to do
            return;
        }

        for (TransactionData transactionData : transferPrivsTransactions) {
            TransferPrivsTransactionData transferPrivsTransactionData = (TransferPrivsTransactionData) transactionData;
            this.penaltyAddresses.add(transferPrivsTransactionData.getRecipient());
            this.fetchSponsorshipRewardShares(transferPrivsTransactionData.getRecipient());
            this.findRecentSponsorshipCount();

            if (this.recentSponsorshipCount >= 1) {
                this.penaltyAddresses.addAll(this.sponsorAddresses);
            }

            String newAddress = this.getDestinationAccount(transferPrivsTransactionData.getRecipient());

            while (newAddress != null) {
                // Found destination account
                this.penaltyAddresses.add(newAddress);
                this.fetchSponsorshipRewardShares(newAddress);
                this.findRecentSponsorshipCount();

                if (this.recentSponsorshipCount >= 1) {
                    this.penaltyAddresses.addAll(this.sponsorAddresses);
                }

                newAddress = this.getDestinationAccount(newAddress);
            }
        }
    }

    private String getDestinationAccount(String address) throws DataException {
        AccountData accountData = this.repository.getAccountRepository().getAccount(address);
        List<TransactionData> transferPrivsTransactions = fetchTransferPrivsForAddress(address);
        transferPrivsTransactions.removeIf(t -> t.getTimestamp() > this.referenceTimestamp || accountData.getAddress().equals(t.getRecipient()));

        if (transferPrivsTransactions.isEmpty()) {
            return null;
        }

        if (accountData == null) {
            return null;
        }

        for (TransactionData transactionData : transferPrivsTransactions) {
            TransferPrivsTransactionData transferPrivsTransactionData = (TransferPrivsTransactionData) transactionData;
            if (Arrays.equals(transferPrivsTransactionData.getSenderPublicKey(), accountData.getPublicKey())) {
                return transferPrivsTransactionData.getRecipient();
            }
        }

        return null;
    }

    private void fetchSponsorshipRewardShares(String address) throws DataException {
        AccountData accountDataRs = this.repository.getAccountRepository().getAccount(address);
        List<RewardShareTransactionData> sponsorshipRewardShares = new ArrayList<>();

        // Define relevant transactions
        List<TransactionType> txTypes = List.of(TransactionType.REWARD_SHARE);
        List<TransactionData> transactionDataList = fetchTransactions(repository, txTypes, address, false);

        for (TransactionData transactionData : transactionDataList) {
            if (transactionData.getType() != TransactionType.REWARD_SHARE) {
                continue;
            }

            RewardShareTransactionData rewardShareTransactionData = (RewardShareTransactionData) transactionData;

            // Skip removals
            if (rewardShareTransactionData.getSharePercent() < 0) {
                continue;
            }

            // Skip if not sponsored by this account
            if (!Arrays.equals(rewardShareTransactionData.getCreatorPublicKey(), accountDataRs.getPublicKey())) {
                continue;
            }

            // Skip self shares
            if (Objects.equals(rewardShareTransactionData.getRecipient(), address)) {
                continue;
            }

            boolean duplicateFound = false;
            for (RewardShareTransactionData existingRewardShare : sponsorshipRewardShares) {
                if (Objects.equals(existingRewardShare.getRecipient(), rewardShareTransactionData.getRecipient())) {
                    // Duplicate
                    duplicateFound = true;
                    break;
                }
            }

            if (!duplicateFound) {
                sponsorshipRewardShares.add(rewardShareTransactionData);
                this.sponsorAddresses.add(rewardShareTransactionData.getRecipient());
            }
        }

        this.sponsorshipRewardShares = sponsorshipRewardShares;
    }

    private void fetchTransferAssetForAddress(String address) throws DataException {
        List<TransferAssetTransactionData> transferAssetForAddress = new ArrayList<>();

        // Define relevant transactions
        List<TransactionType> txTypes = List.of(TransactionType.TRANSFER_ASSET);
        List<TransactionData> transactionDataList = fetchTransactions(repository, txTypes, address, false);
        transactionDataList.removeIf(t -> t.getTimestamp() <= this.snapshotTimestampV1 || t.getTimestamp() >= this.snapshotTimestampV2);

        for (TransactionData transactionData : transactionDataList) {
            if (transactionData.getType() != TransactionType.TRANSFER_ASSET) {
                continue;
            }

            TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) transactionData;

            if (transferAssetTransactionData.getAssetId() == Asset.QORT) {
                if (!Objects.equals(transferAssetTransactionData.getRecipient(), address)) {
                    // Outgoing transfer asset for this account
                    transferAssetForAddress.add(transferAssetTransactionData);
                    this.assetAddresses.add(transferAssetTransactionData.getRecipient());
                }
            }
        }

        this.transferAssetForAddress = transferAssetForAddress;
    }

    private void findRecentSponsorshipCount() {
        int recentSponsorshipCount = 0;

        for (RewardShareTransactionData rewardShare : sponsorshipRewardShares) {
            if (rewardShare.getTimestamp() >= this.snapshotTimestampV1) {
                recentSponsorshipCount++;
            }
        }

        this.recentSponsorshipCount = recentSponsorshipCount;
    }

    private void findRecentAssetSendCount() {
        int recentAssetSendCount = 0;

        for (TransferAssetTransactionData assetSend : transferAssetForAddress) {
            if (assetSend.getTimestamp() >= this.snapshotTimestampV1) {
                recentAssetSendCount++;
            }
        }

        this.recentAssetSendCount = recentAssetSendCount;
    }

    private List<TransactionData> fetchTransferPrivsForAddress(String address) throws DataException {
        return fetchTransactions(repository,
                List.of(TransactionType.TRANSFER_PRIVS),
                address, true);
    }

    private static List<TransactionData> fetchTransactions(Repository repository, List<TransactionType> txTypes, String address, boolean reverse) throws DataException {
        // Fetch all relevant transactions for this account
        List<byte[]> signatures = repository.getTransactionRepository()
                .getSignaturesMatchingCriteria(null, null, null, txTypes,
                        null, null, address, TransactionsResource.ConfirmationStatus.CONFIRMED,
                        null, null, reverse);

        List<TransactionData> transactionDataList = new ArrayList<>();

        for (byte[] signature : signatures) {
            // Fetch transaction data
            TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
            if (transactionData == null) {
                continue;
            }
            transactionDataList.add(transactionData);
        }

        return transactionDataList;
    }
}