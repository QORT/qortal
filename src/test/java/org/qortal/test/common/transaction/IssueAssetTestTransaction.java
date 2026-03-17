package org.qortal.test.common.transaction;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.transaction.IssueAssetTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.test.common.AssetUtils;

import java.util.Random;

public class IssueAssetTestTransaction extends TestTransaction {

	public static TransactionData randomTransaction(Repository repository, PrivateKeyAccount account, boolean wantValid) throws DataException {
		Random random = new Random();

		String assetName = "test-asset-" + random.nextInt(1_000_000);
		String description = "random test asset";
		final long quantity = 1_000_000L;
		final boolean isDivisible = true;
		String data = AssetUtils.randomData();
		final boolean isUnspendable = false;

		return new IssueAssetTransactionData(generateBase(account), assetName, description, quantity, isDivisible, data, isUnspendable);
	}

}
