package org.qortal.block;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.account.SelfSponsorshipAlgoV3;
import org.qortal.api.model.AccountPenaltyStats;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.AccountData;
import org.qortal.data.account.AccountPenaltyData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.utils.Base58;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Self Sponsorship AlgoV3 Block
 * <p>
 * Selected block for the initial run on the "self sponsorship detection algorithm"
 */
public final class SelfSponsorshipAlgoV3Block {

	private static final Logger LOGGER = LogManager.getLogger(SelfSponsorshipAlgoV3Block.class);

	private SelfSponsorshipAlgoV3Block() {
		/* Do not instantiate */
	}

	public static void processAccountPenalties(Block block) throws DataException {
		LOGGER.info("Process Self Sponsorship Algo V3 - this will take a while...");
		logPenaltyStats(block.repository);
		long startTime = System.currentTimeMillis();
		Set<AccountPenaltyData> penalties = getAccountPenalties(block.repository, -5000000);
		block.repository.getAccountRepository().updateBlocksMintedPenalties(penalties);
		long totalTime = System.currentTimeMillis() - startTime;
		String hash = getHash(penalties.stream().map(p -> p.getAddress()).collect(Collectors.toList()));
		LOGGER.info("{} penalty addresses processed (hash: {}). Total time taken: {} seconds", penalties.size(), hash, (int)(totalTime / 1000.0f));
		logPenaltyStats(block.repository);

		int updatedCount = updateAccountLevels(block.repository, penalties);
		LOGGER.info("Account levels updated for {} penalty addresses", updatedCount);
	}

	public static void orphanAccountPenalties(Block block) throws DataException {
		LOGGER.info("Orphan Self Sponsorship Algo V3 - this will take a while...");
		logPenaltyStats(block.repository);
		long startTime = System.currentTimeMillis();
		Set<AccountPenaltyData> penalties = getAccountPenalties(block.repository, 5000000);
		block.repository.getAccountRepository().updateBlocksMintedPenalties(penalties);
		long totalTime = System.currentTimeMillis() - startTime;
		String hash = getHash(penalties.stream().map(p -> p.getAddress()).collect(Collectors.toList()));
		LOGGER.info("{} penalty addresses orphaned (hash: {}). Total time taken: {} seconds", penalties.size(), hash, (int)(totalTime / 1000.0f));
		logPenaltyStats(block.repository);

		int updatedCount = updateAccountLevels(block.repository, penalties);
		LOGGER.info("Account levels updated for {} penalty addresses", updatedCount);
	}

	public static Set<AccountPenaltyData> getAccountPenalties(Repository repository, int penalty) throws DataException {
		final long snapshotTimestampV1 = BlockChain.getInstance().getSelfSponsorshipAlgoV1SnapshotTimestamp();
		final long snapshotTimestampV3 = BlockChain.getInstance().getSelfSponsorshipAlgoV3SnapshotTimestamp();
		Set<AccountPenaltyData> penalties = new LinkedHashSet<>();
		List<String> addresses = repository.getTransactionRepository().getConfirmedRewardShareCreatorsExcludingSelfShares();
		for (String address : addresses) {
			//System.out.println(String.format("address: %s", address));
			SelfSponsorshipAlgoV3 selfSponsorshipAlgoV3 = new SelfSponsorshipAlgoV3(repository, address, snapshotTimestampV1, snapshotTimestampV3, false);
			selfSponsorshipAlgoV3.run();
			//System.out.println(String.format("Penalty addresses: %d", selfSponsorshipAlgoV3.getPenaltyAddresses().size()));

			for (String penaltyAddress : selfSponsorshipAlgoV3.getPenaltyAddresses()) {
				penalties.add(new AccountPenaltyData(penaltyAddress, penalty));
			}
		}
		return penalties;
	}

	private static int updateAccountLevels(Repository repository, Set<AccountPenaltyData> accountPenalties) throws DataException {
		final List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
		final int maximumLevel = cumulativeBlocksByLevel.size() - 1;

		int updatedCount = 0;

		for (AccountPenaltyData penaltyData : accountPenalties) {
			AccountData accountData = repository.getAccountRepository().getAccount(penaltyData.getAddress());
			final int effectiveBlocksMinted = accountData.getBlocksMinted() + accountData.getBlocksMintedAdjustment() + accountData.getBlocksMintedPenalty();

			// Shortcut for penalties
			if (effectiveBlocksMinted < 0) {
				accountData.setLevel(0);
				repository.getAccountRepository().setLevel(accountData);
				updatedCount++;
				LOGGER.trace(() -> String.format("Block minter %s dropped to level %d", accountData.getAddress(), accountData.getLevel()));
				continue;
			}

			for (int newLevel = maximumLevel; newLevel >= 0; --newLevel) {
				if (effectiveBlocksMinted >= cumulativeBlocksByLevel.get(newLevel)) {
					accountData.setLevel(newLevel);
					repository.getAccountRepository().setLevel(accountData);
					updatedCount++;
					LOGGER.trace(() -> String.format("Block minter %s increased to level %d", accountData.getAddress(), accountData.getLevel()));
					break;
				}
			}
		}

		return updatedCount;
	}

	private static void logPenaltyStats(Repository repository) {
		try {
			LOGGER.info(getPenaltyStats(repository));

		} catch (DataException e) {}
	}

	private static AccountPenaltyStats getPenaltyStats(Repository repository) throws DataException {
		List<AccountData> accounts = repository.getAccountRepository().getPenaltyAccounts();
		return AccountPenaltyStats.fromAccounts(accounts);
	}

	public static String getHash(List<String> penaltyAddresses) {
		if (penaltyAddresses == null || penaltyAddresses.isEmpty()) {
			return null;
		}
		Collections.sort(penaltyAddresses);
		return Base58.encode(Crypto.digest(StringUtils.join(penaltyAddresses).getBytes(StandardCharsets.UTF_8)));
	}

}