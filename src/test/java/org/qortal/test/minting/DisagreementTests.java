package org.qortal.test.minting;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.controller.BlockMinter;
import org.qortal.controller.Controller;
import org.qortal.data.account.RewardShareData;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AccountUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TestAccount;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transform.block.BlockTransformer;
import org.roaringbitmap.IntIterator;

import io.druid.extendedset.intset.ConciseSet;

public class DisagreementTests extends Common {

	private static final int CANCEL_SHARE_PERCENT = -1;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@After
	public void afterTest() throws DataException {
		Common.orphanCheck();
	}

	/**
	 * Testing minting a block when there is a signed online account timestamp present
	 * that no longer has a corresponding reward-share in DB.
	 * <p>
	 * Something like:
	 * <ul>
	 * <li>Mint block, with tx to create reward-share R</li>
	 * <li>Sign current timestamp with R</li>
	 * <li>Mint block including R as online account</li>
	 * <li>Mint block, with tx to cancel reward-share R</li>
	 * <li>Mint another block: R's timestamp should be excluded</li>
	 * </ul>
	 * 
	 * @throws DataException
	 */
	@Test
	public void testOnlineAccounts() throws DataException {
		final int sharePercent = 12_80;

		try (final Repository repository = RepositoryManager.getRepository()) {
			TestAccount mintingAccount = Common.getTestAccount(repository, "alice-reward-share");
			TestAccount signingAccount = Common.getTestAccount(repository, "alice");

			// Create reward-share
			byte[] testRewardSharePrivateKey = AccountUtils.rewardShare(repository, "alice", "bob", sharePercent);
			PrivateKeyAccount testRewardShareAccount = new PrivateKeyAccount(repository, testRewardSharePrivateKey);

			// Confirm reward-share info set correctly
			RewardShareData testRewardShareData = repository.getAccountRepository().getRewardShare(testRewardShareAccount.getPublicKey());
			assertNotNull(testRewardShareData);

			// Create signed timestamps
			Controller.getInstance().ensureTestingAccountsOnline(mintingAccount, testRewardShareAccount);

			// Mint another block
			BlockMinter.mintTestingBlockRetainingTimestamps(repository, mintingAccount);

			// Confirm reward-share's signed timestamp is included
			BlockData blockData = repository.getBlockRepository().getLastBlock();
			List<RewardShareData> rewardSharesData = fetchRewardSharesForBlock(repository, blockData);
			boolean doesContainRewardShare = rewardSharesData.stream().anyMatch(rewardShareData -> Arrays.equals(rewardShareData.getRewardSharePublicKey(), testRewardShareData.getRewardSharePublicKey()));
			assertTrue(doesContainRewardShare);

			// Cancel reward-share
			TransactionData cancelRewardShareTransactionData = AccountUtils.createRewardShare(repository, "alice", "bob", CANCEL_SHARE_PERCENT);
			TransactionUtils.signAndImportValid(repository, cancelRewardShareTransactionData, signingAccount);
			BlockMinter.mintTestingBlockRetainingTimestamps(repository, mintingAccount);

			// Confirm reward-share no longer exists in repository
			RewardShareData cancelledRewardShareData = repository.getAccountRepository().getRewardShare(testRewardShareAccount.getPublicKey());
			assertNull("Reward-share shouldn't exist", cancelledRewardShareData);

			// Attempt to mint with cancelled reward-share
			BlockMinter.mintTestingBlockRetainingTimestamps(repository, mintingAccount);

			// Confirm reward-share's signed timestamp is NOT included
			blockData = repository.getBlockRepository().getLastBlock();
			rewardSharesData = fetchRewardSharesForBlock(repository, blockData);
			doesContainRewardShare = rewardSharesData.stream().anyMatch(rewardShareData -> Arrays.equals(rewardShareData.getRewardSharePublicKey(), testRewardShareData.getRewardSharePublicKey()));
			assertFalse(doesContainRewardShare);
		}
	}

	private List<RewardShareData> fetchRewardSharesForBlock(Repository repository, BlockData blockData) throws DataException {
		byte[] encodedOnlineAccounts = blockData.getEncodedOnlineAccounts();
		ConciseSet accountIndexes = BlockTransformer.decodeOnlineAccounts(encodedOnlineAccounts);

		List<RewardShareData> rewardSharesData = new ArrayList<>();

		IntIterator iterator = accountIndexes.iterator();
		while (iterator.hasNext()) {
			int accountIndex = iterator.next();

			RewardShareData rewardShareData = repository.getAccountRepository().getRewardShareByIndex(accountIndex);
			rewardSharesData.add(rewardShareData);
		}

		return rewardSharesData;
	}

}
