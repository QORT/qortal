package org.qortal.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.crosschain.BitcoinACCTv1;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.data.transaction.PresenceTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.PresenceTransaction;
import org.qortal.transaction.PresenceTransaction.PresenceType;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.utils.NTP;

import com.google.common.primitives.Longs;

import static org.junit.Assert.*;

public class PresenceTests extends Common {

	private static final byte[] BITCOIN_PKH = new byte[20];
	private static final byte[] HASH_OF_SECRET_B = new byte[32];

	private PrivateKeyAccount signer;
	private Repository repository;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();

		this.repository = RepositoryManager.getRepository();
		this.signer = Common.getTestAccount(this.repository, "bob");

		// We need to create corresponding test trade offer
		byte[] creationBytes = BitcoinACCTv1.buildQortalAT(this.signer.getAddress(), BITCOIN_PKH, HASH_OF_SECRET_B,
				0L, 0L,
				7 * 24 * 60 * 60);

		long txTimestamp = NTP.getTime();
		byte[] lastReference = this.signer.getLastReference();

		long fee = 0;
		String name = "QORT-BTC cross-chain trade";
		String description = "Qortal-Bitcoin cross-chain trade";
		String atType = "ACCT";
		String tags = "QORT-BTC ACCT";

		BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, this.signer.getPublicKey(), fee, null);
		TransactionData deployAtTransactionData = new DeployAtTransactionData(baseTransactionData, name, description, atType, tags, creationBytes, 1L, Asset.QORT);

		Transaction deployAtTransaction = new DeployAtTransaction(repository, deployAtTransactionData);

		fee = deployAtTransaction.calcRecommendedFee();
		deployAtTransactionData.setFee(fee);

		TransactionUtils.signAndImportValid(this.repository, deployAtTransactionData, this.signer);
		BlockUtils.mintBlock(this.repository);
	}

	@After
	public void afterTest() throws DataException {
		if (this.repository != null)
			this.repository.close();

		this.repository = null;
	}

	@Test
	public void validityTests() throws DataException {
		long timestamp = System.currentTimeMillis();
		byte[] timestampBytes = Longs.toByteArray(timestamp);

		byte[] timestampSignature = this.signer.sign(timestampBytes);

		assertTrue(isValid(Group.NO_GROUP, this.signer, timestamp, timestampSignature));

		PrivateKeyAccount nonTrader = Common.getTestAccount(repository, "alice");
		assertFalse(isValid(Group.NO_GROUP, nonTrader, timestamp, timestampSignature));
	}

	@Test
	public void newestOnlyTests() throws DataException {
		long OLDER_TIMESTAMP = System.currentTimeMillis() - 2000L;
		long NEWER_TIMESTAMP = OLDER_TIMESTAMP + 1000L;

		PresenceTransaction older = buildPresenceTransaction(Group.NO_GROUP, this.signer, OLDER_TIMESTAMP, null);
		older.computeNonce();
		TransactionUtils.signAndImportValid(repository, older.getTransactionData(), this.signer);

		assertTrue(this.repository.getTransactionRepository().exists(older.getTransactionData().getSignature()));

		PresenceTransaction newer = buildPresenceTransaction(Group.NO_GROUP, this.signer, NEWER_TIMESTAMP, null);
		newer.computeNonce();
		TransactionUtils.signAndImportValid(repository, newer.getTransactionData(), this.signer);

		assertTrue(this.repository.getTransactionRepository().exists(newer.getTransactionData().getSignature()));
		assertFalse(this.repository.getTransactionRepository().exists(older.getTransactionData().getSignature()));
	}

	private boolean isValid(int txGroupId, PrivateKeyAccount signer, long timestamp, byte[] timestampSignature) throws DataException {
		Transaction transaction = buildPresenceTransaction(txGroupId, signer, timestamp, timestampSignature);
		return transaction.isValidUnconfirmed() == ValidationResult.OK;
	}

	private PresenceTransaction buildPresenceTransaction(int txGroupId, PrivateKeyAccount signer, long timestamp, byte[] timestampSignature) throws DataException {
		int nonce = 0;

		byte[] reference = signer.getLastReference();
		byte[] creatorPublicKey = signer.getPublicKey();
		long fee = 0L;

		if (timestampSignature == null)
			timestampSignature = this.signer.sign(Longs.toByteArray(timestamp));

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, null);
		PresenceTransactionData transactionData = new PresenceTransactionData(baseTransactionData, nonce, PresenceType.TRADE_BOT, timestampSignature);

		return new PresenceTransaction(this.repository, transactionData);
	}

}
