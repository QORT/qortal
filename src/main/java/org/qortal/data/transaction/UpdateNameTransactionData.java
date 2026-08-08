package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.utils.Unicode;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class UpdateNameTransactionData extends TransactionData {

	// Properties

	@Schema(description = "owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;

	@Schema(description = "which name to update", example = "my-name")
	private String name;

	@Schema(description = "new name", example = "my-new-name")
	private String newName;

	@Schema(description = "replacement simple name-related info in JSON or text format", example = "Registered Name on the Qortal Chain")
	private String newData;

	// For internal use
	@XmlTransient
	@Schema(hidden = true)
	private String reducedNewName;

	// For internal use when orphaning
	@XmlTransient
	@Schema(hidden = true)
	private byte[] nameReference;

	// Constructors

	// For JAXB
	protected UpdateNameTransactionData() {
		super(TransactionType.UPDATE_NAME);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
		this.reducedNewName = this.newName != null ? Unicode.sanitize(this.newName) : null;
	}

	/** From repository */
	public UpdateNameTransactionData(BaseTransactionData baseTransactionData, String name, String newName, String newData, String reducedNewName, byte[] nameReference) {
		super(TransactionType.UPDATE_NAME, baseTransactionData);

		this.ownerPublicKey = baseTransactionData.creatorPublicKey;
		this.name = name;
		this.newName = newName;
		this.newData = newData;
		this.reducedNewName = reducedNewName;
		this.nameReference = nameReference;
	}

	/** From network */
	public UpdateNameTransactionData(BaseTransactionData baseTransactionData, String name, String newName, String newData) {
		this(baseTransactionData, name, newName, newData, Unicode.sanitize(newName), null);
	}

	// Getters / setters

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public String getName() {
		return this.name;
	}

	public String getNewName() {
		return this.newName;
	}

	public String getNewData() {
		return this.newData;
	}

	public String getReducedNewName() {
		return this.reducedNewName;
	}

	public byte[] getNameReference() {
		return this.nameReference;
	}

	public void setNameReference(byte[] nameReference) {
		this.nameReference = nameReference;
	}

}
