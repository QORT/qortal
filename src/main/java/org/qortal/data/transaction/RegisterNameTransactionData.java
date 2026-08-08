package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.utils.Unicode;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
// JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("REGISTER_NAME")
public class RegisterNameTransactionData extends TransactionData {

	// Properties

	@Schema(description = "registrant's public key", example = "2tiMr5LTpaWCgbRvkPK8TFd7k63DyHJMMFFsz9uBf1ZP")
	private byte[] registrantPublicKey;

	@Schema(description = "requested name", example = "my-name")
	private String name;

	@Schema(description = "simple name-related info in JSON or text format", example = "Registered Name on the Qortal Chain")
	private String data;

	// For internal use
	@XmlTransient
	@Schema(hidden = true)
	private String reducedName;

	// Constructors

	// For JAXB
	protected RegisterNameTransactionData() {
		super(TransactionType.REGISTER_NAME);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.registrantPublicKey;
	}

	/** From repository */
	public RegisterNameTransactionData(BaseTransactionData baseTransactionData, String name, String data, String reducedName) {
		super(TransactionType.REGISTER_NAME, baseTransactionData);

		this.registrantPublicKey = baseTransactionData.creatorPublicKey;
		this.name = name;
		this.data = data;
		this.reducedName = reducedName;
	}

	/** From network */
	public RegisterNameTransactionData(BaseTransactionData baseTransactionData, String name, String data) {
		this(baseTransactionData, name, data, Unicode.sanitize(name));
	}

	// Getters / setters

	public byte[] getRegistrantPublicKey() {
		return this.registrantPublicKey;
	}

	public String getName() {
		return this.name;
	}

	public String getData() {
		return this.data;
	}

	public String getReducedName() {
		return this.reducedName;
	}

}
