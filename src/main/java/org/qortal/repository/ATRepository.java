package org.qortal.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.utils.ByteArray;

public interface ATRepository {

	// CIYAM AutomatedTransactions

	/** Returns ATData using AT's address or null if none found */
	public ATData fromATAddress(String atAddress) throws DataException;

	/** Returns where AT with passed address exists in repository */
	public boolean exists(String atAddress) throws DataException;

	/** Returns AT creator's public key, or null if not found */
	public byte[] getCreatorPublicKey(String atAddress) throws DataException;

	/** Returns list of executable ATs, empty if none found */
	public List<ATData> getAllExecutableATs() throws DataException;

	/** Returns list of ATs with matching code hash, optionally executable only. */
	public List<ATData> getATsByFunctionality(byte[] codeHash, Boolean isExecutable, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/** Returns list of all ATs matching one of passed code hashes, optionally executable only. */
	public List<ATData> getAllATsByFunctionality(Set<ByteArray> codeHashes, Boolean isExecutable) throws DataException;

	/** Returns creation block height given AT's address or null if not found */
	public Integer getATCreationBlockHeight(String atAddress) throws DataException;

	/** Saves ATData into repository */
	public void save(ATData atData) throws DataException;

	/** Removes an AT from repository, including associated ATStateData */
	public void delete(String atAddress) throws DataException;

	// AT States

	/**
	 * Returns ATStateData for an AT at given height.
	 * 
	 * @param atAddress
	 *            - AT's address
	 * @param height
	 *            - block height
	 * @return ATStateData for AT at given height or null if none found
	 */
	public ATStateData getATStateAtHeight(String atAddress, int height) throws DataException;

	/**
	 * Returns latest ATStateData for an AT.
	 * <p>
	 * As ATs don't necessarily run every block, this will return the <tt>ATStateData</tt> with the greatest height.
	 * 
	 * @param atAddress
	 *            - AT's address
	 * @return ATStateData for AT with greatest height or null if none found
	 */
	public ATStateData getLatestATState(String atAddress) throws DataException;

	/**
	 * Returns final ATStateData for ATs matching codeHash (required)
	 * and specific data segment value (optional).
	 * <p>
	 * If searching for specific data segment value, both <tt>dataByteOffset</tt>
	 * and <tt>expectedValue</tt> need to be non-null.
	 * <p>
	 * Note that <tt>dataByteOffset</tt> starts from 0 and will typically be
	 * a multiple of <tt>MachineState.VALUE_SIZE</tt>, which is usually 8:
	 * width of a long.
	 * <p>
	 * Although <tt>expectedValue</tt>, if provided, is natively an unsigned long,
	 * the data segment comparison is done via unsigned hex string.
	 */
	public List<ATStateData> getMatchingFinalATStates(byte[] codeHash, Boolean isFinished,
			Integer dataByteOffset, Long expectedValue, Integer minimumFinalHeight,
			Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns final ATStateData for ATs matching codeHash (required)
	 * and specific data segment value (optional), returning at least
	 * <tt>minimumCount</tt> entries over a span of at least
	 * <tt>minimumPeriod</tt> ms, given enough entries in repository.
	 * <p>
	 * If searching for specific data segment value, both <tt>dataByteOffset</tt>
	 * and <tt>expectedValue</tt> need to be non-null.
	 * <p>
	 * Note that <tt>dataByteOffset</tt> starts from 0 and will typically be
	 * a multiple of <tt>MachineState.VALUE_SIZE</tt>, which is usually 8:
	 * width of a long.
	 * <p>
	 * Although <tt>expectedValue</tt>, if provided, is natively an unsigned long,
	 * the data segment comparison is done via unsigned hex string.
	 */
	public List<ATStateData> getMatchingFinalATStatesQuorum(byte[] codeHash, Boolean isFinished,
			Integer dataByteOffset, Long expectedValue,
			int minimumCount, int maximumCount, long minimumPeriod) throws DataException;

	/**
	 * Returns all ATStateData for a given block height.
	 * <p>
	 * Unlike <tt>getATState</tt>, only returns <i>partial</i> ATStateData saved at the given height.
	 *
	 * @param height
	 *            - block height
	 * @return list of ATStateData for given height, empty list if none found
	 * @throws DataException
	 */
	public List<ATStateData> getBlockATStatesAtHeight(int height) throws DataException;


	/** Rebuild the latest AT states cache, necessary for AT state trimming/pruning.
	 * <p>
	 * NOTE: performs implicit <tt>repository.saveChanges()</tt>.
	 */
	public void rebuildLatestAtStates() throws DataException;


	/** Returns height of first trimmable AT state. */
	public int getAtTrimHeight() throws DataException;

	/** Sets new base height for AT state trimming.
	 * <p>
	 * NOTE: performs implicit <tt>repository.saveChanges()</tt>.
	 */
	public void setAtTrimHeight(int trimHeight) throws DataException;

	/** Trims full AT state data between passed heights. Returns number of trimmed rows. */
	public int trimAtStates(int minHeight, int maxHeight, int limit) throws DataException;


	/** Returns height of first prunable AT state. */
	public int getAtPruneHeight() throws DataException;

	/** Sets new base height for AT state pruning.
	 * <p>
	 * NOTE: performs implicit <tt>repository.saveChanges()</tt>.
	 */
	public void setAtPruneHeight(int pruneHeight) throws DataException;

	/** Prunes full AT state data between passed heights. Returns number of pruned rows. */
	public int pruneAtStates(int minHeight, int maxHeight) throws DataException;


	/** Checks for the presence of the ATStatesHeightIndex in repository */
	public boolean hasAtStatesHeightIndex() throws DataException;


	/**
	 * Save ATStateData into repository.
	 * <p>
	 * Note: Requires at least these <tt>ATStateData</tt> properties to be filled, or an <tt>IllegalArgumentException</tt> will be thrown:
	 * <p>
	 * <ul>
	 * <li><tt>creation</tt></li>
	 * <li><tt>stateHash</tt></li>
	 * <li><tt>height</tt></li>
	 * </ul>
	 * 
	 * @param atStateData
	 * @throws IllegalArgumentException
	 */
	public void save(ATStateData atStateData) throws DataException;

	/** Delete AT's state data at this height */
	public void delete(String atAddress, int height) throws DataException;

	/** Delete state data for all ATs at this height */
	public void deleteATStates(int height) throws DataException;

	// Finding transactions for ATs to process

	static class NextTransactionInfo {
		public final int height;
		public final int sequence;
		public final byte[] signature;

		public NextTransactionInfo(int height, int sequence, byte[] signature) {
			this.height = height;
			this.sequence = sequence;
			this.signature = signature;
		}
	}

	/**
	 * Find next transaction for AT to process.
	 * <p>
	 * @param recipient AT address
	 * @param height starting height
	 * @param sequence starting sequence
	 * @return next transaction info, or null if none found
	 */
	public NextTransactionInfo findNextTransaction(String recipient, int height, int sequence) throws DataException;

	// Other

	public void checkConsistency() throws DataException;

}
