package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
@XmlDiscriminatorValue("VOTE_ON_POLL")
public class VoteOnPollTransactionData extends TransactionData {

	// Properties
	@Schema(description = "Vote creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] voterPublicKey;
	private String pollName;
	private int optionIndex;
	// For internal use when orphaning
	@XmlTransient
	@Schema(hidden = true)
	private Integer previousOptionIndex;

	// Constructors

	// For JAXB
	protected VoteOnPollTransactionData() {
		super(TransactionType.VOTE_ON_POLL);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.voterPublicKey;
	}

	/** From repository */
	public VoteOnPollTransactionData(BaseTransactionData baseTransactionData, String pollName, int optionIndex, Integer previousOptionIndex) {
		super(TransactionType.VOTE_ON_POLL, baseTransactionData);

		this.voterPublicKey = baseTransactionData.creatorPublicKey;
		this.pollName = pollName;
		this.optionIndex = optionIndex;
		this.previousOptionIndex = previousOptionIndex;
	}

	/** From network/API */
	public VoteOnPollTransactionData(BaseTransactionData baseTransactionData, String pollName, int optionIndex) {
		this(baseTransactionData, pollName, optionIndex, null);
	}

	// Getters / setters

	public byte[] getVoterPublicKey() {
		return this.voterPublicKey;
	}

	public String getPollName() {
		return this.pollName;
	}

	public int getOptionIndex() {
		return this.optionIndex;
	}

	public Integer getPreviousOptionIndex() {
		return this.previousOptionIndex;
	}

	public void setPreviousOptionIndex(Integer previousOptionIndex) {
		this.previousOptionIndex = previousOptionIndex;
	}

}
