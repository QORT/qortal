package org.qortal.data.voting;

import java.util.List;

public class PollData {

	// Properties
	private byte[] creatorPublicKey;
	private String owner;
	private String pollName;
	private String description;
	private List<PollOptionData> pollOptions;
	private long published;

	// Constructors

	// For JAXB
	protected PollData() {
		super();
	}

	public PollData(byte[] creatorPublicKey, String owner, String pollName, String description, List<PollOptionData> pollOptions, long published) {
		this.creatorPublicKey = creatorPublicKey;
		this.owner = owner;
		this.pollName = pollName;
		this.description = description;
		this.pollOptions = pollOptions;
		this.published = published;
	}

	// Getters/setters

	public byte[] getCreatorPublicKey() {
		return this.creatorPublicKey;
	}

	public void setCreatorPublicKey(byte[] creatorPublicKey) {
		this.creatorPublicKey = creatorPublicKey;
	}

	public String getOwner() {
		return this.owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getPollName() {
		return this.pollName;
	}

	public void setPollName(String pollName) {
		this.pollName = pollName;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<PollOptionData> getPollOptions() {
		return this.pollOptions;
	}

	public void setPollOptions(List<PollOptionData> pollOptions) {
		this.pollOptions = pollOptions;
	}

	public long getPublished() {
		return this.published;
	}

	public void setPublished(long published) {
		this.published = published;
	}

}
