package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = {TransactionData.class})
// JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("REWARD_SHARE")
public class RewardShareTransactionData extends TransactionData {

	@Schema(example = "minter_public_key")
	private byte[] minterPublicKey;

	private String recipient;

	@Schema(example = "reward_share_public_key")
	private byte[] rewardSharePublicKey;

	@Schema(description = "Percentage of block rewards that minter shares to recipient, or negative value to cancel existing reward-share")
	@XmlJavaTypeAdapter(value = org.qortal.api.RewardSharePercentTypeAdapter.class)
	private int sharePercent;

	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private Integer previousSharePercent;

	// Constructors

	// For JAXB
	protected RewardShareTransactionData() {
		super(TransactionType.REWARD_SHARE);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.minterPublicKey;
	}

	/** From repository */
	public RewardShareTransactionData(BaseTransactionData baseTransactionData,
			String recipient, byte[] rewardSharePublicKey, int sharePercent, Integer previousSharePercent) {
		super(TransactionType.REWARD_SHARE, baseTransactionData);

		this.minterPublicKey = baseTransactionData.creatorPublicKey;
		this.recipient = recipient;
		this.rewardSharePublicKey = rewardSharePublicKey;
		this.sharePercent = sharePercent;
		this.previousSharePercent = previousSharePercent;
	}

	/** From network/API */
	public RewardShareTransactionData(BaseTransactionData baseTransactionData,
			String recipient, byte[] rewardSharePublicKey, int sharePercent) {
		this(baseTransactionData, recipient, rewardSharePublicKey, sharePercent, null);
	}

	// Getters / setters

	public byte[] getMinterPublicKey() {
		return this.minterPublicKey;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public byte[] getRewardSharePublicKey() {
		return this.rewardSharePublicKey;
	}

	public int getSharePercent() {
		return this.sharePercent;
	}

	public Integer getPreviousSharePercent() {
		return this.previousSharePercent;
	}

	public void setPreviousSharePercent(Integer previousSharePercent) {
		this.previousSharePercent = previousSharePercent;
	}

}
