package org.qortal.crosschain;

import org.bitcoinj.crypto.DeterministicKey;
import org.qortal.settings.Settings;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Class BlockchainCache
 *
 * Cache blockchain information to reduce redundant RPCs to the ElectrumX servers.
 */
public class BlockchainCache {

    /**
     * Keys With History
     *
     * Deterministic Keys with any transaction history.
     */
    private Queue<DeterministicKey> keysWithHistory = new ConcurrentLinkedDeque<>();

    /**
     * Transactions By Hash
     *
     * Transaction Hash -> Transaction
     */
    private ConcurrentHashMap<String, BitcoinyTransaction> transactionByHash = new ConcurrentHashMap<>();

    /**
     * Cache Limit
     *
     * If this limit is reached, the cache will be cleared or reduced.
     */
    private static final int CACHE_LIMIT = Settings.getInstance().getBlockchainCacheLimit();

    /**
     * Add Key With History
     *
     * @param key a deterministic key with a verified history
     */
    public void addKeyWithHistory(DeterministicKey key) {

        if( this.keysWithHistory.size() > CACHE_LIMIT ) {
            this.keysWithHistory.remove();
        }

        this.keysWithHistory.add(key);
    }

    /**
     * Key Has History?
     *
     * @param key the deterministic key
     *
     * @return true if the key has a history, otherwise false
     */
    public boolean keyHasHistory( DeterministicKey key ) {
        return this.keysWithHistory.contains(key);
    }

    /**
     * Add Transaction By Hash
     *
     * @param hash the transaction hash
     * @param transaction the transaction
     */
    public void addTransactionByHash( String hash, BitcoinyTransaction transaction ) {

        if( this.transactionByHash.size() > CACHE_LIMIT ) {
            this.transactionByHash.clear();
        }

        this.transactionByHash.put(hash, transaction);
    }

    /**
     * Get Transaction By Hash
     *
     * @param hash the transaction hash
     *
     * @return the transaction, empty if the hash is not in the cache
     */
    public Optional<BitcoinyTransaction> getTransactionByHash( String hash ) {
        return Optional.ofNullable( this.transactionByHash.get(hash) );
    }
}