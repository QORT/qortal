package org.qortal.test.crosschain.bitcoinv1;

import com.google.common.hash.HashCode;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.crosschain.ACCT;
import org.qortal.crosschain.AcctMode;
import org.qortal.crosschain.BitcoinACCTv1;
import org.qortal.crypto.Crypto;
import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.data.crosschain.CrossChainTradeData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.crosschain.ACCTTests;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.MessageTransaction;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BitcoinACCTv1Tests extends ACCTTests {

	public static final byte[] secretB = "This string is roughly 32 bytes?".getBytes();
	public static final byte[] hashOfSecretB = Crypto.hash160(secretB); // 31f0dd71decf59bbc8ef0661f4030479255cfa58
	public static final byte[] bitcoinPublicKeyHash = HashCode.fromString("bb00bb11bb22bb33bb44bb55bb66bb77bb88bb99").asBytes();

	private static final String SYMBOL = "BTC";

	private static final String NAME = "Bitcoin";

	@Override
	protected byte[] getPublicKey() {
		return bitcoinPublicKeyHash;
	}

	@Override
	protected byte[] buildQortalAT(String address, byte[] publicKey, long redeemAmount, long foreignAmount, int tradeTimeout) {
		return BitcoinACCTv1.buildQortalAT(address,publicKey, hashOfSecretB, redeemAmount, foreignAmount,tradeTimeout);
	}

	@Override
	protected ACCT getInstance() {
		return BitcoinACCTv1.getInstance();
	}

	@Override
	protected int calcRefundTimeout(long partnersOfferMessageTransactionTimestamp, int lockTimeA) {
		return BitcoinACCTv1.calcLockTimeB(partnersOfferMessageTransactionTimestamp, lockTimeA);
	}

	@Override
	protected byte[] buildTradeMessage(String address, byte[] publicKey, byte[] hashOfSecretA, int lockTimeA, int refundTimeout) {
		return BitcoinACCTv1.buildTradeMessage(address, publicKey, hashOfSecretA, lockTimeA, refundTimeout);
	}

	@Override
	protected byte[] buildRedeemMessage(byte[] secretA, String address) {
		return BitcoinACCTv1.buildRedeemMessage(secretA,secretB,address);
	}

	@Override
	protected byte[] getCodeBytesHash() {
		return BitcoinACCTv1.CODE_BYTES_HASH;
	}

	@Override
	protected String getSymbol() {
		return SYMBOL;
	}

	@Override
	protected String getName() {
		return NAME;
	}

	@SuppressWarnings("unused")
	@Override
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
			int lockTimeB = BitcoinACCTv1.calcLockTimeB(partnersOfferMessageTransactionTimestamp, lockTimeA);

			// Send trade info to AT
			byte[] messageData = BitcoinACCTv1.buildTradeMessage(partner.getAddress(), bitcoinPublicKeyHash, hashOfSecretA, lockTimeA, lockTimeB);
			MessageTransaction messageTransaction = sendMessage(repository, tradeAccount, messageData, atAddress);

			// Give AT time to process message
			BlockUtils.mintBlock(repository);

			// Send incorrect secrets to AT, from correct account
			byte[] wrongSecret = new byte[32];
			RANDOM.nextBytes(wrongSecret);
			messageData = BitcoinACCTv1.buildRedeemMessage(wrongSecret, secretB, partner.getAddress());
			messageTransaction = sendMessage(repository, partner, messageData, atAddress);

			// AT should NOT send funds in the next block
			ATStateData preRedeemAtStateData = repository.getATRepository().getLatestATState(atAddress);
			BlockUtils.mintBlock(repository);

			describeAt(repository, atAddress);

			// Check AT is NOT finished
			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			assertFalse(atData.getIsFinished());

			// AT should still be in TRADE mode
			CrossChainTradeData tradeData = BitcoinACCTv1.getInstance().populateTradeData(repository, atData);
			assertEquals(AcctMode.TRADING, tradeData.mode);

			long expectedBalance = partnersInitialBalance - messageTransaction.getTransactionData().getFee();
			long actualBalance = partner.getConfirmedBalance(Asset.QORT);

			assertEquals("Partner's balance incorrect", expectedBalance, actualBalance);

			// Send incorrect secrets to AT, from correct account
			messageData = BitcoinACCTv1.buildRedeemMessage(secretA, wrongSecret, partner.getAddress());
			messageTransaction = sendMessage(repository, partner, messageData, atAddress);

			// AT should NOT send funds in the next block
			BlockUtils.mintBlock(repository);

			describeAt(repository, atAddress);

			// Check AT is NOT finished
			atData = repository.getATRepository().fromATAddress(atAddress);
			assertFalse(atData.getIsFinished());

			// AT should still be in TRADE mode
			tradeData = BitcoinACCTv1.getInstance().populateTradeData(repository, atData);
			assertEquals(AcctMode.TRADING, tradeData.mode);

			// Check balances
			expectedBalance = partnersInitialBalance - messageTransaction.getTransactionData().getFee() * 2;
			actualBalance = partner.getConfirmedBalance(Asset.QORT);

			assertEquals("Partner's balance incorrect", expectedBalance, actualBalance);

			// Check eventual refund
			checkTradeRefund(repository, deployer, deployersInitialBalance, deployAtFee);
		}
	}

	@Override
	protected void describeRefundAt(CrossChainTradeData tradeData, Function<Long, String> epochMilliFormatter) {
		if (tradeData.mode != AcctMode.OFFERING && tradeData.mode != AcctMode.CANCELLED) {
			System.out.println(String.format("\trefund height: block %d,\n"
					+ "\tHASH160 of secret-A: %s,\n"
					+ "\tBitcoin P2SH-A nLockTime: %d (%s),\n"
					+ "\tBitcoin P2SH-B nLockTime: %d (%s),\n"
					+ "\ttrade partner: %s",
					tradeData.tradeRefundHeight,
					HashCode.fromBytes(tradeData.hashOfSecretA).toString().substring(0, 40),
					tradeData.lockTimeA, epochMilliFormatter.apply(tradeData.lockTimeA * 1000L),
					tradeData.lockTimeB, epochMilliFormatter.apply(tradeData.lockTimeB * 1000L),
					tradeData.qortalPartnerAddress));
		}
	}
}
