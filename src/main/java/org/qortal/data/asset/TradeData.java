package org.qortal.data.asset;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
public class TradeData {

	// Properties
	@Schema(name = "initiatingOrderId", description = "ID of order that caused trade")
	@XmlElement(name = "initiatingOrderId")
	private byte[] initiator;

	@Schema(name = "targetOrderId", description = "ID of order that matched")
	@XmlElement(name = "targetOrderId")
	private byte[] target;

	@Schema(description = "amount traded from target")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long targetAmount;

	@Schema(description = "amount traded from initiator")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long initiatorAmount;

	@Schema(description = "amount refunded to initiator due to price improvement")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long initiatorSaving;

	@Schema(description = "when trade happened")
	private long timestamp;

	// Used by API - not always present
	@Schema(hidden = true)
	@XmlTransient
	private Long haveAssetId;

	@Schema(hidden = true)
	@XmlTransient
	private String haveAssetName;

	@Schema(hidden = true)
	@XmlTransient
	private Long wantAssetId;

	@Schema(hidden = true)
	@XmlTransient
	private String wantAssetName;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private Long targetAmountAssetId;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String targetAmountAssetName;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private Long initiatorAmountAssetId;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String initiatorAmountAssetName;

	// Constructors

	// Necessary for JAXB
	protected TradeData() {
	}

	// Called before converting to JSON for API
	public void beforeMarshal(Marshaller m) {
		// If we don't have the extra asset name fields then we can't fill in the others
		if (this.haveAssetName == null)
			return;

		// have-asset and want-asset are from the viewpoint of the initiator
		// and amounts are FROM initiator/target
		if (this.haveAssetId < this.wantAssetId) {
			this.initiatorAmountAssetId = this.haveAssetId;
			this.initiatorAmountAssetName = this.haveAssetName;
			this.targetAmountAssetId = this.wantAssetId;
			this.targetAmountAssetName = this.wantAssetName;
		} else {
			this.initiatorAmountAssetId = this.wantAssetId;
			this.initiatorAmountAssetName = this.wantAssetName;
			this.targetAmountAssetId = this.haveAssetId;
			this.targetAmountAssetName = this.haveAssetName;
		}
	}

	public TradeData(byte[] initiator, byte[] target, long targetAmount, long initiatorAmount, long initiatorSaving, long timestamp,
			Long haveAssetId, String haveAssetName, Long wantAssetId, String wantAssetName) {
		this.initiator = initiator;
		this.target = target;
		this.targetAmount = targetAmount;
		this.initiatorAmount = initiatorAmount;
		this.initiatorSaving = initiatorSaving;
		this.timestamp = timestamp;

		this.haveAssetId = haveAssetId;
		this.haveAssetName = haveAssetName;
		this.wantAssetId = wantAssetId;
		this.wantAssetName = wantAssetName;
	}

	public TradeData(byte[] initiator, byte[] target, long targetAmount, long initiatorAmount, long initiatorSaving, long timestamp) {
		this(initiator, target, targetAmount, initiatorAmount, initiatorSaving, timestamp, null, null, null, null);
	}

	// Getters/setters

	public byte[] getInitiator() {
		return this.initiator;
	}

	public byte[] getTarget() {
		return this.target;
	}

	public long getTargetAmount() {
		return this.targetAmount;
	}

	public long getInitiatorAmount() {
		return this.initiatorAmount;
	}

	public long getInitiatorSaving() {
		return this.initiatorSaving;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

}
