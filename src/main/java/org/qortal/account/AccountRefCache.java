package org.qortal.account;

import org.qortal.data.account.AccountData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.utils.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BinaryOperator;

/**
 * Account lastReference caching
 * <p>
 * When checking an account's lastReference, the value returned should be the
 * most recent value set after processing the most recent block.
 * <p>
 * However, when processing a batch of transactions, e.g. during block processing or validation,
 * each transaction needs to check, and maybe update, multiple accounts' lastReference values.
 * <p>
 * Because the intermediate updates would affect future checks, we set up a cache of that
 * maintains a consistent value for fetching lastReference, but also tracks the latest new
 * value, without the overhead of repository calls.
 * <p>
 * Thus, when batch transaction processing is finished, only the latest new lastReference values
 * can be committed to the repository, via {@link AccountRefCache#commit()}.
 * <p>
 * Getting and setting lastReferences values are done the usual way via
 * {@link Account#getLastReference()} and {@link Account#setLastReference(byte[])} which call
 * package-visibility methods in <tt>AccountRefCache</tt>.
 * <p>
 * If {@link Account#getLastReference()} or {@link Account#setLastReference(byte[])} are called
 * outside of caching then lastReference values are fetched/set directly from/to the repository.
 * <p>
 * <tt>AccountRefCache</tt> implements <tt>AutoCloseable</tt> for (typical) use in a try-with-resources block.
 *
 * @see Account#getLastReference()
 * @see Account#setLastReference(byte[])
 * @see org.qortal.block.Block#process()
 */
public class AccountRefCache implements AutoCloseable {

	private static final Map<Repository, RefCache> CACHE = new HashMap<>();

	private static class RefCache {
		private final Map<String, byte[]> getLastReferenceValues = new HashMap<>();
		private final Map<String, Pair<byte[], byte[]>> setLastReferenceValues = new HashMap<>();

		/**
		 * Function for merging publicKey from new data with old publicKey from map.
		 * <p>
		 * Last reference is <tt>A</tt> element in pair.<br>
		 * Public key is <tt>B</tt> element in pair.
		 */
		private static final BinaryOperator<Pair<byte[], byte[]>> mergePublicKey = (oldPair,  newPair) -> {
			// If passed new pair contains non-null publicKey, then we use that one in preference.
			if (newPair.getB() == null)
				// Otherwise, inherit publicKey from old map value.
				newPair.setB(oldPair.getB());

			// We always use new lastReference from new pair.
			return newPair;
		};


		public byte[] getLastReference(Repository repository, String address) throws DataException {
			synchronized (this.getLastReferenceValues) {
				byte[] lastReference = getLastReferenceValues.get(address);
				if (lastReference != null)
					// address is present in map, lastReference not null
					return lastReference;

				// address is present in map, just lastReference is null
				if (getLastReferenceValues.containsKey(address))
					return null;

				lastReference = repository.getAccountRepository().getLastReference(address);
				this.getLastReferenceValues.put(address, lastReference);
				return lastReference;
			}
		}

		public void setLastReference(AccountData accountData) {
			// We're only interested in lastReference and publicKey
			Pair<byte[], byte[]> newPair = new Pair<>(accountData.getReference(), accountData.getPublicKey());

			synchronized (this.setLastReferenceValues) {
				setLastReferenceValues.merge(accountData.getAddress(), newPair, mergePublicKey);
			}
		}

		Map<String, Pair<byte[], byte[]>> getNewLastReferences() {
			return setLastReferenceValues;
		}
	}

	private Repository repository;

	/**
	 * Constructs a new account reference cache, unique to passed <tt>repository</tt> handle.
	 * 
	 * @param repository
	 * @throws IllegalStateException if a cache already exists for <tt>repository</tt>
	 */
	public AccountRefCache(Repository repository) {
		RefCache refCache = new RefCache();

		synchronized (CACHE) {
			if (CACHE.putIfAbsent(repository, refCache) != null)
				throw new IllegalStateException("Account reference cache entry already exists");
		}

		this.repository = repository;
	}

	/**
	 * Save all cached setLastReference account-reference values into repository.
	 * <p>
	 * Closes cache to prevent any future setLastReference() attempts post-commit.
	 * 
	 * @throws DataException
	 */
	public void commit() throws DataException {
		RefCache refCache;

		// Also duplicated in close(), this prevents future setLastReference() attempts post-commit.
		synchronized (CACHE) {
			refCache = CACHE.remove(this.repository);
		}

		if (refCache == null)
			throw new IllegalStateException("Tried to commit non-existent account reference cache");

		Map<String, Pair<byte[], byte[]>> newLastReferenceValues = refCache.getNewLastReferences();

		for (Entry<String, Pair<byte[], byte[]>> entry : newLastReferenceValues.entrySet()) {
			AccountData accountData = new AccountData(entry.getKey());

			accountData.setReference(entry.getValue().getA());

			if (entry.getValue().getB() != null)
				accountData.setPublicKey(entry.getValue().getB());

			this.repository.getAccountRepository().setLastReference(accountData);
		}
	}

	@Override
	public void close() {
		synchronized (CACHE) {
			CACHE.remove(this.repository);
		}
	}

	/**
	 * Returns lastReference value for account.
	 * <p>
	 * If cache is not in effect for passed <tt>repository</tt> handle,
	 * then this method fetches lastReference directly from repository.
	 * <p>
	 * If cache <i>is</i> in effect, then this method returns cached
	 * lastReference, which is <b>not</b> affected by calls to
	 * <tt>setLastReference</tt>.
	 * <p>
	 * Typically called by corresponding method in Account class.
	 * 
	 * @param repository
	 * @param address account's address
	 * @return account's lastReference, or null if account unknown, or lastReference not set
	 * @throws DataException
	 */
	/*package*/ static byte[] getLastReference(Repository repository, String address) throws DataException {
		RefCache refCache;

		synchronized (CACHE) {
			refCache = CACHE.get(repository);
		}

		if (refCache == null)
			return repository.getAccountRepository().getLastReference(address);

		return refCache.getLastReference(repository, address);
	}

	/**
	 * Sets lastReference value for account.
	 * <p>
	 * If cache is not in effect for passed <tt>repository</tt> handle,
	 * then this method sets lastReference directly in repository.
	 * <p>
	 * If cache <i>is</i> in effect, then this method caches the new
	 * lastReference, which is <b>not</b> returned by calls to
	 * <tt>getLastReference</tt>.
	 * <p>
	 * Typically called by corresponding method in Account class.
	 * 
	 * @param repository
	 * @param accountData
	 * @throws DataException
	 */
	/*package*/ static void setLastReference(Repository repository, AccountData accountData) throws DataException {
		RefCache refCache;

		synchronized (CACHE) {
			refCache = CACHE.get(repository);
		}

		if (refCache == null) {
			repository.getAccountRepository().setLastReference(accountData);
			return;
		}

		refCache.setLastReference(accountData);
	}

}
