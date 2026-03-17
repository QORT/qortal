package org.qortal.transaction;

import com.google.common.base.Utf8;
import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.crypto.Crypto;
import org.qortal.data.transaction.CreatePollTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.utils.Unicode;
import org.qortal.voting.Poll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreatePollTransaction extends Transaction {

	// Properties
	private CreatePollTransactionData createPollTransactionData;

	// Constructors

	public CreatePollTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.createPollTransactionData = (CreatePollTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.singletonList(this.createPollTransactionData.getOwner());
	}

	// Navigation

	public Account getOwner() {
		return new Account(this.repository, this.createPollTransactionData.getOwner());
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		// Check owner address is valid
		if (!Crypto.isValidAddress(this.createPollTransactionData.getOwner()))
			return ValidationResult.INVALID_ADDRESS;

		// Check name size bounds
		String pollName = this.createPollTransactionData.getPollName();
		int pollNameLength = Utf8.encodedLength(pollName);
		if (pollNameLength < Poll.MIN_NAME_SIZE || pollNameLength > Poll.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check description size bounds
		int pollDescriptionLength = Utf8.encodedLength(this.createPollTransactionData.getDescription());
		if (pollDescriptionLength < 1 || pollDescriptionLength > Poll.MAX_DESCRIPTION_SIZE)
			return ValidationResult.INVALID_DESCRIPTION_LENGTH;

		// Check name is in normalized form (no leading/trailing whitespace, etc.)
		if (!pollName.equals(Unicode.normalize(pollName)))
			return ValidationResult.NAME_NOT_NORMALIZED;

		// Check number of options
		List<PollOptionData> pollOptions = this.createPollTransactionData.getPollOptions();
		int pollOptionsCount = pollOptions.size();
		if (pollOptionsCount < 1 || pollOptionsCount > Poll.MAX_OPTIONS)
			return ValidationResult.INVALID_OPTIONS_COUNT;

		// Check each option
		List<String> optionNames = new ArrayList<>();
		for (PollOptionData pollOptionData : pollOptions) {
			// Check option length
			int optionNameLength = Utf8.encodedLength(pollOptionData.getOptionName());
			if (optionNameLength < 1 || optionNameLength > Poll.MAX_NAME_SIZE)
				return ValidationResult.INVALID_OPTION_LENGTH;

			// Check option is unique. NOTE: NOT case-sensitive!
			if (optionNames.contains(pollOptionData.getOptionName())) {
				return ValidationResult.DUPLICATE_OPTION;
			}

			optionNames.add(pollOptionData.getOptionName());
		}

		Account creator = getCreator();

		// Check creator has enough funds
		if (creator.getConfirmedBalance(Asset.QORT) < this.createPollTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public ValidationResult isProcessable() throws DataException {
		// Check the poll name isn't already taken
		if (this.repository.getVotingRepository().pollExists(this.createPollTransactionData.getPollName()))
			return ValidationResult.POLL_ALREADY_EXISTS;

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		// Publish poll to allow voting
		Poll poll = new Poll(this.repository, this.createPollTransactionData);
		poll.publish();
	}

	@Override
	public void orphan() throws DataException {
		// Unpublish poll
		Poll poll = new Poll(this.repository, this.createPollTransactionData.getPollName());
		poll.unpublish();
	}

}
