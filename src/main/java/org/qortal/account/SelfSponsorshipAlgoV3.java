package org.qortal.account;

import org.qortal.api.resource.TransactionsResource;
import org.qortal.asset.Asset;
import org.qortal.data.account.AccountData;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.*;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transaction.Transaction.TransactionType;

import java.util.*;
import java.util.stream.Collectors;

public class SelfSponsorshipAlgoV3 {

    private final Repository repository;
    private final String address;
    private final AccountData accountData;
    private final long snapshotTimestampV1;
    private final long snapshotTimestampV3;
    private final boolean override;

    private int registeredNameCount = 0;
    private int suspiciousCount = 0;
    private int suspiciousPercent = 0;
    private int consolidationCount = 0;
    private int bulkIssuanceCount = 0;
    private int recentSponsorshipCount = 0;

    private List<RewardShareTransactionData> sponsorshipRewardShares = new ArrayList<>();
    private final Map<String, List<TransactionData>> paymentsByAddress = new HashMap<>();
    private final Set<String> sponsees = new LinkedHashSet<>();
    private Set<String> consolidatedAddresses = new LinkedHashSet<>();
    private final Set<String> zeroTransactionAddreses = new LinkedHashSet<>();
    private final Set<String> penaltyAddresses = new LinkedHashSet<>();

    public SelfSponsorshipAlgoV3(Repository repository, String address, long snapshotTimestampV1, long snapshotTimestampV3, boolean override) throws DataException {
        this.repository = repository;
        this.address = address;
        this.accountData = this.repository.getAccountRepository().getAccount(this.address);
        this.snapshotTimestampV1 = snapshotTimestampV1;
        this.snapshotTimestampV3 = snapshotTimestampV3;
        this.override = override;
    }

    public String getAddress() {
        return this.address;
    }

    public Set<String> getPenaltyAddresses() {
        return this.penaltyAddresses;
    }


    public void run() throws DataException {
        if (this.accountData == null) {
            // Nothing to do
            return;
        }

        this.fetchSponsorshipRewardShares();
        if (this.sponsorshipRewardShares.isEmpty()) {
            // Nothing to do
            return;
        }

        this.findConsolidatedRewards();
        this.findBulkIssuance();
        this.findRegisteredNameCount();
        this.findRecentSponsorshipCount();

        int score = this.calculateScore();
        if (score <= 0 && !override) {
            return;
        }

        String newAddress = this.getDestinationAccount(this.address);
        while (newAddress != null) {
            // Found destination account
            this.penaltyAddresses.add(newAddress);

            // Run algo for this address, but in "override" mode because it has already been flagged
            SelfSponsorshipAlgoV3 algoV3 = new SelfSponsorshipAlgoV3(this.repository, newAddress, this.snapshotTimestampV1, this.snapshotTimestampV3, true);
            algoV3.run();
            this.penaltyAddresses.addAll(algoV3.getPenaltyAddresses());

            newAddress = this.getDestinationAccount(newAddress);
        }

        this.penaltyAddresses.add(this.address);

        if (this.override || this.recentSponsorshipCount < 20) {
            this.penaltyAddresses.addAll(this.consolidatedAddresses);
            this.penaltyAddresses.addAll(this.zeroTransactionAddreses);
        }
        else {
            this.penaltyAddresses.addAll(this.sponsees);
        }
    }

    private String getDestinationAccount(String address) throws DataException {
        List<TransactionData> transferPrivsTransactions = fetchTransferPrivsForAddress(address);
        if (transferPrivsTransactions.isEmpty()) {
            // No TRANSFER_PRIVS transactions for this address
            return null;
        }

        AccountData accountData = this.repository.getAccountRepository().getAccount(address);
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

    private void findConsolidatedRewards() throws DataException {
        List<String> sponseesThatSentRewards = new ArrayList<>();
        Map<String, Integer> paymentRecipients = new HashMap<>();

        // Collect outgoing payments of each sponsee
        for (String sponseeAddress : this.sponsees) {

            // Firstly fetch all payments for address, since the functions below depend on this data
            this.fetchPaymentsForAddress(sponseeAddress);

            // Check if the address has zero relevant transactions
            if (this.hasZeroTransactions(sponseeAddress)) {
                this.zeroTransactionAddreses.add(sponseeAddress);
            }

            // Get payment recipients
            List<String> allPaymentRecipients = this.fetchOutgoingPaymentRecipientsForAddress(sponseeAddress);
            if (allPaymentRecipients.isEmpty()) {
                continue;
            }
            sponseesThatSentRewards.add(sponseeAddress);

            List<String> addressesPaidByThisSponsee = new ArrayList<>();
            for (String paymentRecipient : allPaymentRecipients) {
                if (addressesPaidByThisSponsee.contains(paymentRecipient)) {
                    // We already tracked this association - don't allow multiple to stack up
                    continue;
                }
                addressesPaidByThisSponsee.add(paymentRecipient);

                // Increment count for this recipient, or initialize to 1 if not present
                if (paymentRecipients.computeIfPresent(paymentRecipient, (k, v) -> v + 1) == null) {
                    paymentRecipients.put(paymentRecipient, 1);
                }
            }

        }

        // Exclude addresses with a low number of payments
        Map<String, Integer> filteredPaymentRecipients = paymentRecipients.entrySet().stream()
                .filter(p -> p.getValue() != null && p.getValue() >= 10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Now check how many sponsees have sent to this subset of addresses
        Map<String, Integer> sponseesThatConsolidatedRewards = new HashMap<>();
        for (String sponseeAddress : sponseesThatSentRewards) {
            List<String> allPaymentRecipients = this.fetchOutgoingPaymentRecipientsForAddress(sponseeAddress);
            // Remove any that aren't to one of the flagged recipients (i.e. consolidation)
            allPaymentRecipients.removeIf(r -> !filteredPaymentRecipients.containsKey(r));

            int count = allPaymentRecipients.size();
            if (count == 0) {
                continue;
            }
            if (sponseesThatConsolidatedRewards.computeIfPresent(sponseeAddress, (k, v) -> v + count) == null) {
                sponseesThatConsolidatedRewards.put(sponseeAddress, count);
            }
        }

        // Remove sponsees that have only sent a low number of payments to the filtered addresses
        Map<String, Integer> filteredSponseesThatConsolidatedRewards = sponseesThatConsolidatedRewards.entrySet().stream()
                .filter(p -> p.getValue() != null && p.getValue() >= 2)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        this.consolidationCount = sponseesThatConsolidatedRewards.size();
        this.consolidatedAddresses = new LinkedHashSet<>(filteredSponseesThatConsolidatedRewards.keySet());
        this.suspiciousCount = this.consolidationCount + this.zeroTransactionAddreses.size();
        this.suspiciousPercent = (int)(this.suspiciousCount / (float) this.sponsees.size() * 100);
    }

    private void findBulkIssuance() {
        Long lastTimestamp = null;
        for (RewardShareTransactionData rewardShareTransactionData : sponsorshipRewardShares) {
            long timestamp = rewardShareTransactionData.getTimestamp();
            if (timestamp >= this.snapshotTimestampV3) {
                continue;
            }

            if (lastTimestamp != null) {
                if (timestamp - lastTimestamp < 3*60*1000L) {
                    this.bulkIssuanceCount++;
                }
            }
            lastTimestamp = timestamp;
        }
    }

    private void findRegisteredNameCount() throws DataException {
        int registeredNameCount = 0;
        for (String sponseeAddress : sponsees) {
            List<NameData> names = repository.getNameRepository().getNamesByOwner(sponseeAddress);
            for (NameData name : names) {
                if (name.getRegistered() < this.snapshotTimestampV3) {
                    registeredNameCount++;
                    break;
                }
            }
        }
        this.registeredNameCount = registeredNameCount;
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

    private int calculateScore() {
        final int suspiciousMultiplier = (this.suspiciousCount >= 100) ? this.suspiciousPercent : 1;
        final int nameMultiplier = (this.sponsees.size() >= 25 && this.registeredNameCount <= 1) ? 21 :
            (this.sponsees.size() >= 15 && this.registeredNameCount <= 1) ? 11 :
                (this.sponsees.size() >= 5 && this.registeredNameCount <= 1) ? 5 : 1;
        final int consolidationMultiplier = Math.max(this.consolidationCount, 1);
        final int bulkIssuanceMultiplier = Math.max(this.bulkIssuanceCount / 2, 1);
        final int offset = 20;
        return suspiciousMultiplier * nameMultiplier * consolidationMultiplier * bulkIssuanceMultiplier - offset;
    }

    private void fetchSponsorshipRewardShares() throws DataException {
        List<RewardShareTransactionData> sponsorshipRewardShares = new ArrayList<>();

        // Define relevant transactions
        List<TransactionType> txTypes = List.of(TransactionType.REWARD_SHARE);
        List<TransactionData> transactionDataList = fetchTransactions(repository, txTypes, this.address, false);
        transactionDataList.removeIf(t -> t.getTimestamp() <= this.snapshotTimestampV1 || t.getTimestamp() >= this.snapshotTimestampV3);

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
            if (!Arrays.equals(rewardShareTransactionData.getCreatorPublicKey(), accountData.getPublicKey())) {
                continue;
            }

            // Skip self shares
            if (Objects.equals(rewardShareTransactionData.getRecipient(), this.address)) {
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
                this.sponsees.add(rewardShareTransactionData.getRecipient());
            }
        }

        this.sponsorshipRewardShares = sponsorshipRewardShares;
    }

    private List<TransactionData> fetchTransferPrivsForAddress(String address) throws DataException {
        return fetchTransactions(repository,
                List.of(TransactionType.TRANSFER_PRIVS),
                address, true);
    }

    private void fetchPaymentsForAddress(String address) throws DataException {
        List<TransactionData> payments = fetchTransactions(repository,
                Arrays.asList(TransactionType.PAYMENT, TransactionType.TRANSFER_ASSET),
                address, false);
        this.paymentsByAddress.put(address, payments);
    }

    private List<String> fetchOutgoingPaymentRecipientsForAddress(String address) {
        List<String> outgoingPaymentRecipients = new ArrayList<>();

        List<TransactionData> transactionDataList = this.paymentsByAddress.get(address);
        if (transactionDataList == null) transactionDataList = new ArrayList<>();
        transactionDataList.removeIf(t -> t.getTimestamp() <= this.snapshotTimestampV1 || t.getTimestamp() >= this.snapshotTimestampV3);
        for (TransactionData transactionData : transactionDataList) {
            switch (transactionData.getType()) {

                case PAYMENT:
                    PaymentTransactionData paymentTransactionData = (PaymentTransactionData) transactionData;
                    if (!Objects.equals(paymentTransactionData.getRecipient(), address)) {
                        // Outgoing payment from this account
                        outgoingPaymentRecipients.add(paymentTransactionData.getRecipient());
                    }
                    break;

                case TRANSFER_ASSET:
                    TransferAssetTransactionData transferAssetTransactionData = (TransferAssetTransactionData) transactionData;
                    if (transferAssetTransactionData.getAssetId() == Asset.QORT) {
                        if (!Objects.equals(transferAssetTransactionData.getRecipient(), address)) {
                            // Outgoing payment from this account
                            outgoingPaymentRecipients.add(transferAssetTransactionData.getRecipient());
                        }
                    }
                    break;

                default:
                    break;
            }
        }

        return outgoingPaymentRecipients;
    }

    private boolean hasZeroTransactions(String address) {
        List<TransactionData> transactionDataList = this.paymentsByAddress.get(address);
        if (transactionDataList == null) {
            return true;
        }
        transactionDataList.removeIf(t -> t.getTimestamp() <= this.snapshotTimestampV1 || t.getTimestamp() >= this.snapshotTimestampV3);
        return transactionDataList.isEmpty();
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