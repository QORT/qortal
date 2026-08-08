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
public class RemoveGroupAdminTransactionData extends TransactionData {

	// Properties
	@Schema(description = "group owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;
	@Schema(description = "group ID")
	private int groupId;
	@Schema(description = "admin to demote", example = "QixPbJUwsaHsVEofJdozU9zgVqkK6aYhrK")
	private String admin; 
	/** Reference to transaction that triggered adminship. */
	// For internal use when orphaning
	@XmlTransient
	@Schema(hidden = true)
	private byte[] adminReference;

	// Constructors

	// For JAXB
	protected RemoveGroupAdminTransactionData() {
		super(TransactionType.REMOVE_GROUP_ADMIN);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	/** From repository */
	public RemoveGroupAdminTransactionData(BaseTransactionData baseTransactionData, int groupId, String admin, byte[] adminReference) {
		super(TransactionType.REMOVE_GROUP_ADMIN, baseTransactionData);

		this.ownerPublicKey = baseTransactionData.creatorPublicKey;
		this.groupId = groupId;
		this.admin = admin;
		this.adminReference = adminReference;
	}

	/** From network/API */
	public RemoveGroupAdminTransactionData(BaseTransactionData baseTransactionData, int groupId, String admin) {
		this(baseTransactionData, groupId, admin, null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public String getAdmin() {
		return this.admin;
	}

	public byte[] getAdminReference() {
		return this.adminReference;
	}

	public void setAdminReference(byte[] adminReference) {
		this.adminReference = adminReference;
	}

}
