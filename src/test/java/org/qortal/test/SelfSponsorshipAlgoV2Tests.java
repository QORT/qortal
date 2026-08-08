package org.qortal.test;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.Block;
import org.qortal.controller.BlockMinter;
import org.qortal.data.account.AccountPenaltyData;
import org.qortal.data.transaction.*;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.utils.NTP;

import java.util.*;

import static org.junit.Assert.*;
import static org.qortal.test.common.AccountUtils.fee;

public class SelfSponsorshipAlgoV2Tests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useSettings("test-settings-v2-self-sponsorship-algo-v2.json");
		NTP.setFixedOffset(Settings.getInstance().getTestNtpOffset());
	}

	@Test
	public void tesTransferAssetsQort() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {

			// Alice self share online, and will be used to mint the blocks
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			List<PrivateKeyAccount> onlineAccounts = new ArrayList<>();
			onlineAccounts.add(aliceSelfShare);

			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloeAccount = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbertAccount = Common.getTestAccount(repository, "dilbert");

			// Mint blocks
			Block block = null;
			for (int i = 0; i <= 1; i++)
				block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that Bob, Chloe and Dilbert are greater than level 0
			assertTrue(new Account(repository, bobAccount.getAddress()).getLevel() > 0);
			assertTrue(new Account(repository, chloeAccount.getAddress()).getLevel() > 0);
			assertTrue(new Account(repository, dilbertAccount.getAddress()).getLevel() > 0);

			// Mint some blocks, until accounts have leveled up
			for (int i = 0; i <= 5; i++)
				block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that Chloe and Dilbert have more than 20 qort
			assertTrue(new Account(repository, chloeAccount.getAddress()).getConfirmedBalance(Asset.QORT) > 20); // 10 for transfer asset, 10 for fee
			assertTrue(new Account(repository, dilbertAccount.getAddress()).getConfirmedBalance(Asset.QORT) > 20); // 10 for transfer asset, 10 for fee

			// Mint until block 10
			while (block.getBlockData().getHeight() < 10)
				block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(10, (int) block.getBlockData().getHeight());

			// Chloe transfer assets to Bob and Dilbert
			transferAssets(repository, chloeAccount, bobAccount);
			transferAssets(repository, chloeAccount, dilbertAccount);

			// Dilbert transfer assets to Bob and Chloe
			transferAssets(repository, dilbertAccount, bobAccount);
			transferAssets(repository, dilbertAccount, chloeAccount);

			// Mint until block 29 (the algo runs at block 30)
			while (block.getBlockData().getHeight() < 29)
				block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(29, (int) block.getBlockData().getHeight());

			// Ensure that Bob have no penalties and level 5
			assertEquals(0, (int) new Account(repository, bobAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(5, (int)bobAccount.getLevel());

			// Ensure that Chloe have no penalties and level 5
			assertEquals(0, (int) new Account(repository, chloeAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(5, (int)chloeAccount.getLevel());

			// Ensure that Dilbert have no penalties and level6
			assertEquals(0, (int) new Account(repository, dilbertAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(6, (int)dilbertAccount.getLevel());

			// Mint a block, so the algo runs
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that Bob, Chloe and Dilbert are now have penalties
			assertEquals(-5000000, (int) new Account(repository, bobAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(-5000000, (int) new Account(repository, chloeAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(-5000000, (int) new Account(repository, dilbertAccount.getAddress()).getBlocksMintedPenalty());

			// Ensure that Bob, Chloe and Dilbert are now level 0
			assertEquals(0, (int) new Account(repository, bobAccount.getAddress()).getLevel());
			assertEquals(0, (int) new Account(repository, chloeAccount.getAddress()).getLevel());
			assertEquals(0, (int) new Account(repository, dilbertAccount.getAddress()).getLevel());

			// Orphan last block
			BlockUtils.orphanLastBlock(repository);

			// Ensure that Bob, Chloe and Dilbert are now greater than level 0
			assertTrue(new Account(repository, bobAccount.getAddress()).getLevel() > 0);
			assertTrue(new Account(repository, chloeAccount.getAddress()).getLevel() > 0);
			assertTrue(new Account(repository, dilbertAccount.getAddress()).getLevel() > 0);

			// Ensure that Bob, Chloe and Dilbert have no penalties again
			assertEquals(0, (int) new Account(repository, bobAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(0, (int) new Account(repository, chloeAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(0, (int) new Account(repository, dilbertAccount.getAddress()).getBlocksMintedPenalty());

			// Run orphan check - this can't be in afterTest() because some tests access the live db
			Common.orphanCheck();
		}
	}

	@Test
	public void testSingleTransferPrivsBeforeAlgoBlock() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {

			// Alice self share online, and will be used to mint the blocks
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			List<PrivateKeyAccount> onlineAccounts = new ArrayList<>();
			onlineAccounts.add(aliceSelfShare);

			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");

			// Mint blocks
			Block block = null;
			for (int i = 0; i <= 5; i++)
				block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that Bob have more than 20 qort
			assertTrue(new Account(repository, bobAccount.getAddress()).getConfirmedBalance(Asset.QORT) > 20);

			// Mint until block 17 (the algo runs at block 20)
			while (block.getBlockData().getHeight() < 26)
				block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(26, (int) block.getBlockData().getHeight());

			// Bob then issues a TRANSFER_PRIVS
			PrivateKeyAccount recipientAccount = randomTransferPrivs(repository, bobAccount);

			// Ensure recipient has no level (actually, no account record) at this point (pre-confirmation)
			assertNull(recipientAccount.getLevel());

			// Mint a block, so that the TRANSFER_PRIVS confirms
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Now ensure that the TRANSFER_PRIVS recipient has inherited Bob's level, and Bob is at level 0
			assertTrue(recipientAccount.getLevel() > 0);
			assertEquals(0, (int)bobAccount.getLevel());
			assertEquals(0, (int) new Account(repository, recipientAccount.getAddress()).getBlocksMintedPenalty());

			// Mint a block, so that we can penalize Bob after transfer privs
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Update blocks minted penalty for Bob
			Set<AccountPenaltyData> penalties = new LinkedHashSet<>();
			penalties.add(new AccountPenaltyData(bobAccount.getAddress(), -5000000));
			repository.getAccountRepository().updateBlocksMintedPenalties(penalties);

			// Mint a block, so that we check if Bob got penalized before algo runs
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure Bob got penalized
			assertEquals(-5000000, (int) new Account(repository, bobAccount.getAddress()).getBlocksMintedPenalty());

			// Mint a block, so the algo runs
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure recipient account has penalty too
			assertEquals(-5000000, (int) new Account(repository, recipientAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(0, (int) new Account(repository, recipientAccount.getAddress()).getLevel());

			// Orphan last block
			BlockUtils.orphanLastBlock(repository);

			// Ensure recipient account has no penalty again and has a level greater than 0
			assertEquals(0, (int) new Account(repository, recipientAccount.getAddress()).getBlocksMintedPenalty());
			assertTrue(new Account(repository, recipientAccount.getAddress()).getLevel() > 0);

			// Run orphan check - this can't be in afterTest() because some tests access the live db
			Common.orphanCheck();
		}
	}

	@Test
	public void testMultipleTransferPrivsBeforeAlgoBlock() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {

			// Alice self share online, and will be used to mint the blocks
			PrivateKeyAccount aliceSelfShare = Common.getTestAccount(repository, "alice-reward-share");
			List<PrivateKeyAccount> onlineAccounts = new ArrayList<>();
			onlineAccounts.add(aliceSelfShare);

			PrivateKeyAccount bobAccount = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloeAccount = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbertAccount = Common.getTestAccount(repository, "dilbert");

			// Mint blocks
			Block block = null;
			for (int i = 0; i <= 5; i++)
				block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure that Bob, Chloe and Dilbert have more than 20 qort
			assertTrue(new Account(repository, bobAccount.getAddress()).getConfirmedBalance(Asset.QORT) > 20);
			assertTrue(new Account(repository, chloeAccount.getAddress()).getConfirmedBalance(Asset.QORT) > 20);
			assertTrue(new Account(repository, dilbertAccount.getAddress()).getConfirmedBalance(Asset.QORT) > 20);

			// Mint until block 12 (the algo runs at block 20)
			while (block.getBlockData().getHeight() < 22)
				block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));
			assertEquals(22, (int) block.getBlockData().getHeight());

			// Bob then issues a TRANSFER_PRIVS
			PrivateKeyAccount recipientAccount1 = randomTransferPrivs(repository, bobAccount);

			// Ensure Bob's recipient has no level (actually, no account record) at this point (pre-confirmation)
			assertNull(recipientAccount1.getLevel());

			// Mint a block, so that Bob's TRANSFER_PRIVS confirms
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Now ensure that the Bob's TRANSFER_PRIVS recipient has inherited Bob's level, and Bob is at level 0
			assertTrue(recipientAccount1.getLevel() > 0);
			assertEquals(0, (int)bobAccount.getLevel());
			assertEquals(0, (int) new Account(repository, recipientAccount1.getAddress()).getBlocksMintedPenalty());

			// Mint a block, so that Chloe can issue a TRANSFER_PRIVS
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Chloe then issues a TRANSFER_PRIVS
			PrivateKeyAccount recipientAccount2 = randomTransferPrivs(repository, chloeAccount);

			// Ensure Chloe's recipient has no level (actually, no account record) at this point (pre-confirmation)
			assertNull(recipientAccount2.getLevel());

			// Mint a block, so that Chloe's TRANSFER_PRIVS confirms
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Now ensure that the Chloe's TRANSFER_PRIVS recipient has inherited Chloe's level, and Chloe is at level 0
			assertTrue(recipientAccount2.getLevel() > 0);
			assertEquals(0, (int)chloeAccount.getLevel());
			assertEquals(0, (int) new Account(repository, recipientAccount2.getAddress()).getBlocksMintedPenalty());

			// Mint a block, so that Dilbert can issue a TRANSFER_PRIVS
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Dilbert then issues a TRANSFER_PRIVS
			PrivateKeyAccount recipientAccount3 = randomTransferPrivs(repository, dilbertAccount);

			// Ensure Dilbert's recipient has no level (actually, no account record) at this point (pre-confirmation)
			assertNull(recipientAccount3.getLevel());

			// Mint a block, so that Dilbert's TRANSFER_PRIVS confirms
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Now ensure that the Dilbert's TRANSFER_PRIVS recipient has inherited Dilbert's level, and Dilbert is at level 0
			assertTrue(recipientAccount3.getLevel() > 0);
			assertEquals(0, (int)dilbertAccount.getLevel());
			assertEquals(0, (int) new Account(repository, recipientAccount3.getAddress()).getBlocksMintedPenalty());

			// Mint a block, so that we can penalize Bob, Chloe and Dilbert after transfer privs
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Update blocks minted penalty for Bob, Chloe and Dilbert
			Set<AccountPenaltyData> penalties = new LinkedHashSet<>();
			penalties.add(new AccountPenaltyData(bobAccount.getAddress(), -5000000));
			penalties.add(new AccountPenaltyData(chloeAccount.getAddress(), -5000000));
			penalties.add(new AccountPenaltyData(dilbertAccount.getAddress(), -5000000));
			repository.getAccountRepository().updateBlocksMintedPenalties(penalties);

			// Mint a block, so that we check if Bob, Chloe and Dilbert got penalized before algo runs
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure Bob, Chloe and Dilbert got penalized
			assertEquals(-5000000, (int) new Account(repository, bobAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(-5000000, (int) new Account(repository, chloeAccount.getAddress()).getBlocksMintedPenalty());
			assertEquals(-5000000, (int) new Account(repository, dilbertAccount.getAddress()).getBlocksMintedPenalty());

			// Mint a block, so the algo runs
			block = BlockMinter.mintTestingBlock(repository, onlineAccounts.toArray(new PrivateKeyAccount[0]));

			// Ensure recipients accounts has penalty too
			assertEquals(-5000000, (int) new Account(repository, recipientAccount1.getAddress()).getBlocksMintedPenalty());
			assertEquals(-5000000, (int) new Account(repository, recipientAccount2.getAddress()).getBlocksMintedPenalty());
			assertEquals(-5000000, (int) new Account(repository, recipientAccount3.getAddress()).getBlocksMintedPenalty());
			assertEquals(0, (int) new Account(repository, recipientAccount1.getAddress()).getLevel());
			assertEquals(0, (int) new Account(repository, recipientAccount2.getAddress()).getLevel());
			assertEquals(0, (int) new Account(repository, recipientAccount3.getAddress()).getLevel());

			// Orphan last block
			BlockUtils.orphanLastBlock(repository);

			// Ensure recipients accounts has no penalty again and has a level greater than 0
			assertEquals(0, (int) new Account(repository, recipientAccount1.getAddress()).getBlocksMintedPenalty());
			assertEquals(0, (int) new Account(repository, recipientAccount2.getAddress()).getBlocksMintedPenalty());
			assertEquals(0, (int) new Account(repository, recipientAccount3.getAddress()).getBlocksMintedPenalty());
			assertTrue(new Account(repository, recipientAccount1.getAddress()).getLevel() > 0);
			assertTrue(new Account(repository, recipientAccount2.getAddress()).getLevel() > 0);
			assertTrue(new Account(repository, recipientAccount3.getAddress()).getLevel() > 0);

			// Run orphan check - this can't be in afterTest() because some tests access the live db
			Common.orphanCheck();
		}
	}

	private static void transferAssets(Repository repository, PrivateKeyAccount senderAccount, PrivateKeyAccount recipientAccount) throws DataException {
		for (int i = 0; i < 5; i++) {
			// Generate new asset transfers from sender to recipient
			BaseTransactionData baseTransactionData = new BaseTransactionData(NTP.getTime(), 0, senderAccount.getLastReference(), senderAccount.getPublicKey(), fee, null);
			TransactionData transactionData;
			transactionData = new TransferAssetTransactionData(baseTransactionData, recipientAccount.getAddress(), 1, 0);
			TransactionUtils.signAndImportValid(repository, transactionData, senderAccount); // updates paymentData's signature
		}
	}

	private static PrivateKeyAccount randomTransferPrivs(Repository repository, PrivateKeyAccount senderAccount) throws DataException {
		// Generate random recipient account
		byte[] randomPrivateKey = new byte[32];
		new Random().nextBytes(randomPrivateKey);
		PrivateKeyAccount recipientAccount = new PrivateKeyAccount(repository, randomPrivateKey);

		BaseTransactionData baseTransactionData = new BaseTransactionData(NTP.getTime(), 0, senderAccount.getLastReference(), senderAccount.getPublicKey(), fee, null);
		TransactionData transactionData = new TransferPrivsTransactionData(baseTransactionData, recipientAccount.getAddress());

		TransactionUtils.signAndImportValid(repository, transactionData, senderAccount);

		return recipientAccount;
	}

}