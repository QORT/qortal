package org.qortal.data.transaction;


import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CancelGroupBanTransactionData extends TransactionData {

	// Properties
	@Schema(description = "admin's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] adminPublicKey;
	@Schema(description = "group ID")
	private int groupId;
	@Schema(description = "member to unban from group", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String member;
	/** Reference to GROUP_BAN transaction, used to rebuild ban during orphaning. */
	// No need to ever expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] banReference;

	// Constructors

	// For JAXB
	protected CancelGroupBanTransactionData() {
		super(TransactionType.CANCEL_GROUP_BAN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.adminPublicKey;
	}

	/** From repository */
	public CancelGroupBanTransactionData(BaseTransactionData baseTransactionData, int groupId, String member, byte[] banReference) {
		super(TransactionType.CANCEL_GROUP_BAN, baseTransactionData);

		this.adminPublicKey = baseTransactionData.creatorPublicKey;
		this.groupId = groupId;
		this.member = member;
		this.banReference = banReference;
	}

	/** From network/API */
	public CancelGroupBanTransactionData(BaseTransactionData baseTransactionData, int groupId, String member) {
		this(baseTransactionData, groupId, member, null);
	}

	// Getters / setters

	public byte[] getAdminPublicKey() {
		return this.adminPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public String getMember() {
		return this.member;
	}

	public byte[] getBanReference() {
		return this.banReference;
	}

	public void setBanReference(byte[] banReference) {
		this.banReference = banReference;
	}

}
