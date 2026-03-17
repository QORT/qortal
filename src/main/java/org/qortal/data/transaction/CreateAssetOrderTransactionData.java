package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
public class CreateAssetOrderTransactionData extends TransactionData {

	// Properties

	@Schema(description = "ID of asset on offer to give by order creator", example = "1")
	private long haveAssetId;

	@Schema(description = "ID of asset wanted to receive by order creator", example = "0")
	private long wantAssetId;

	@Schema(description = "amount of highest-assetID asset to trade")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long amount;

	@Schema(description = "price in lowest-assetID asset / highest-assetID asset")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long price;

	// Used by API - not always present

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String haveAssetName;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String wantAssetName;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long amountAssetId;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String amountAssetName;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String pricePair;

	// Constructors

	// For JAXB
	protected CreateAssetOrderTransactionData() {
		super(TransactionType.CREATE_ASSET_ORDER);
	}

	// Called before converting to JSON for API
	public void beforeMarshal(Marshaller m) {
		this.amountAssetId = Math.max(this.haveAssetId, this.wantAssetId);

		// If we don't have the extra asset name fields then we can't fill in the others
		if (this.haveAssetName == null)
			return;

		if (this.haveAssetId < this.wantAssetId) {
			this.amountAssetName = this.wantAssetName;
			this.pricePair = this.haveAssetName + "/" + this.wantAssetName;
		} else {
			this.amountAssetName = this.haveAssetName;
			this.pricePair = this.wantAssetName + "/" + this.haveAssetName;
		}
	}

	/** Constructs using data from repository, including optional asset names. */
	public CreateAssetOrderTransactionData(BaseTransactionData baseTransactionData,
			long haveAssetId, long wantAssetId, long amount, long price, String haveAssetName, String wantAssetName) {
		super(TransactionType.CREATE_ASSET_ORDER, baseTransactionData);

		this.haveAssetId = haveAssetId;
		this.wantAssetId = wantAssetId;
		this.amount = amount;
		this.price = price;

		this.haveAssetName = haveAssetName;
		this.wantAssetName = wantAssetName;
	}

	/** Constructor excluding optional asset names. */
	public CreateAssetOrderTransactionData(BaseTransactionData baseTransactionData, long haveAssetId, long wantAssetId, long amount, long price) {
		this(baseTransactionData, haveAssetId, wantAssetId, amount, price, null, null);
	}

	// Getters/Setters

	public long getHaveAssetId() {
		return this.haveAssetId;
	}

	public long getWantAssetId() {
		return this.wantAssetId;
	}

	public long getAmount() {
		return this.amount;
	}

	public long getPrice() {
		return this.price;
	}

	// Re-expose creatorPublicKey for this transaction type for JAXB
	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "order creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public byte[] getOrderCreatorPublicKey() {
		return this.creatorPublicKey;
	}

	@XmlElement(name = "creatorPublicKey")
	@Schema(name = "creatorPublicKey", description = "order creator's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	public void setOrderCreatorPublicKey(byte[] creatorPublicKey) {
		this.creatorPublicKey = creatorPublicKey;
	}

}
