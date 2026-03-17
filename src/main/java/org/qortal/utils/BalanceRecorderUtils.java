package org.qortal.utils;

import org.qortal.block.Block;
import org.qortal.crypto.Crypto;
import org.qortal.data.PaymentData;
import org.qortal.data.account.AccountBalanceData;
import org.qortal.data.account.AddressAmountData;
import org.qortal.data.account.BlockHeightRange;
import org.qortal.data.account.BlockHeightRangeAddressAmounts;
import org.qortal.data.transaction.ATTransactionData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.BuyNameTransactionData;
import org.qortal.data.transaction.CreateAssetOrderTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.data.transaction.MultiPaymentTransactionData;
import org.qortal.data.transaction.PaymentTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.TransferAssetTransactionData;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BalanceRecorderUtils {

    public static final Predicate<AddressAmountData> ADDRESS_AMOUNT_DATA_NOT_ZERO = addressAmount -> addressAmount.getAmount() != 0;
    public static final Comparator<BlockHeightRangeAddressAmounts> BLOCK_HEIGHT_RANGE_ADDRESS_AMOUNTS_COMPARATOR = new Comparator<BlockHeightRangeAddressAmounts>() {
        @Override
        public int compare(BlockHeightRangeAddressAmounts amounts1, BlockHeightRangeAddressAmounts amounts2) {
            return amounts1.getRange().getEnd() - amounts2.getRange().getEnd();
        }
    };

    public static final Comparator<AddressAmountData> ADDRESS_AMOUNT_DATA_COMPARATOR = new Comparator<AddressAmountData>() {
        @Override
        public int compare(AddressAmountData addressAmountData, AddressAmountData t1) {
            if( addressAmountData.getAmount() > t1.getAmount() ) {
                return 1;
            }
            else if( addressAmountData.getAmount() < t1.getAmount() ) {
                return -1;
            }
            else {
                return 0;
            }
        }
    };

    public static final Comparator<BlockHeightRange> BLOCK_HEIGHT_RANGE_COMPARATOR = new Comparator<BlockHeightRange>() {
        @Override
        public int compare(BlockHeightRange range1, BlockHeightRange range2) {
            return range1.getEnd() - range2.getEnd();
        }
    };

    /**
     * Build Balance Dynmaics For Account
     *
     * @param priorBalances the balances prior to the current height, assuming only one balance per address
     * @param accountBalance the current balance
     *
     * @return the difference between the current balance and the prior balance for the current balance address
     */
    public static AddressAmountData buildBalanceDynamicsForAccount(List<AccountBalanceData> priorBalances, AccountBalanceData accountBalance) {
        Optional<AccountBalanceData> matchingAccountPriorBalance
            = priorBalances.stream()
                .filter(priorBalance -> accountBalance.getAddress().equals(priorBalance.getAddress()))
                .findFirst();
        if(matchingAccountPriorBalance.isPresent()) {
            return new AddressAmountData(accountBalance.getAddress(), accountBalance.getBalance() - matchingAccountPriorBalance.get().getBalance());
        }
        else {
            return new AddressAmountData(accountBalance.getAddress(), accountBalance.getBalance());
        }
    }

    public static List<AddressAmountData> buildBalanceDynamics(
            final List<AccountBalanceData> balances,
            final List<AccountBalanceData> priorBalances,
            long minimum,
            List<TransactionData> transactions) {

        Map<String, Long> amountsByAddress = new HashMap<>(transactions.size());

        for( TransactionData transactionData : transactions ) {

            mapBalanceModificationsForTransaction(amountsByAddress, transactionData);
        }

        List<AddressAmountData> addressAmounts
            = balances.stream()
                .map(balance -> buildBalanceDynamicsForAccount(priorBalances, balance))
                .map( data -> adjustAddressAmount(amountsByAddress.getOrDefault(data.getAddress(), 0L), data))
                .filter(ADDRESS_AMOUNT_DATA_NOT_ZERO)
                .filter(data -> data.getAmount() >= minimum)
                .collect(Collectors.toList());

        return addressAmounts;
    }

    public static AddressAmountData adjustAddressAmount(long adjustment, AddressAmountData data) {

        return new AddressAmountData(data.getAddress(), data.getAmount() - adjustment);
    }

    public static void mapBalanceModificationsForTransaction(Map<String, Long> amountsByAddress, TransactionData transactionData) {
        String creatorAddress;

        // AT Transaction
        if( transactionData instanceof ATTransactionData) {
            creatorAddress = mapBalanceModificationsForAtTransaction(amountsByAddress, (ATTransactionData) transactionData);
        }
        // Buy Name Transaction
        else if( transactionData instanceof BuyNameTransactionData) {
            creatorAddress = mapBalanceModificationsForBuyNameTransaction(amountsByAddress, (BuyNameTransactionData) transactionData);
        }
        // Create Asset Order Transaction
        else if( transactionData instanceof CreateAssetOrderTransactionData) {
            //TODO I'm not sure how to handle this one. This hasn't been used at this point in the blockchain.

            creatorAddress = Crypto.toAddress(transactionData.getCreatorPublicKey());
        }
        // Deploy AT Transaction
        else if( transactionData instanceof DeployAtTransactionData ) {
            creatorAddress = mapBalanceModificationsForDeployAtTransaction(amountsByAddress, (DeployAtTransactionData) transactionData);
        }
        // Multi Payment Transaction
        else if( transactionData instanceof MultiPaymentTransactionData) {
            creatorAddress = mapBalanceModificationsForMultiPaymentTransaction(amountsByAddress, (MultiPaymentTransactionData) transactionData);
        }
        // Payment Transaction
        else if( transactionData instanceof  PaymentTransactionData ) {
            creatorAddress = mapBalanceModicationsForPaymentTransaction(amountsByAddress, (PaymentTransactionData) transactionData);
        }
        // Transfer Asset Transaction
        else if( transactionData instanceof TransferAssetTransactionData) {
            creatorAddress = mapBalanceModificationsForTransferAssetTransaction(amountsByAddress, (TransferAssetTransactionData) transactionData);
        }
        // Other Transactions
        else {
            creatorAddress = Crypto.toAddress(transactionData.getCreatorPublicKey());
        }

        // all transactions modify the balance for fees
        mapBalanceModifications(amountsByAddress, transactionData.getFee(), creatorAddress, Optional.empty());
    }

    public static String mapBalanceModificationsForTransferAssetTransaction(Map<String, Long> amountsByAddress, TransferAssetTransactionData transferAssetData) {
        String creatorAddress = Crypto.toAddress(transferAssetData.getSenderPublicKey());

        if( transferAssetData.getAssetId() == 0) {
            mapBalanceModifications(
                    amountsByAddress,
                    transferAssetData.getAmount(),
                    creatorAddress,
                    Optional.of(transferAssetData.getRecipient())
            );
        }
        return creatorAddress;
    }

    public static String mapBalanceModicationsForPaymentTransaction(Map<String, Long> amountsByAddress, PaymentTransactionData paymentData) {
        String creatorAddress = Crypto.toAddress(paymentData.getCreatorPublicKey());

        mapBalanceModifications(amountsByAddress,
            paymentData.getAmount(),
            creatorAddress,
            Optional.of(paymentData.getRecipient())
        );
        return creatorAddress;
    }

    public static String mapBalanceModificationsForMultiPaymentTransaction(Map<String, Long> amountsByAddress, MultiPaymentTransactionData multiPaymentData) {
        String creatorAddress = Crypto.toAddress(multiPaymentData.getCreatorPublicKey());

        for(PaymentData payment : multiPaymentData.getPayments() ) {
            mapBalanceModificationsForTransaction(
                amountsByAddress,
                getPaymentTransactionData(multiPaymentData, payment)
            );
        }
        return creatorAddress;
    }

    public static String mapBalanceModificationsForDeployAtTransaction(Map<String, Long> amountsByAddress, DeployAtTransactionData transactionData) {
        String creatorAddress;
        DeployAtTransactionData deployAtData = transactionData;

        creatorAddress = Crypto.toAddress(deployAtData.getCreatorPublicKey());

        if( deployAtData.getAssetId() == 0 ) {
            mapBalanceModifications(
                    amountsByAddress,
                    deployAtData.getAmount(),
                    creatorAddress,
                    Optional.of(deployAtData.getAtAddress())
            );
        }
        return creatorAddress;
    }

    public static String mapBalanceModificationsForBuyNameTransaction(Map<String, Long> amountsByAddress, BuyNameTransactionData transactionData) {
        String creatorAddress;
        BuyNameTransactionData buyNameData = transactionData;

        creatorAddress = Crypto.toAddress(buyNameData.getCreatorPublicKey());

        mapBalanceModifications(
                amountsByAddress,
            buyNameData.getAmount(),
            creatorAddress,
            Optional.of(buyNameData.getSeller())
        );
        return creatorAddress;
    }

    public static String mapBalanceModificationsForAtTransaction(Map<String, Long> amountsByAddress, ATTransactionData transactionData) {
        String creatorAddress;
        ATTransactionData atData = transactionData;
        creatorAddress = atData.getATAddress();

        if( atData.getAssetId() != null && atData.getAssetId() == 0) {
            mapBalanceModifications(
                    amountsByAddress,
                    atData.getAmount(),
                    creatorAddress,
                    Optional.of(atData.getRecipient())
            );
        }
        return creatorAddress;
    }

    public static PaymentTransactionData getPaymentTransactionData(MultiPaymentTransactionData multiPaymentData, PaymentData payment) {
        return new PaymentTransactionData(
                new BaseTransactionData(
                        multiPaymentData.getTimestamp(),
                        multiPaymentData.getTxGroupId(),
                        multiPaymentData.getReference(),
                        multiPaymentData.getCreatorPublicKey(),
                        0L,
                        multiPaymentData.getSignature()
                ),
                payment.getRecipient(),
                payment.getAmount()
        );
    }

    public static void mapBalanceModifications(Map<String, Long> amountsByAddress, Long amount, String sender, Optional<String> recipient) {
        amountsByAddress.put(
                sender,
            amountsByAddress.getOrDefault(sender, 0L) - amount
        );

        if( recipient.isPresent() )
            amountsByAddress.put(
                recipient.get(),
                amountsByAddress.getOrDefault(recipient.get(), 0L) + amount
        );
    }

    public static void removeRecordingsAboveHeight(int currentHeight, ConcurrentHashMap<Integer, List<AccountBalanceData>> balancesByHeight) {
        balancesByHeight.entrySet().stream()
            .filter(heightWithBalances -> heightWithBalances.getKey() > currentHeight)
            .forEach(heightWithBalances -> balancesByHeight.remove(heightWithBalances.getKey()));
    }

    public static void removeRecordingsBelowHeight(int currentHeight, ConcurrentHashMap<Integer, List<AccountBalanceData>> balancesByHeight) {
        balancesByHeight.entrySet().stream()
            .filter(heightWithBalances -> heightWithBalances.getKey() < currentHeight)
            .forEach(heightWithBalances -> balancesByHeight.remove(heightWithBalances.getKey()));
    }

    public static void removeDynamicsOnOrAboveHeight(int currentHeight, CopyOnWriteArrayList<BlockHeightRangeAddressAmounts> balanceDynamics) {
        balanceDynamics.stream()
            .filter(addressAmounts -> addressAmounts.getRange().getEnd() >= currentHeight)
            .forEach(addressAmounts -> balanceDynamics.remove(addressAmounts));
    }

    public static BlockHeightRangeAddressAmounts removeOldestDynamics(CopyOnWriteArrayList<BlockHeightRangeAddressAmounts> balanceDynamics) {
        BlockHeightRangeAddressAmounts oldestDynamics
                = balanceDynamics.stream().sorted(BLOCK_HEIGHT_RANGE_ADDRESS_AMOUNTS_COMPARATOR).findFirst().get();

        balanceDynamics.remove(oldestDynamics);
        return oldestDynamics;
    }

    public static Optional<Integer> getPriorHeight(int currentHeight, ConcurrentHashMap<Integer, List<AccountBalanceData>> balancesByHeight) {
        Optional<Integer> priorHeight
            = balancesByHeight.keySet().stream()
                .filter(height -> height < currentHeight)
                .sorted(Comparator.reverseOrder()).findFirst();
        return priorHeight;
    }

    /**
     * Is Reward Distribution Range?
     *
     * @param start start height, exclusive
     * @param end end height, inclusive
     *
     * @return true there is a reward distribution block within this block range
     */
    public static boolean isRewardDistributionRange(int start, int end) {

        // iterate through the block height until a reward distribution block or the end of the range
        for( int i = start + 1; i <= end; i++) {
            if( Block.isRewardDistributionBlock(i) ) return true;
        }

        // no reward distribution blocks found within range
        return false;
    }
}
