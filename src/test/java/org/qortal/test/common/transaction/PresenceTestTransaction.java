package org.qortal.test.common.transaction;

import com.google.common.primitives.Longs;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.transaction.PresenceTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transaction.PresenceTransaction.PresenceType;
import org.qortal.utils.NTP;

public class PresenceTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final int nonce = 0;

		byte[] tradePrivateKey = new byte[32];
		PrivateKeyAccount tradeNativeAccount = new PrivateKeyAccount(repository, tradePrivateKey);
		long timestamp = NTP.getTime();
		byte[] timestampSignature = tradeNativeAccount.sign(Longs.toByteArray(timestamp));

		return new PresenceTransactionData(generateBase(account), nonce, PresenceType.TRADE_BOT, timestampSignature);
	}

}
