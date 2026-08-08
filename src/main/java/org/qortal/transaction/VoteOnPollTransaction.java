package org.qortal.transaction;

import com.google.common.base.Utf8;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.account.Account;
import org.qortal.asset.Asset;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.VoteOnPollTransactionData;
import org.qortal.data.voting.PollData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.data.voting.VoteOnPollData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.VotingRepository;
import org.qortal.utils.Unicode;
import org.qortal.voting.Poll;

import java.util.Collections;
import java.util.List;

public class VoteOnPollTransaction extends Transaction {

	private static final Logger LOGGER = LogManager.getLogger(VoteOnPollTransaction.class);

	// Properties
	private VoteOnPollTransactionData voteOnPollTransactionData;

	// Constructors

	public VoteOnPollTransaction(Repository repository, TransactionData transactionData) {
		super(repository, transactionData);

		this.voteOnPollTransactionData = (VoteOnPollTransactionData) this.transactionData;
	}

	// More information

	@Override
	public List<String> getRecipientAddresses() throws DataException {
		return Collections.emptyList();
	}

	// Navigation

	public Account getVoter() {
		return this.getCreator();
	}

	// Processing

	@Override
	public ValidationResult isValid() throws DataException {
		String pollName = this.voteOnPollTransactionData.getPollName();

		// Check name size bounds
		int pollNameLength = Utf8.encodedLength(pollName);
		if (pollNameLength < 1 || pollNameLength > Poll.MAX_NAME_SIZE)
			return ValidationResult.INVALID_NAME_LENGTH;

		// Check name is in normalized form (no leading/trailing whitespace, etc.)
		if (!pollName.equals(Unicode.normalize(pollName)))
			return ValidationResult.NAME_NOT_NORMALIZED;

		VotingRepository votingRepository = this.repository.getVotingRepository();

		// Check poll exists
		PollData pollData = votingRepository.fromPollName(pollName);
		if (pollData == null)
			return ValidationResult.POLL_DOES_NOT_EXIST;

		// Check poll option index is within bounds
		List<PollOptionData> pollOptions = pollData.getPollOptions();
		int optionIndex = this.voteOnPollTransactionData.getOptionIndex();

		if (optionIndex < 0 || optionIndex > pollOptions.size() - 1)
			return ValidationResult.POLL_OPTION_DOES_NOT_EXIST;

		// Check if vote already exists
		VoteOnPollData voteOnPollData = votingRepository.getVote(pollName, this.voteOnPollTransactionData.getVoterPublicKey());
		if (voteOnPollData != null && voteOnPollData.getOptionIndex() == optionIndex)
			return ValidationResult.ALREADY_VOTED_FOR_THAT_OPTION;

		// Check reference is correct
		Account voter = getVoter();

		// Check voter has enough funds
		if (voter.getConfirmedBalance(Asset.QORT) < this.voteOnPollTransactionData.getFee())
			return ValidationResult.NO_BALANCE;

		return ValidationResult.OK;
	}

	@Override
	public void preProcess() throws DataException {
		// Nothing to do
	}

	@Override
	public void process() throws DataException {
		String pollName = this.voteOnPollTransactionData.getPollName();

		Account voter = getVoter();

		VotingRepository votingRepository = this.repository.getVotingRepository();

		// Check for previous vote so we can save option in case of orphaning
		VoteOnPollData previousVoteOnPollData = votingRepository.getVote(pollName, this.voteOnPollTransactionData.getVoterPublicKey());
		if (previousVoteOnPollData != null) {
			voteOnPollTransactionData.setPreviousOptionIndex(previousVoteOnPollData.getOptionIndex());
			LOGGER.trace(() -> String.format("Previous vote by %s on poll \"%s\" was option index %d",
					voter.getAddress(), pollName, previousVoteOnPollData.getOptionIndex()));
		}

		// Save this transaction, now with possible previous vote
		this.repository.getTransactionRepository().save(voteOnPollTransactionData);

		// Apply vote to poll
		LOGGER.trace(() -> String.format("Vote by %s on poll \"%s\" with option index %d",
				voter.getAddress(), pollName, this.voteOnPollTransactionData.getOptionIndex()));
		VoteOnPollData newVoteOnPollData = new VoteOnPollData(pollName, this.voteOnPollTransactionData.getVoterPublicKey(),
				this.voteOnPollTransactionData.getOptionIndex());
		votingRepository.save(newVoteOnPollData);
	}

	@Override
	public void orphan() throws DataException {
		Account voter = getVoter();

		// Does this transaction have previous vote info?
		VotingRepository votingRepository = this.repository.getVotingRepository();
		Integer previousOptionIndex = this.voteOnPollTransactionData.getPreviousOptionIndex();
		if (previousOptionIndex != null) {
			// Reinstate previous vote
			LOGGER.trace(() -> String.format("Reinstating previous vote by %s on poll \"%s\" with option index %d",
					voter.getAddress(), this.voteOnPollTransactionData.getPollName(), previousOptionIndex));
			VoteOnPollData previousVoteOnPollData = new VoteOnPollData(this.voteOnPollTransactionData.getPollName(), this.voteOnPollTransactionData.getVoterPublicKey(),
					previousOptionIndex);
			votingRepository.save(previousVoteOnPollData);
		} else {
			// Delete vote
			LOGGER.trace(() -> String.format("Deleting vote by %s on poll \"%s\" with option index %d",
					voter.getAddress(), this.voteOnPollTransactionData.getPollName(), this.voteOnPollTransactionData.getOptionIndex()));
			votingRepository.delete(this.voteOnPollTransactionData.getPollName(), this.voteOnPollTransactionData.getVoterPublicKey());
		}

		// Save this transaction, with removed previous vote info
		this.voteOnPollTransactionData.setPreviousOptionIndex(null);
		this.repository.getTransactionRepository().save(this.voteOnPollTransactionData);
	}

}
