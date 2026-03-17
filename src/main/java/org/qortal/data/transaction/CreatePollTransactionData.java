package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qortal.data.voting.PollOptionData;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.List;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
@XmlDiscriminatorValue("CREATE_POLL")
public class CreatePollTransactionData extends TransactionData {


	@Schema(description = "Poll creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] pollCreatorPublicKey;
	
	// Properties
	private String owner;
	private String pollName;
	private String description;
	private List<PollOptionData> pollOptions;

	// Constructors

	// For JAXB
	protected CreatePollTransactionData() {
		super(TransactionType.CREATE_POLL);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.pollCreatorPublicKey;
	}
	
	public CreatePollTransactionData(BaseTransactionData baseTransactionData,
			String owner, String pollName, String description, List<PollOptionData> pollOptions) {
		super(Transaction.TransactionType.CREATE_POLL, baseTransactionData);

		this.creatorPublicKey = baseTransactionData.creatorPublicKey;
		this.owner = owner;
		this.pollName = pollName;
		this.description = description;
		this.pollOptions = pollOptions;
	}

	// Getters/setters

	public byte[] getPollCreatorPublicKey() { return this.creatorPublicKey; }
	public String getOwner() {
		return this.owner;
	}

	public String getPollName() {
		return this.pollName;
	}

	public String getDescription() {
		return this.description;
	}

	public List<PollOptionData> getPollOptions() {
		return this.pollOptions;
	}

}
