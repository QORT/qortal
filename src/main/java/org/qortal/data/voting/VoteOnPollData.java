package org.qortal.data.voting;

public class VoteOnPollData {

	// Properties
	private String pollName;
	private byte[] voterPublicKey;
	private int optionIndex;

	// Constructors

	// For JAXB
	protected VoteOnPollData() {
		super();
	}

	public VoteOnPollData(String pollName, byte[] voterPublicKey, int optionIndex) {
		this.pollName = pollName;
		this.voterPublicKey = voterPublicKey;
		this.optionIndex = optionIndex;
	}

	// Getters/setters

	public String getPollName() {
		return this.pollName;
	}

	public void setPollName(String pollName) {
		this.pollName = pollName;
	}

	public byte[] getVoterPublicKey() {
		return this.voterPublicKey;
	}

	public void setVoterPublicKey(byte[] voterPublicKey) {
		this.voterPublicKey = voterPublicKey;
	}

	public int getOptionIndex() {
		return this.optionIndex;
	}

	public void setOptionIndex(int optionIndex) {
		this.optionIndex = optionIndex;
	}

}
