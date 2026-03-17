package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qortal.account.NullAccount;
import org.qortal.block.GenesisBlock;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = {TransactionData.class})
// JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("ACCOUNT_LEVEL")
public class AccountLevelTransactionData extends TransactionData {

	private String target;
	private int level;

	// Constructors

	// For JAXB
	protected AccountLevelTransactionData() {
		super(TransactionType.ACCOUNT_LEVEL);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		/*
		 *  If we're being constructed as part of the genesis block info inside blockchain config
		 *  and no specific creator's public key is supplied
		 *  then use null account's public key.
		 */
		if (parent instanceof GenesisBlock.GenesisInfo && this.creatorPublicKey == null)
			this.creatorPublicKey = NullAccount.PUBLIC_KEY;
	}

	/** From repository, network/API */
	public AccountLevelTransactionData(BaseTransactionData baseTransactionData,
			String target, int level) {
		super(TransactionType.ACCOUNT_LEVEL, baseTransactionData);

		this.target = target;
		this.level = level;
	}

	// Getters / setters

	public String getTarget() {
		return this.target;
	}

	public int getLevel() {
		return this.level;
	}

	// Re-expose to JAXB

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public byte[] getAccountLevelCreatorPublicKey() {
		return super.getCreatorPublicKey();
	}

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public void setAccountLevelCreatorPublicKey(byte[] creatorPublicKey) {
		super.setCreatorPublicKey(creatorPublicKey);
	}

}
