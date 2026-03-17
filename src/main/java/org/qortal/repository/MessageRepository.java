package org.qortal.repository;

import org.qortal.data.transaction.MessageTransactionData;

import java.util.List;

public interface MessageRepository {

	/**
	 * Returns list of confirmed MESSAGE transaction data matching (some) participants.
	 * <p>
	 * At least one of <tt>senderPublicKey</tt> or <tt>recipient</tt> must be specified.
	 * <p>
	 * @throws DataException
	 */
	public List<MessageTransactionData> getMessagesByParticipants(byte[] senderPublicKey,
			String recipient, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Does a MESSAGE exist with matching sender (pubkey), recipient and message payload?
	 * <p>
	 * Includes both confirmed and unconfirmed transactions!
	 * <p>
	 * @param senderPublicKey
	 * @param recipient
	 * @param messageData
	 * @return true if a message exists, false otherwise
	 */
	public boolean exists(byte[] senderPublicKey, String recipient, byte[] messageData) throws DataException;

}
