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
public class UpdateAssetTransactionData extends TransactionData {

	// Properties
	private long assetId;
	@Schema(description = "asset owner's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] ownerPublicKey;
	@Schema(description = "asset new owner's address", example = "QgV4s3xnzLhVBEJxcYui4u4q11yhUHsd9v")
	private String newOwner;
	@Schema(description = "asset new description", example = "Gold asset - 1 unit represents one 1kg of gold")
	private String newDescription;
	@Schema(description = "new asset-related data, typically JSON", example = "{\"logo\": \"data:image/jpeg;base64,/9j/4AAQSkZJRgA==\"}")
	private String newData;
	// No need to expose this via API
	@XmlTransient
	@Schema(hidden = true)
	private byte[] orphanReference;

	// Constructors

	// For JAXB
	protected UpdateAssetTransactionData() {
		super(TransactionType.UPDATE_ASSET);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.ownerPublicKey;
	}

	/** From repository */
	public UpdateAssetTransactionData(BaseTransactionData baseTransactionData,
			long assetId, String newOwner, String newDescription, String newData, byte[] orphanReference) {
		super(TransactionType.UPDATE_ASSET, baseTransactionData);

		this.assetId = assetId;
		this.ownerPublicKey = baseTransactionData.creatorPublicKey;
		this.newOwner = newOwner;
		this.newDescription = newDescription;
		this.newData = newData;
		this.orphanReference = orphanReference;
	}

	/** From network/API */
	public UpdateAssetTransactionData(BaseTransactionData baseTransactionData, long assetId, String newOwner, String newDescription, String newData) {
		this(baseTransactionData, assetId, newOwner, newDescription, newData, null);
	}

	// Getters/Setters

	public long getAssetId() {
		return this.assetId;
	}

	public byte[] getOwnerPublicKey() {
		return this.ownerPublicKey;
	}

	public String getNewOwner() {
		return this.newOwner;
	}

	public String getNewDescription() {
		return this.newDescription;
	}

	public String getNewData() {
		return this.newData;
	}

	public byte[] getOrphanReference() {
		return this.orphanReference;
	}

	public void setOrphanReference(byte[] orphanReference) {
		this.orphanReference = orphanReference;
	}

}
