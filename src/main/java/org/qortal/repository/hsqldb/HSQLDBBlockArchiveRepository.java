package org.qortal.repository.hsqldb;

import org.qortal.api.model.BlockSignerSummary;
import org.qortal.data.block.BlockArchiveData;
import org.qortal.data.block.BlockData;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.repository.BlockArchiveReader;
import org.qortal.repository.BlockArchiveRepository;
import org.qortal.repository.DataException;
import org.qortal.transform.block.BlockTransformation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HSQLDBBlockArchiveRepository implements BlockArchiveRepository {

    protected HSQLDBRepository repository;

    public HSQLDBBlockArchiveRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }


    @Override
    public BlockData fromSignature(byte[] signature) throws DataException {
        BlockTransformation blockInfo = BlockArchiveReader.getInstance().fetchBlockWithSignature(signature, this.repository);
        if (blockInfo == null)
            return null;

        return blockInfo.getBlockData();
    }

    @Override
    public int getHeightFromSignature(byte[] signature) throws DataException {
        Integer height = BlockArchiveReader.getInstance().fetchHeightForSignature(signature, this.repository);
        if (height == null || height == 0) {
            return 0;
        }
        return height;
    }

    @Override
    public BlockData fromHeight(int height) throws DataException {
        BlockTransformation blockInfo = BlockArchiveReader.getInstance().fetchBlockAtHeight(height);
        if (blockInfo == null)
            return null;

        return blockInfo.getBlockData();
    }

    @Override
    public List<BlockData> fromRange(int startHeight, int endHeight) throws DataException {
        List<BlockData> blocks = new ArrayList<>();

        for (int height = startHeight; height < endHeight; height++) {
            BlockData blockData = this.fromHeight(height);
            if (blockData == null) {
                return blocks;
            }
            blocks.add(blockData);
        }
        return blocks;
    }

    @Override
    public BlockData fromReference(byte[] reference) throws DataException {
        BlockData referenceBlock = this.repository.getBlockArchiveRepository().fromSignature(reference);
        if (referenceBlock == null) {
            // Try the main block repository. Needed for genesis block.
            referenceBlock = this.repository.getBlockRepository().fromSignature(reference);
        }
        if (referenceBlock != null) {
            int height = referenceBlock.getHeight();
            if (height > 0) {
                // Request the block at height + 1
                BlockTransformation blockInfo = BlockArchiveReader.getInstance().fetchBlockAtHeight(height + 1);
                if (blockInfo != null) {
                    return blockInfo.getBlockData();
                }
            }
        }
        return null;
    }

    @Override
    public int getHeightFromTimestamp(long timestamp) throws DataException {
        String sql = "SELECT height FROM BlockArchive WHERE minted_when <= ? ORDER BY minted_when DESC, height DESC LIMIT 1";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, timestamp)) {
            if (resultSet == null) {
                return 0;
            }
            return resultSet.getInt(1);

        } catch (SQLException e) {
            throw new DataException("Error fetching height from BlockArchive repository", e);
        }
    }

    @Override
    public long getTimestampFromHeight(int height) throws DataException {
        String sql = "SELECT minted_when FROM BlockArchive WHERE height = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, height)) {
            if (resultSet == null)
                return 0;

            return resultSet.getLong(1);
        } catch (SQLException e) {
            throw new DataException("Error obtaining block timestamp by height from BlockArchive repository", e);
        }
    }

    @Override
    public List<BlockSummaryData> getBlockSummariesBySigner(byte[] signerPublicKey, Integer limit, Integer offset, Boolean reverse) throws DataException {
        StringBuilder sql = new StringBuilder(512);
        sql.append("SELECT signature, height, BlockArchive.minter FROM ");

        // List of minter account's public key and reward-share public keys with minter's public key
        sql.append("(SELECT * FROM (VALUES (CAST(? AS QortalPublicKey))) UNION (SELECT reward_share_public_key FROM RewardShares WHERE minter_public_key = ?)) AS PublicKeys (public_key) ");

        // Match BlockArchive blocks signed with public key from above list
        sql.append("JOIN BlockArchive ON BlockArchive.minter = public_key ");

        sql.append("ORDER BY BlockArchive.height ");
        if (reverse != null && reverse)
            sql.append("DESC ");

        HSQLDBRepository.limitOffsetSql(sql, limit, offset);

        List<BlockSummaryData> blockSummaries = new ArrayList<>();

        try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), signerPublicKey, signerPublicKey)) {
            if (resultSet == null)
                return blockSummaries;

            do {
                byte[] signature = resultSet.getBytes(1);
                int height = resultSet.getInt(2);
                byte[] blockMinterPublicKey = resultSet.getBytes(3);

                // Fetch additional info from the archive itself
                Integer onlineAccountsCount = null;
                Long timestamp = null;
                Integer transactionCount = null;
                byte[] reference = null;

                BlockData blockData = this.fromSignature(signature);
                if (blockData != null) {
                    onlineAccountsCount = blockData.getOnlineAccountsCount();
                }

                BlockSummaryData blockSummary = new BlockSummaryData(height, signature, blockMinterPublicKey, onlineAccountsCount, timestamp, transactionCount, reference);
                blockSummaries.add(blockSummary);
            } while (resultSet.next());

            return blockSummaries;
        } catch (SQLException e) {
            throw new DataException("Unable to fetch minter's block summaries from repository", e);
        }
    }

    @Override
    public List<BlockSignerSummary> getBlockSigners(List<String> addresses, Integer limit, Integer offset, Boolean reverse) throws DataException {
        String subquerySql = "SELECT minter, COUNT(signature) FROM (" +
                    "(SELECT minter, signature FROM Blocks) UNION ALL (SELECT minter, signature FROM BlockArchive)" +
                ") GROUP BY minter";

        StringBuilder sql = new StringBuilder(1024);
        sql.append("SELECT DISTINCT block_minter, n_blocks, minter_public_key, minter, recipient FROM (");
        sql.append(subquerySql);
        sql.append(") AS Minters (block_minter, n_blocks) LEFT OUTER JOIN RewardShares ON reward_share_public_key = block_minter ");

        if (addresses != null && !addresses.isEmpty()) {
            sql.append(" LEFT OUTER JOIN Accounts AS BlockMinterAccounts ON BlockMinterAccounts.public_key = block_minter ");
            sql.append(" LEFT OUTER JOIN Accounts AS RewardShareMinterAccounts ON RewardShareMinterAccounts.public_key = minter_public_key ");
            sql.append(" JOIN (VALUES ");

            final int addressesSize = addresses.size();
            for (int ai = 0; ai < addressesSize; ++ai) {
                if (ai != 0)
                    sql.append(", ");

                sql.append("(?)");
            }

            sql.append(") AS FilterAccounts (account) ");
            sql.append(" ON FilterAccounts.account IN (recipient, BlockMinterAccounts.account, RewardShareMinterAccounts.account) ");
        } else {
            addresses = Collections.emptyList();
        }

        sql.append("ORDER BY n_blocks ");
        if (reverse != null && reverse)
            sql.append("DESC ");

        HSQLDBRepository.limitOffsetSql(sql, limit, offset);

        List<BlockSignerSummary> summaries = new ArrayList<>();

        try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), addresses.toArray())) {
            if (resultSet == null)
                return summaries;

            do {
                byte[] blockMinterPublicKey = resultSet.getBytes(1);
                int nBlocks = resultSet.getInt(2);

                // May not be present if no reward-share:
                byte[] mintingAccountPublicKey = resultSet.getBytes(3);
                String minterAccount = resultSet.getString(4);
                String recipientAccount = resultSet.getString(5);

                BlockSignerSummary blockSignerSummary;
                if (recipientAccount == null)
                    blockSignerSummary = new BlockSignerSummary(blockMinterPublicKey, nBlocks);
                else
                    blockSignerSummary = new BlockSignerSummary(blockMinterPublicKey, nBlocks, mintingAccountPublicKey, minterAccount, recipientAccount);

                summaries.add(blockSignerSummary);
            } while (resultSet.next());

            return summaries;
        } catch (SQLException e) {
            throw new DataException("Unable to fetch block minters from repository", e);
        }
    }


    @Override
    public int getBlockArchiveHeight() throws DataException {
        String sql = "SELECT block_archive_height FROM DatabaseInfo";

        try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
            if (resultSet == null)
                return 0;

            return resultSet.getInt(1);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch block archive height from repository", e);
        }
    }

    @Override
    public void setBlockArchiveHeight(int archiveHeight) throws DataException {
        // trimHeightsLock is to prevent concurrent update on DatabaseInfo
        // that could result in "transaction rollback: serialization failure"
        synchronized (this.repository.trimHeightsLock) {
            String updateSql = "UPDATE DatabaseInfo SET block_archive_height = ?";

            try {
                this.repository.executeCheckedUpdate(updateSql, archiveHeight);
                this.repository.saveChanges();
            } catch (SQLException e) {
                repository.examineException(e);
                throw new DataException("Unable to set block archive height in repository", e);
            }
        }
    }


    @Override
    public BlockArchiveData getBlockArchiveDataForSignature(byte[] signature) throws DataException {
        String sql = "SELECT height, signature, minted_when, minter FROM BlockArchive WHERE signature = ? LIMIT 1";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, signature)) {
            if (resultSet == null) {
                return null;
            }
            int height = resultSet.getInt(1);
            byte[] sig = resultSet.getBytes(2);
            long timestamp = resultSet.getLong(3);
            byte[] minterPublicKey = resultSet.getBytes(4);
            return new BlockArchiveData(sig, height, timestamp, minterPublicKey);

        } catch (SQLException e) {
            throw new DataException("Error fetching height from BlockArchive repository", e);
        }
    }


    @Override
    public void save(BlockArchiveData blockArchiveData) throws DataException {
        HSQLDBSaver saveHelper = new HSQLDBSaver("BlockArchive");

        saveHelper.bind("signature", blockArchiveData.getSignature())
                .bind("height", blockArchiveData.getHeight())
                .bind("minted_when", blockArchiveData.getTimestamp())
                .bind("minter", blockArchiveData.getMinterPublicKey());

        try {
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save SimpleBlockData into BlockArchive repository", e);
        }
    }

    @Override
    public void delete(BlockArchiveData blockArchiveData) throws DataException {
        try {
            this.repository.delete("BlockArchive",
                    "block_signature = ?", blockArchiveData.getSignature());
        } catch (SQLException e) {
            throw new DataException("Unable to delete SimpleBlockData from BlockArchive repository", e);
        }
    }

}
