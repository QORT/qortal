package org.qortal.transform.transaction;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.qortal.data.transaction.GenesisTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class GenesisTransactionTransformer extends TransactionTransformer {

	protected static final TransactionLayout layout = null;

	public static TransactionData fromByteBuffer(ByteBuffer byteBuffer) throws TransformationException {
		throw new TransformationException("Serialized GENESIS transactions should not exist!");
	}

	public static int getDataLength(TransactionData transactionData) throws TransformationException {
		throw new TransformationException("Serialized GENESIS transactions should not exist!");
	}

	// Used when generating fake signatures for genesis block
	public static byte[] toBytes(TransactionData transactionData) throws TransformationException {
		try {
			GenesisTransactionData genesisTransactionData = (GenesisTransactionData) transactionData;

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();

			bytes.write(Ints.toByteArray(genesisTransactionData.getType().value));

			bytes.write(Longs.toByteArray(genesisTransactionData.getTimestamp()));

			Serialization.serializeAddress(bytes, genesisTransactionData.getRecipient());

			bytes.write(Longs.toByteArray(genesisTransactionData.getAmount()));

			bytes.write(Longs.toByteArray(genesisTransactionData.getAssetId()));

			return bytes.toByteArray();
		} catch (IOException | ClassCastException e) {
			throw new TransformationException(e);
		}
	}

}
