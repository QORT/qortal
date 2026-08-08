package org.qortal.test.common.transaction;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.arbitrary.misc.Service;
import org.qortal.asset.Asset;
import org.qortal.data.PaymentData;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.ArbitraryTransactionData.DataType;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.utils.Amounts;

import java.util.ArrayList;
import java.util.List;

public class ArbitraryTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		final int version = 5;
		final Service service = Service.ARBITRARY_DATA;
		final int nonce = 0;
		final int size = 4 * 1024 * 1024;
		final String name = "TEST";
		final String identifier = "qortal_avatar";
		final ArbitraryTransactionData.Method method = ArbitraryTransactionData.Method.PUT;

		final byte[] secret = new byte[32];
		random.nextBytes(secret);

        final ArbitraryTransactionData.Compression compression = ArbitraryTransactionData.Compression.ZIP;

		final byte[] metadataHash = new byte[32];
		random.nextBytes(metadataHash);

		byte[] data = new byte[256];
		random.nextBytes(data);

		DataType dataType = DataType.RAW_DATA;

		String recipient = account.getAddress();
		final long assetId = Asset.QORT;
		long amount = 123L * Amounts.MULTIPLIER;

		List<PaymentData> payments = new ArrayList<>();
		payments.add(new PaymentData(recipient, assetId, amount));

		return new ArbitraryTransactionData(generateBase(account), version, service.value, nonce, size,name, identifier,
				method, secret, compression, data, dataType, metadataHash, payments);
	}

}
