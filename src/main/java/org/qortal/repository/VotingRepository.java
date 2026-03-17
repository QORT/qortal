package org.qortal.repository;

import org.qortal.data.voting.PollData;
import org.qortal.data.voting.VoteOnPollData;

import java.util.List;

public interface VotingRepository {

	// Polls

	public List<PollData> getAllPolls(Integer limit, Integer offset, Boolean reverse) throws DataException;

	public PollData fromPollName(String pollName) throws DataException;

	public boolean pollExists(String pollName) throws DataException;

	public void save(PollData pollData) throws DataException;

	public void delete(String pollName) throws DataException;

	// Votes

	public List<VoteOnPollData> getVotes(String pollName) throws DataException;

	public VoteOnPollData getVote(String pollName, byte[] voterPublicKey) throws DataException;

	public void save(VoteOnPollData voteOnPollData) throws DataException;

	public void delete(String pollName, byte[] voterPublicKey) throws DataException;

}
