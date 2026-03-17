package org.qortal.repository.hsqldb;

import org.qortal.data.voting.PollData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.data.voting.VoteOnPollData;
import org.qortal.repository.DataException;
import org.qortal.repository.VotingRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HSQLDBVotingRepository implements VotingRepository {

	protected HSQLDBRepository repository;

	public HSQLDBVotingRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	// Polls

	@Override
	public List<PollData> getAllPolls(Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(512);

		sql.append("SELECT poll_name, description, creator, owner, published_when FROM Polls ORDER BY poll_name");

		if (reverse != null && reverse)
			sql.append(" DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<PollData> polls = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString())) {
			if (resultSet == null)
				return polls;

			do {
				String pollName = resultSet.getString(1);
				String description = resultSet.getString(2);
				byte[] creatorPublicKey = resultSet.getBytes(3);
				String owner = resultSet.getString(4);
				long published = resultSet.getLong(5);

				String optionsSql = "SELECT option_name FROM PollOptions WHERE poll_name = ? ORDER BY option_index ASC";
				try (ResultSet optionsResultSet = this.repository.checkedExecute(optionsSql, pollName)) {
					if (optionsResultSet == null)
						return null;

					List<PollOptionData> pollOptions = new ArrayList<>();

					// NOTE: do-while because checkedExecute() above has already called rs.next() for us
					do {
						String optionName = optionsResultSet.getString(1);

						pollOptions.add(new PollOptionData(optionName));
					} while (optionsResultSet.next());

					polls.add(new PollData(creatorPublicKey, owner, pollName, description, pollOptions, published));
				}
				
			} while (resultSet.next());

			return polls;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch polls from repository", e);
		}
	}

	@Override
	public PollData fromPollName(String pollName) throws DataException {
		String sql = "SELECT description, creator, owner, published_when FROM Polls WHERE poll_name = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, pollName)) {
			if (resultSet == null)
				return null;

			String description = resultSet.getString(1);
			byte[] creatorPublicKey = resultSet.getBytes(2);
			String owner = resultSet.getString(3);
			long published = resultSet.getLong(4);

			String optionsSql = "SELECT option_name FROM PollOptions WHERE poll_name = ? ORDER BY option_index ASC";
			try (ResultSet optionsResultSet = this.repository.checkedExecute(optionsSql, pollName)) {
				if (optionsResultSet == null)
					return null;

				List<PollOptionData> pollOptions = new ArrayList<>();

				// NOTE: do-while because checkedExecute() above has already called rs.next() for us
				do {
					String optionName = optionsResultSet.getString(1);

					pollOptions.add(new PollOptionData(optionName));
				} while (optionsResultSet.next());

				return new PollData(creatorPublicKey, owner, pollName, description, pollOptions, published);
			}
		} catch (SQLException e) {
			throw new DataException("Unable to fetch poll from repository", e);
		}
	}

	@Override
	public boolean pollExists(String pollName) throws DataException {
		try {
			return this.repository.exists("Polls", "poll_name = ?", pollName);
		} catch (SQLException e) {
			throw new DataException("Unable to check for poll in repository", e);
		}
	}

	@Override
	public void save(PollData pollData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("Polls");

		saveHelper.bind("poll_name", pollData.getPollName()).bind("description", pollData.getDescription()).bind("creator", pollData.getCreatorPublicKey())
				.bind("owner", pollData.getOwner()).bind("published_when", pollData.getPublished());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save poll into repository", e);
		}

		// Now attempt to save poll options
		List<PollOptionData> pollOptions = pollData.getPollOptions();
		for (int optionIndex = 0; optionIndex < pollOptions.size(); ++optionIndex) {
			PollOptionData pollOptionData = pollOptions.get(optionIndex);

			HSQLDBSaver optionSaveHelper = new HSQLDBSaver("PollOptions");

			optionSaveHelper.bind("poll_name", pollData.getPollName()).bind("option_index", optionIndex).bind("option_name", pollOptionData.getOptionName());

			try {
				optionSaveHelper.execute(this.repository);
			} catch (SQLException e) {
				throw new DataException("Unable to save poll option into repository", e);
			}
		}
	}

	@Override
	public void delete(String pollName) throws DataException {
		// NOTE: The corresponding rows in PollOptions are deleted automatically by the database
		// thanks to "ON DELETE CASCADE" in the PollOptions' FOREIGN KEY definition.
		try {
			this.repository.delete("Polls", "poll_name = ?", pollName);
		} catch (SQLException e) {
			throw new DataException("Unable to delete poll from repository", e);
		}
	}

	// Votes

	@Override
	public List<VoteOnPollData> getVotes(String pollName) throws DataException {
		String sql = "SELECT voter, option_index FROM PollVotes WHERE poll_name = ?";
		List<VoteOnPollData> votes = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, pollName)) {
			if (resultSet == null)
				return votes;

			// NOTE: do-while because checkedExecute() above has already called rs.next() for us
			do {
				byte[] voterPublicKey = resultSet.getBytes(1);
				int optionIndex = resultSet.getInt(2);

				votes.add(new VoteOnPollData(pollName, voterPublicKey, optionIndex));
			} while (resultSet.next());

			return votes;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch poll votes from repository", e);
		}
	}

	@Override
	public VoteOnPollData getVote(String pollName, byte[] voterPublicKey) throws DataException {
		String sql = "SELECT option_index FROM PollVotes WHERE poll_name = ? AND voter = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, pollName, voterPublicKey)) {
			if (resultSet == null)
				return null;

			int optionIndex = resultSet.getInt(1);

			return new VoteOnPollData(pollName, voterPublicKey, optionIndex);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch poll vote from repository", e);
		}
	}

	@Override
	public void save(VoteOnPollData voteOnPollData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("PollVotes");

		saveHelper.bind("poll_name", voteOnPollData.getPollName()).bind("voter", voteOnPollData.getVoterPublicKey())
				.bind("option_index", voteOnPollData.getOptionIndex());

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save poll vote into repository", e);
		}
	}

	@Override
	public void delete(String pollName, byte[] voterPublicKey) throws DataException {
		try {
			this.repository.delete("PollVotes", "poll_name = ? AND voter = ?", pollName, voterPublicKey);
		} catch (SQLException e) {
			throw new DataException("Unable to delete poll vote from repository", e);
		}
	}

}
