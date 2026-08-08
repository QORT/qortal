package org.qortal.repository;

import org.qortal.api.model.BlockSignerSummary;
import org.qortal.data.block.BlockArchiveData;
import org.qortal.data.block.BlockData;
import org.qortal.data.block.BlockSummaryData;

import java.util.List;

public interface BlockArchiveRepository {

    /**
     * Returns BlockData from archive using block signature.
     *
     * @param signature
     * @return block data, or null if not found in archive.
     * @throws DataException
     */
    public BlockData fromSignature(byte[] signature) throws DataException;

    /**
     * Return height of block in archive using block's signature.
     *
     * @param signature
     * @return height, or 0 if not found in blockchain.
     * @throws DataException
     */
    public int getHeightFromSignature(byte[] signature) throws DataException;

    /**
     * Returns BlockData from archive using block height.
     *
     * @param height
     * @return block data, or null if not found in blockchain.
     * @throws DataException
     */
    public BlockData fromHeight(int height) throws DataException;

    /**
     * Returns a list of BlockData objects from archive using
     * block height range.
     *
     * @param startHeight
     * @return a list of BlockData objects, or an empty list if
     * not found in blockchain. It is not guaranteed that all
     * requested blocks will be returned.
     * @throws DataException
     */
    public List<BlockData> fromRange(int startHeight, int endHeight) throws DataException;

    /**
     * Returns BlockData from archive using block reference.
     * Currently relies on a child block being the one block
     * higher than its parent. This limitation can be removed
     * by storing the reference in the BlockArchive table, but
     * this has been avoided to reduce space.
     *
     * @param reference
     * @return block data, or null if either parent or child
     * not found in the archive.
     * @throws DataException
     */
    public BlockData fromReference(byte[] reference) throws DataException;

    /**
     * Return height of block with timestamp just before passed timestamp.
     *
     * @param timestamp
     * @return height, or 0 if not found in blockchain.
     * @throws DataException
     */
    public int getHeightFromTimestamp(long timestamp) throws DataException;

    /**
     * Returns block timestamp for a given height.
     *
     * @param height
     * @return timestamp, or 0 if height is out of bounds.
     * @throws DataException
     */
    public long getTimestampFromHeight(int height) throws DataException;

    /**
     * Returns block summaries for blocks signed by passed public key, or reward-share with minter with passed public key.
     */
    public List<BlockSummaryData> getBlockSummariesBySigner(byte[] signerPublicKey, Integer limit, Integer offset, Boolean reverse) throws DataException;

    /**
     * Returns summaries of block signers, optionally limited to passed addresses.
     * This combines both the BlockArchive and the Blocks data into a single result set.
     */
    public List<BlockSignerSummary> getBlockSigners(List<String> addresses, Integer limit, Integer offset, Boolean reverse) throws DataException;


    /** Returns height of first unarchived block. */
    public int getBlockArchiveHeight() throws DataException;

    /** Sets new height for block archiving.
     * <p>
     * NOTE: performs implicit <tt>repository.saveChanges()</tt>.
     */
    public void setBlockArchiveHeight(int archiveHeight) throws DataException;


    /**
     * Returns the block archive data for a given signature, from the block archive.
     * <p>
     * This method will return null if no block archive has been built for the
     * requested signature. In those cases, the height (and other data) can be
     * looked up using the Blocks table. This allows a block to be located in
     * the archive when we only know its signature.
     * <p>
     *
     * @param signature
     * @throws DataException
     */
    public BlockArchiveData getBlockArchiveDataForSignature(byte[] signature) throws DataException;

    /**
     * Saves a block archive entry into the repository.
     * <p>
     * This can be used to find the height of a block by its signature, without
     * having access to the block data itself.
     * <p>
     *
     * @param blockArchiveData
     * @throws DataException
     */
    public void save(BlockArchiveData blockArchiveData) throws DataException;

    /**
     * Deletes a block archive entry from the repository.
     *
     * @param blockArchiveData
     * @throws DataException
     */
    public void delete(BlockArchiveData blockArchiveData) throws DataException;

}
