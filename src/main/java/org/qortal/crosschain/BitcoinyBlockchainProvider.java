package org.qortal.crosschain;

import java.util.List;

public abstract class BitcoinyBlockchainProvider {

	public static final boolean INCLUDE_UNCONFIRMED = true;
	public static final boolean EXCLUDE_UNCONFIRMED = false;

	/** Returns ID unique to bitcoiny network (e.g. "Litecoin-TEST3") */
	public abstract String getNetId();

	/** Returns current blockchain height. */
	public abstract int getCurrentHeight() throws ForeignBlockchainException;

	/** Returns a list of raw block headers, starting at <tt>startHeight</tt> (inclusive), up to <tt>count</tt> max. */
	public abstract List<byte[]> getRawBlockHeaders(int startHeight, int count) throws ForeignBlockchainException;

	/** Returns balance of address represented by <tt>scriptPubKey</tt>. */
	public abstract long getConfirmedBalance(byte[] scriptPubKey) throws ForeignBlockchainException;

	/** Returns raw, serialized, transaction bytes given <tt>txHash</tt>. */
	public abstract byte[] getRawTransaction(String txHash) throws ForeignBlockchainException;

	/** Returns raw, serialized, transaction bytes given <tt>txHash</tt>. */
	public abstract byte[] getRawTransaction(byte[] txHash) throws ForeignBlockchainException;

	/** Returns unpacked transaction given <tt>txHash</tt>. */
	public abstract BitcoinyTransaction getTransaction(String txHash) throws ForeignBlockchainException;

	/** Returns list of transaction hashes (and heights) for address represented by <tt>scriptPubKey</tt>, optionally including unconfirmed transactions. */
	public abstract List<TransactionHash> getAddressTransactions(byte[] scriptPubKey, boolean includeUnconfirmed) throws ForeignBlockchainException;

	/** Returns list of unspent transaction outputs for address represented by <tt>scriptPubKey</tt>, optionally including unconfirmed transactions. */
	public abstract List<UnspentOutput> getUnspentOutputs(byte[] scriptPubKey, boolean includeUnconfirmed) throws ForeignBlockchainException;

	/** Broadcasts raw, serialized, transaction bytes to network, returning success/failure. */
	public abstract void broadcastTransaction(byte[] rawTransaction) throws ForeignBlockchainException;

}
