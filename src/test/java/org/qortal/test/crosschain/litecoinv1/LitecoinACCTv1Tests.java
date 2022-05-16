package org.qortal.test.crosschain.litecoinv1;

import static org.junit.Assert.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.Block;
import org.qortal.crosschain.LitecoinACCTv1;
import org.qortal.crosschain.AcctMode;
import org.qortal.crypto.Crypto;
import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.data.crosschain.CrossChainTradeData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.MessageTransaction;
import org.qortal.utils.Amounts;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Bytes;

public class LitecoinACCTv1Tests extends Common {

	public static final byte[] secretA = "This string is exactly 32 bytes!".getBytes();
	public static final byte[] hashOfSecretA = Crypto.hash160(secretA); // daf59884b4d1aec8c1b17102530909ee43c0151a
	public static final byte[] litecoinPublicKeyHash = HashCode.fromString("bb00bb11bb22bb33bb44bb55bb66bb77bb88bb99").asBytes();
	public static final int tradeTimeout = 20; // blocks
	public static final long redeemAmount = 80_40200000L;
	public static final long fundingAmount = 123_45600000L;
	public static final long litecoinAmount = 864200L; // 0.00864200 LTC

	private static final Random RANDOM = new Random();

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testCompile() {
		PrivateKeyAccount tradeAccount = createTradeAccount(null);

		byte[] creationBytes = LitecoinACCTv1.buildQortalAT(tradeAccount.getAddress(), litecoinPublicKeyHash, redeemAmount, litecoinAmount, tradeTimeout);
		assertNotNull(creationBytes);

		System.out.println("AT creation bytes: " + HashCode.fromBytes(creationBytes).toString());
	}

	@Test
	public void testDeploy() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());

			long expectedBalance = deployersInitialBalance - fundingAmount - deployAtTransaction.getTransactionData().getFee();
			long actualBalance = deployer.getConfirmedBalance(Asset.QORT);

			assertEquals("Deployer's post-deployment balance incorrect", expectedBalance, actualBalance);

			expectedBalance = fundingAmount;
			actualBalance = deployAtTransaction.getATAccount().getConfirmedBalance(Asset.QORT);

			assertEquals("AT's post-deployment balance incorrect", expectedBalance, actualBalance);

			expectedBalance = partnersInitialBalance;
			actualBalance = partner.getConfirmedBalance(Asset.QORT);

			assertEquals("Partner's post-deployment balance incorrect", expectedBalance, actualBalance);

			// Test orphaning
			BlockUtils.orphanLastBlock(repository);

			expectedBalance = deployersInitialBalance;
			actualBalance = deployer.getConfirmedBalance(Asset.QORT);

			assertEquals("Deployer's post-orphan/pre-deployment balance incorrect", expectedBalance, actualBalance);

			expectedBalance = 0;
			actualBalance = deployAtTransaction.getATAccount().getConfirmedBalance(Asset.QORT);

			assertEquals("AT's post-orphan/pre-deployment balance incorrect", expectedBalance, actualBalance);

			expectedBalance = partnersInitialBalance;
			actualBalance = partner.getConfirmedBalance(Asset.QORT);

			assertEquals("Partner's post-orphan/pre-deployment balance incorrect", expectedBalance, actualBalance);
		}
	}

	@SuppressWarnings("unused")
	@Test
	public void testOfferCancel() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
			Account at = deployAtTransaction.getATAccount();
			String atAddress = at.getAddress();

			long deployAtFee = deployAtTransaction.getTransactionData().getFee();
			long deployersPostDeploymentBalance = deployersInitialBalance - fundingAmount - deployAtFee;

			// Send creator's address to AT, instead of typical partner's address
			byte[] messageData = LitecoinACCTv1.getInstance().buildCancelMessage(deployer.getAddress());
			MessageTransaction messageTransaction = sendMessage(repository, deployer, messageData, atAddress);
			long messageFee = messageTransaction.getTransactionData().getFee();

			// AT should process 'cancel' message in next block
			BlockUtils.mintBlock(repository);

			describeAt(repository, atAddress);

			// Check AT is finished
			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			assertTrue(atData.getIsFinished());

			// AT should be in CANCELLED mode
			CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);
			assertEquals(AcctMode.CANCELLED, tradeData.mode);

			// Check balances
			long expectedMinimumBalance = deployersPostDeploymentBalance;
			long expectedMaximumBalance = deployersInitialBalance - deployAtFee - messageFee;

			long actualBalance = deployer.getConfirmedBalance(Asset.QORT);

			assertTrue(String.format("Deployer's balance %s should be above minimum %s", actualBalance, expectedMinimumBalance), actualBalance > expectedMinimumBalance);
			assertTrue(String.format("Deployer's balance %s should be below maximum %s", actualBalance, expectedMaximumBalance), actualBalance < expectedMaximumBalance);

			// Test orphaning
			BlockUtils.orphanLastBlock(repository);

			// Check balances
			long expectedBalance = deployersPostDeploymentBalance - messageFee;
			actualBalance = deployer.getConfirmedBalance(Asset.QORT);

			assertEquals("Deployer's post-orphan/pre-refund balance incorrect", expectedBalance, actualBalance);
		}
	}

	@SuppressWarnings("unused")
	@Test
	public void testOfferCancelInvalidLength() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
			Account at = deployAtTransaction.getATAccount();
			String atAddress = at.getAddress();

			long deployAtFee = deployAtTransaction.getTransactionData().getFee();
			long deployersPostDeploymentBalance = deployersInitialBalance - fundingAmount - deployAtFee;

			// Instead of sending creator's address to AT, send too-short/invalid message
			byte[] messageData = new byte[7];
			RANDOM.nextBytes(messageData);
			MessageTransaction messageTransaction = sendMessage(repository, deployer, messageData, atAddress);
			long messageFee = messageTransaction.getTransactionData().getFee();

			// AT should process 'cancel' message in next block
			// As message is too short, it will be padded to 32bytes but cancel code doesn't care about message content, so should be ok
			BlockUtils.mintBlock(repository);

			describeAt(repository, atAddress);

			// Check AT is finished
			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			assertTrue(atData.getIsFinished());

			// AT should be in CANCELLED mode
			CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);
			assertEquals(AcctMode.CANCELLED, tradeData.mode);
		}
	}

	@SuppressWarnings("unused")
	@Test
	public void testTradingInfoProcessing() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
			Account at = deployAtTransaction.getATAccount();
			String atAddress = at.getAddress();

			long partnersOfferMessageTransactionTimestamp = System.currentTimeMillis();
			int lockTimeA = calcTestLockTimeA(partnersOfferMessageTransactionTimestamp);
			int refundTimeout = LitecoinACCTv1.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);

			// Send trade info to AT
			byte[] messageData = LitecoinACCTv1.buildTradeMessage(partner.getAddress(), litecoinPublicKeyHash, hashOfSecretA, lockTimeA, refundTimeout);
			MessageTransaction messageTransaction = sendMessage(repository, tradeAccount, messageData, atAddress);

			Block postDeploymentBlock = BlockUtils.mintBlock(repository);
			int postDeploymentBlockHeight = postDeploymentBlock.getBlockData().getHeight();

			long deployAtFee = deployAtTransaction.getTransactionData().getFee();
			long deployersPostDeploymentBalance = deployersInitialBalance - fundingAmount - deployAtFee;

			describeAt(repository, atAddress);

			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);

			// AT should be in TRADE mode
			assertEquals(AcctMode.TRADING, tradeData.mode);

			// Check hashOfSecretA was extracted correctly
			assertTrue(Arrays.equals(hashOfSecretA, tradeData.hashOfSecretA));

			// Check trade partner Qortal address was extracted correctly
			assertEquals(partner.getAddress(), tradeData.qortalPartnerAddress);

			// Check trade partner's Litecoin PKH was extracted correctly
			assertTrue(Arrays.equals(litecoinPublicKeyHash, tradeData.partnerForeignPKH));

			// Test orphaning
			BlockUtils.orphanToBlock(repository, postDeploymentBlockHeight);

			// Check balances
			long expectedBalance = deployersPostDeploymentBalance;
			long actualBalance = deployer.getConfirmedBalance(Asset.QORT);

			assertEquals("Deployer's post-orphan/pre-refund balance incorrect", expectedBalance, actualBalance);
		}
	}

	// TEST SENDING TRADING INFO BUT NOT FROM AT CREATOR (SHOULD BE IGNORED)
	@SuppressWarnings("unused")
	@Test
	public void testIncorrectTradeSender() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount bystander = Common.getTestAccount(repository, "bob");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
			Account at = deployAtTransaction.getATAccount();
			String atAddress = at.getAddress();

			long partnersOfferMessageTransactionTimestamp = System.currentTimeMillis();
			int lockTimeA = calcTestLockTimeA(partnersOfferMessageTransactionTimestamp);
			int refundTimeout = LitecoinACCTv1.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);

			// Send trade info to AT BUT NOT FROM AT CREATOR
			byte[] messageData = LitecoinACCTv1.buildTradeMessage(partner.getAddress(), litecoinPublicKeyHash, hashOfSecretA, lockTimeA, refundTimeout);
			MessageTransaction messageTransaction = sendMessage(repository, bystander, messageData, atAddress);

			BlockUtils.mintBlock(repository);

			long expectedBalance = partnersInitialBalance;
			long actualBalance = partner.getConfirmedBalance(Asset.QORT);

			assertEquals("Partner's post-initial-payout balance incorrect", expectedBalance, actualBalance);

			describeAt(repository, atAddress);

			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);

			// AT should still be in OFFER mode
			assertEquals(AcctMode.OFFERING, tradeData.mode);
		}
	}

	@SuppressWarnings("unused")
	@Test
	public void testAutomaticTradeRefund() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
			Account at = deployAtTransaction.getATAccount();
			String atAddress = at.getAddress();

			long partnersOfferMessageTransactionTimestamp = System.currentTimeMillis();
			int lockTimeA = calcTestLockTimeA(partnersOfferMessageTransactionTimestamp);
			int refundTimeout = LitecoinACCTv1.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);

			// Send trade info to AT
			byte[] messageData = LitecoinACCTv1.buildTradeMessage(partner.getAddress(), litecoinPublicKeyHash, hashOfSecretA, lockTimeA, refundTimeout);
			MessageTransaction messageTransaction = sendMessage(repository, tradeAccount, messageData, atAddress);

			Block postDeploymentBlock = BlockUtils.mintBlock(repository);
			int postDeploymentBlockHeight = postDeploymentBlock.getBlockData().getHeight();

			// Check refund
			long deployAtFee = deployAtTransaction.getTransactionData().getFee();
			long deployersPostDeploymentBalance = deployersInitialBalance - fundingAmount - deployAtFee;

			checkTradeRefund(repository, deployer, deployersInitialBalance, deployAtFee);

			describeAt(repository, atAddress);

			// Check AT is finished
			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			assertTrue(atData.getIsFinished());

			// AT should be in REFUNDED mode
			CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);
			assertEquals(AcctMode.REFUNDED, tradeData.mode);

			// Test orphaning
			BlockUtils.orphanToBlock(repository, postDeploymentBlockHeight);

			// Check balances
			long expectedBalance = deployersPostDeploymentBalance;
			long actualBalance = deployer.getConfirmedBalance(Asset.QORT);

			assertEquals("Deployer's post-orphan/pre-refund balance incorrect", expectedBalance, actualBalance);
		}
	}

	@SuppressWarnings("unused")
	@Test
	public void testCorrectSecretCorrectSender() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
			Account at = deployAtTransaction.getATAccount();
			String atAddress = at.getAddress();

			long partnersOfferMessageTransactionTimestamp = System.currentTimeMillis();
			int lockTimeA = calcTestLockTimeA(partnersOfferMessageTransactionTimestamp);
			int refundTimeout = LitecoinACCTv1.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);

			// Send trade info to AT
			byte[] messageData = LitecoinACCTv1.buildTradeMessage(partner.getAddress(), litecoinPublicKeyHash, hashOfSecretA, lockTimeA, refundTimeout);
			MessageTransaction messageTransaction = sendMessage(repository, tradeAccount, messageData, atAddress);

			// Give AT time to process message
			BlockUtils.mintBlock(repository);

			// Send correct secret to AT, from correct account
			messageData = LitecoinACCTv1.buildRedeemMessage(secretA,  partner.getAddress());
			messageTransaction = sendMessage(repository, partner, messageData, atAddress);

			// AT should send funds in the next block
			ATStateData preRedeemAtStateData = repository.getATRepository().getLatestATState(atAddress);
			BlockUtils.mintBlock(repository);

			describeAt(repository, atAddress);

			// Check AT is finished
			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			assertTrue(atData.getIsFinished());

			// AT should be in REDEEMED mode
			CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);
			assertEquals(AcctMode.REDEEMED, tradeData.mode);

			// Check balances
			long expectedBalance = partnersInitialBalance - messageTransaction.getTransactionData().getFee() + redeemAmount;
			long actualBalance = partner.getConfirmedBalance(Asset.QORT);

			assertEquals("Partner's post-redeem balance incorrect", expectedBalance, actualBalance);

			// Orphan redeem
			BlockUtils.orphanLastBlock(repository);

			// Check balances
			expectedBalance = partnersInitialBalance - messageTransaction.getTransactionData().getFee();
			actualBalance = partner.getConfirmedBalance(Asset.QORT);

			assertEquals("Partner's post-orphan/pre-redeem balance incorrect", expectedBalance, actualBalance);

			// Check AT state
			ATStateData postOrphanAtStateData = repository.getATRepository().getLatestATState(atAddress);

			assertTrue("AT states mismatch", Arrays.equals(preRedeemAtStateData.getStateData(), postOrphanAtStateData.getStateData()));
		}
	}

	@SuppressWarnings("unused")
	@Test
	public void testCorrectSecretIncorrectSender() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			PrivateKeyAccount bystander = Common.getTestAccount(repository, "bob");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
			long deployAtFee = deployAtTransaction.getTransactionData().getFee();

			Account at = deployAtTransaction.getATAccount();
			String atAddress = at.getAddress();

			long partnersOfferMessageTransactionTimestamp = System.currentTimeMillis();
			int lockTimeA = calcTestLockTimeA(partnersOfferMessageTransactionTimestamp);
			int refundTimeout = LitecoinACCTv1.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);

			// Send trade info to AT
			byte[] messageData = LitecoinACCTv1.buildTradeMessage(partner.getAddress(), litecoinPublicKeyHash, hashOfSecretA, lockTimeA, refundTimeout);
			MessageTransaction messageTransaction = sendMessage(repository, tradeAccount, messageData, atAddress);

			// Give AT time to process message
			BlockUtils.mintBlock(repository);

			// Send correct secret to AT, but from wrong account
			messageData = LitecoinACCTv1.buildRedeemMessage(secretA, partner.getAddress());
			messageTransaction = sendMessage(repository, bystander, messageData, atAddress);

			// AT should NOT send funds in the next block
			ATStateData preRedeemAtStateData = repository.getATRepository().getLatestATState(atAddress);
			BlockUtils.mintBlock(repository);

			describeAt(repository, atAddress);

			// Check AT is NOT finished
			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			assertFalse(atData.getIsFinished());

			// AT should still be in TRADE mode
			CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);
			assertEquals(AcctMode.TRADING, tradeData.mode);

			// Check balances
			long expectedBalance = partnersInitialBalance;
			long actualBalance = partner.getConfirmedBalance(Asset.QORT);

			assertEquals("Partner's balance incorrect", expectedBalance, actualBalance);

			// Check eventual refund
			checkTradeRefund(repository, deployer, deployersInitialBalance, deployAtFee);
		}
	}

	@SuppressWarnings("unused")
	@Test
	public void testIncorrectSecretCorrectSender() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
			long deployAtFee = deployAtTransaction.getTransactionData().getFee();

			Account at = deployAtTransaction.getATAccount();
			String atAddress = at.getAddress();

			long partnersOfferMessageTransactionTimestamp = System.currentTimeMillis();
			int lockTimeA = calcTestLockTimeA(partnersOfferMessageTransactionTimestamp);
			int refundTimeout = LitecoinACCTv1.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);

			// Send trade info to AT
			byte[] messageData = LitecoinACCTv1.buildTradeMessage(partner.getAddress(), litecoinPublicKeyHash, hashOfSecretA, lockTimeA, refundTimeout);
			MessageTransaction messageTransaction = sendMessage(repository, tradeAccount, messageData, atAddress);

			// Give AT time to process message
			BlockUtils.mintBlock(repository);

			// Send incorrect secret to AT, from correct account
			byte[] wrongSecret = new byte[32];
			RANDOM.nextBytes(wrongSecret);
			messageData = LitecoinACCTv1.buildRedeemMessage(wrongSecret, partner.getAddress());
			messageTransaction = sendMessage(repository, partner, messageData, atAddress);

			// AT should NOT send funds in the next block
			ATStateData preRedeemAtStateData = repository.getATRepository().getLatestATState(atAddress);
			BlockUtils.mintBlock(repository);

			describeAt(repository, atAddress);

			// Check AT is NOT finished
			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			assertFalse(atData.getIsFinished());

			// AT should still be in TRADE mode
			CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);
			assertEquals(AcctMode.TRADING, tradeData.mode);

			long expectedBalance = partnersInitialBalance - messageTransaction.getTransactionData().getFee();
			long actualBalance = partner.getConfirmedBalance(Asset.QORT);

			assertEquals("Partner's balance incorrect", expectedBalance, actualBalance);

			// Check eventual refund
			checkTradeRefund(repository, deployer, deployersInitialBalance, deployAtFee);
		}
	}

	@SuppressWarnings("unused")
	@Test
	public void testCorrectSecretCorrectSenderInvalidMessageLength() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());
			Account at = deployAtTransaction.getATAccount();
			String atAddress = at.getAddress();

			long partnersOfferMessageTransactionTimestamp = System.currentTimeMillis();
			int lockTimeA = calcTestLockTimeA(partnersOfferMessageTransactionTimestamp);
			int refundTimeout = LitecoinACCTv1.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);

			// Send trade info to AT
			byte[] messageData = LitecoinACCTv1.buildTradeMessage(partner.getAddress(), litecoinPublicKeyHash, hashOfSecretA, lockTimeA, refundTimeout);
			MessageTransaction messageTransaction = sendMessage(repository, tradeAccount, messageData, atAddress);

			// Give AT time to process message
			BlockUtils.mintBlock(repository);

			// Send correct secret to AT, from correct account, but missing receive address, hence incorrect length
			messageData = Bytes.concat(secretA);
			messageTransaction = sendMessage(repository, partner, messageData, atAddress);

			// AT should NOT send funds in the next block
			ATStateData preRedeemAtStateData = repository.getATRepository().getLatestATState(atAddress);
			BlockUtils.mintBlock(repository);

			describeAt(repository, atAddress);

			// Check AT is NOT finished
			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			assertFalse(atData.getIsFinished());

			// AT should be in TRADING mode
			CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);
			assertEquals(AcctMode.TRADING, tradeData.mode);
		}
	}

	@SuppressWarnings("unused")
	@Test
	public void testDescribeDeployed() throws DataException {
		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount tradeAccount = createTradeAccount(repository);

			PrivateKeyAccount partner = Common.getTestAccount(repository, "dilbert");

			long deployersInitialBalance = deployer.getConfirmedBalance(Asset.QORT);
			long partnersInitialBalance = partner.getConfirmedBalance(Asset.QORT);

			DeployAtTransaction deployAtTransaction = doDeploy(repository, deployer, tradeAccount.getAddress());

			List<ATData> executableAts = repository.getATRepository().getAllExecutableATs();

			for (ATData atData : executableAts) {
				String atAddress = atData.getATAddress();
				byte[] codeBytes = atData.getCodeBytes();
				byte[] codeHash = Crypto.digest(codeBytes);

				System.out.println(String.format("%s: code length: %d byte%s, code hash: %s",
						atAddress,
						codeBytes.length,
						(codeBytes.length != 1 ? "s": ""),
						HashCode.fromBytes(codeHash)));

				// Not one of ours?
				if (!Arrays.equals(codeHash, LitecoinACCTv1.CODE_BYTES_HASH))
					continue;

				describeAt(repository, atAddress);
			}
		}
	}

	private int calcTestLockTimeA(long messageTimestamp) {
		return (int) (messageTimestamp / 1000L + tradeTimeout * 60);
	}

	private DeployAtTransaction doDeploy(Repository repository, PrivateKeyAccount deployer, String tradeAddress) throws DataException {
		byte[] creationBytes = LitecoinACCTv1.buildQortalAT(tradeAddress, litecoinPublicKeyHash, redeemAmount, litecoinAmount, tradeTimeout);

		long txTimestamp = System.currentTimeMillis();
		byte[] lastReference = deployer.getLastReference();

		if (lastReference == null) {
			System.err.println(String.format("Qortal account %s has no last reference", deployer.getAddress()));
			System.exit(2);
		}

		Long fee = null;
		String name = "QORT-LTC cross-chain trade";
		String description = String.format("Qortal-Litecoin cross-chain trade");
		String atType = "ACCT";
		String tags = "QORT-LTC ACCT";

		BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, deployer.getPublicKey(), fee, null);
		TransactionData deployAtTransactionData = new DeployAtTransactionData(baseTransactionData, name, description, atType, tags, creationBytes, fundingAmount, Asset.QORT);

		DeployAtTransaction deployAtTransaction = new DeployAtTransaction(repository, deployAtTransactionData);

		fee = deployAtTransaction.calcRecommendedFee();
		deployAtTransactionData.setFee(fee);

		TransactionUtils.signAndMint(repository, deployAtTransactionData, deployer);

		return deployAtTransaction;
	}

	private MessageTransaction sendMessage(Repository repository, PrivateKeyAccount sender, byte[] data, String recipient) throws DataException {
		long txTimestamp = System.currentTimeMillis();
		byte[] lastReference = sender.getLastReference();

		if (lastReference == null) {
			System.err.println(String.format("Qortal account %s has no last reference", sender.getAddress()));
			System.exit(2);
		}

		Long fee = null;
		int version = 4;
		int nonce = 0;
		long amount = 0;
		Long assetId = null; // because amount is zero

		BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, sender.getPublicKey(), fee, null);
		TransactionData messageTransactionData = new MessageTransactionData(baseTransactionData, version, nonce, recipient, amount, assetId, data, false, false);

		MessageTransaction messageTransaction = new MessageTransaction(repository, messageTransactionData);

		fee = messageTransaction.calcRecommendedFee();
		messageTransactionData.setFee(fee);

		TransactionUtils.signAndMint(repository, messageTransactionData, sender);

		return messageTransaction;
	}

	private void checkTradeRefund(Repository repository, Account deployer, long deployersInitialBalance, long deployAtFee) throws DataException {
		long deployersPostDeploymentBalance = deployersInitialBalance - fundingAmount - deployAtFee;
		int refundTimeout = tradeTimeout / 2 + 1; // close enough

		// AT should automatically refund deployer after 'refundTimeout' blocks
		for (int blockCount = 0; blockCount <= refundTimeout; ++blockCount)
			BlockUtils.mintBlock(repository);

		// We don't bother to exactly calculate QORT spent running AT for several blocks, but we do know the expected range
		long expectedMinimumBalance = deployersPostDeploymentBalance;
		long expectedMaximumBalance = deployersInitialBalance - deployAtFee;

		long actualBalance = deployer.getConfirmedBalance(Asset.QORT);

		assertTrue(String.format("Deployer's balance %s should be above minimum %s", actualBalance, expectedMinimumBalance), actualBalance > expectedMinimumBalance);
		assertTrue(String.format("Deployer's balance %s should be below maximum %s", actualBalance, expectedMaximumBalance), actualBalance < expectedMaximumBalance);
	}

	private void describeAt(Repository repository, String atAddress) throws DataException {
		ATData atData = repository.getATRepository().fromATAddress(atAddress);
		CrossChainTradeData tradeData = LitecoinACCTv1.getInstance().populateTradeData(repository, atData);

		Function<Long, String> epochMilliFormatter = (timestamp) -> LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
		int currentBlockHeight = repository.getBlockRepository().getBlockchainHeight();

		System.out.print(String.format("%s:\n"
				+ "\tmode: %s\n"
				+ "\tcreator: %s,\n"
				+ "\tcreation timestamp: %s,\n"
				+ "\tcurrent balance: %s QORT,\n"
				+ "\tis finished: %b,\n"
				+ "\tredeem payout: %s QORT,\n"
				+ "\texpected Litecoin: %s LTC,\n"
				+ "\tcurrent block height: %d,\n",
				tradeData.qortalAtAddress,
				tradeData.mode,
				tradeData.qortalCreator,
				epochMilliFormatter.apply(tradeData.creationTimestamp),
				Amounts.prettyAmount(tradeData.qortBalance),
				atData.getIsFinished(),
				Amounts.prettyAmount(tradeData.qortAmount),
				Amounts.prettyAmount(tradeData.expectedForeignAmount),
				currentBlockHeight));

		if (tradeData.mode != AcctMode.OFFERING && tradeData.mode != AcctMode.CANCELLED) {
			System.out.println(String.format("\trefund timeout: %d minutes,\n"
					+ "\trefund height: block %d,\n"
					+ "\tHASH160 of secret-A: %s,\n"
					+ "\tLitecoin P2SH-A nLockTime: %d (%s),\n"
					+ "\ttrade partner: %s\n"
					+ "\tpartner's receiving address: %s",
					tradeData.refundTimeout,
					tradeData.tradeRefundHeight,
					HashCode.fromBytes(tradeData.hashOfSecretA).toString().substring(0, 40),
					tradeData.lockTimeA, epochMilliFormatter.apply(tradeData.lockTimeA * 1000L),
					tradeData.qortalPartnerAddress,
					tradeData.qortalPartnerReceivingAddress));
		}
	}

	private PrivateKeyAccount createTradeAccount(Repository repository) {
		// We actually use a known test account with funds to avoid PoW compute
		return Common.getTestAccount(repository, "alice");
	}

}
