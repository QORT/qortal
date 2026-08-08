package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qortal.account.NullAccount;
import org.qortal.block.GenesisBlock;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.utils.Unicode;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
// JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("ISSUE_ASSET")
public class IssueAssetTransactionData extends TransactionData {

	// Properties

	// assetId can be null but assigned during save() or during load from repository
	@Schema(accessMode = AccessMode.READ_ONLY)
	private Long assetId = null;

	@Schema(description = "asset issuer's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] issuerPublicKey;

	@Schema(description = "asset name", example = "GOLD")
	private String assetName;

	@Schema(description = "asset description", example = "Gold asset - 1 unit represents one 1kg of gold")
	private String description;

	@Schema(description = "total supply of asset in existence (integer)", example = "1000")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long quantity;

	@Schema(description = "whether asset quantities can be fractional", example = "true")
	private boolean isDivisible;

	@Schema(description = "non-human-readable asset-related data, typically JSON", example = "{\"logo\": \"data:image/jpeg;base64,/9j/4AAQSkZJRgA==\"}")
	private String data;

	@Schema(description = "whether non-owner holders of asset are barred from using asset", example = "false")
	private boolean isUnspendable;

	// For internal use
	@Schema(hidden = true)
	@XmlTransient
	private String reducedAssetName;

	// Constructors

	// For JAXB
	protected IssueAssetTransactionData() {
		super(TransactionType.ISSUE_ASSET);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		/*
		 *  If we're being constructed as part of the genesis block info inside blockchain config
		 *  and no specific issuer's public key is supplied
		 *  then use null account's public key.
		 */
		if (parent instanceof GenesisBlock.GenesisInfo && this.issuerPublicKey == null)
			this.issuerPublicKey = NullAccount.PUBLIC_KEY;

		/*
		 *  If we're being constructed as part of the genesis block info inside blockchain config
		 *  then we need to construct 'reduced' form of asset name.
		 */
		if (parent instanceof GenesisBlock.GenesisInfo && this.reducedAssetName == null)
			this.reducedAssetName = Unicode.sanitize(this.assetName);

		this.creatorPublicKey = this.issuerPublicKey;
	}

	/** From repository */
	public IssueAssetTransactionData(BaseTransactionData baseTransactionData, Long assetId, String assetName,
			String description, long quantity, boolean isDivisible, String data, boolean isUnspendable,
			String reducedAssetName) {
		super(TransactionType.ISSUE_ASSET, baseTransactionData);

		this.assetId = assetId;
		this.issuerPublicKey = baseTransactionData.creatorPublicKey;
		this.assetName = assetName;
		this.description = description;
		this.quantity = quantity;
		this.isDivisible = isDivisible;
		this.data = data;
		this.isUnspendable = isUnspendable;
		this.reducedAssetName = reducedAssetName;
	}

	/** From network/API */
	public IssueAssetTransactionData(BaseTransactionData baseTransactionData, String assetName, String description,
			long quantity, boolean isDivisible, String data, boolean isUnspendable) {
		this(baseTransactionData, null, assetName, description, quantity, isDivisible, data, isUnspendable,
				Unicode.sanitize(assetName));
	}

	// Getters/Setters

	public Long getAssetId() {
		return this.assetId;
	}

	public void setAssetId(Long assetId) {
		this.assetId = assetId;
	}

	public byte[] getIssuerPublicKey() {
		return this.issuerPublicKey;
	}

	public String getAssetName() {
		return this.assetName;
	}

	public String getDescription() {
		return this.description;
	}

	public long getQuantity() {
		return this.quantity;
	}

	public boolean isDivisible() {
		return this.isDivisible;
	}

	public String getData() {
		return this.data;
	}

	public boolean isUnspendable() {
		return this.isUnspendable;
	}

	public String getReducedAssetName() {
		return this.reducedAssetName;
	}

}
