package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
// JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("ADD_GROUP_ADMIN")
public class AddGroupAdminTransactionData extends TransactionData {

	// Properties
	@Schema(description = "group owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;

	@Schema(description = "group ID")
	private int groupId;

	@Schema(description = "member to promote to admin", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String member; 

	// Constructors

	// For JAXB
	protected AddGroupAdminTransactionData() {
		super(TransactionType.ADD_GROUP_ADMIN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	public AddGroupAdminTransactionData(BaseTransactionData baseTransactionData, int groupId, String member) {
		super(TransactionType.ADD_GROUP_ADMIN, baseTransactionData);

		this.ownerPublicKey = baseTransactionData.creatorPublicKey;
		this.groupId = groupId;
		this.member = member;
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public String getMember() {
		return this.member;
	}

}
