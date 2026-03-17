package org.qortal.network.message;

import com.google.common.primitives.Ints;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.TransactionTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TransactionsMessage extends Message {

	private List<TransactionData> transactions;

	public TransactionsMessage(List<TransactionData> transactions) throws MessageException {
		super(MessageType.TRANSACTIONS);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(Ints.toByteArray(transactions.size()));

			for (int i = 0; i < transactions.size(); ++i) {
				TransactionData transactionData = transactions.get(i);

				byte[] serializedTransactionData = TransactionTransformer.toBytes(transactionData);
				bytes.write(serializedTransactionData);
			}

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		} catch (TransformationException e) {
			throw new MessageException(e.getMessage(), e);
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private TransactionsMessage(int id, List<TransactionData> transactions) {
		super(id, MessageType.TRANSACTIONS);

		this.transactions = transactions;
	}

	public List<TransactionData> getTransactions() {
		return this.transactions;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException {
		try {
			final int transactionCount = byteBuffer.getInt();

			List<TransactionData> transactions = new ArrayList<>();

			for (int i = 0; i < transactionCount; ++i) {
				TransactionData transactionData = TransactionTransformer.fromByteBuffer(byteBuffer);
				transactions.add(transactionData);
			}

			if (byteBuffer.hasRemaining()) {
				throw new BufferUnderflowException();
			}

			return new TransactionsMessage(id, transactions);

		} catch (TransformationException e) {
			throw new MessageException(e.getMessage(), e);
		}
	}

}
