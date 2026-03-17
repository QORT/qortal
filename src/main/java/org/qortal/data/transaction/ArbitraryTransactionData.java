package org.qortal.data.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import org.eclipse.persistence.oxm.annotations.XmlDiscriminatorValue;
import org.qortal.arbitrary.misc.Service;
import org.qortal.data.PaymentData;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

// All properties to be converted to JSON via JAXB
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(allOf = { TransactionData.class })
//JAXB: use this subclass if XmlDiscriminatorNode matches XmlDiscriminatorValue below:
@XmlDiscriminatorValue("ARBITRARY")
public class ArbitraryTransactionData extends TransactionData {

	// "data" field types
	public enum DataType {
		RAW_DATA,
		DATA_HASH
    }

	// Methods
	public enum Method {
		PUT(0), // A complete replacement of a resource
		PATCH(1); // An update / partial replacement of a resource

		public final int value;

		private static final Map<Integer, Method> map = stream(Method.values())
				.collect(toMap(method -> method.value, method -> method));

		Method(int value) {
			this.value = value;
		}

		public static Method valueOf(int value) {
			return map.get(value);
		}
	}

	// Compression types
	public enum Compression {
		NONE(0),
		ZIP(1);

		public final int value;

		private static final Map<Integer, Compression> map = stream(Compression.values())
				.collect(toMap(compression -> compression.value, compression -> compression));

		Compression(int value) {
			this.value = value;
		}

		public static Compression valueOf(int value) {
			return map.get(value);
		}
	}

	// Properties
	private int version;
	@Schema(example = "sender_public_key")
	private byte[] senderPublicKey;

	private int service;
	private int nonce;
	private int size;

	private String name;
	private String identifier;
	private Method method;
	private byte[] secret;
	private Compression compression;

	@Schema(example = "raw_data_in_base58")
	private byte[] data;
	private DataType dataType;
	@Schema(example = "metadata_file_hash_in_base58")
	private byte[] metadataHash;

	private List<PaymentData> payments;

	// Constructors

	// For JAXB
	protected ArbitraryTransactionData() {
		super(TransactionType.ARBITRARY);
	}

	public void afterUnmarshal(Unmarshaller u, Object parent) {
		this.creatorPublicKey = this.senderPublicKey;
	}

	public ArbitraryTransactionData(BaseTransactionData baseTransactionData,
									int version, int service, int nonce, int size,
									String name, String identifier, Method method, byte[] secret, Compression compression,
									byte[] data, DataType dataType, byte[] metadataHash, List<PaymentData> payments) {
		super(TransactionType.ARBITRARY, baseTransactionData);

		this.senderPublicKey = baseTransactionData.creatorPublicKey;
		this.version = version;
		this.service = service;
		this.nonce = nonce;
		this.size = size;
		this.name = name;
		this.identifier = identifier;
		this.method = method;
		this.secret = secret;
		this.compression = compression;
		this.data = data;
		this.dataType = dataType;
		this.metadataHash = metadataHash;
		this.payments = payments;
	}

	// Getters/Setters

	public byte[] getSenderPublicKey() {
		return this.senderPublicKey;
	}

	public int getVersion() {
		return this.version;
	}

	public Service getService() {
		return Service.valueOf(this.service);
	}

	public int getServiceInt() {
		return this.service;
	}

	public int getNonce() {
		return this.nonce;
	}

	public void setNonce(int nonce) {
		this.nonce = nonce;
	}

	public int getSize() {
		return this.size;
	}

	public String getName() {
		return this.name;
	}

	public String getIdentifier() {
		return (this.identifier != "") ? this.identifier : null;
	}

	public Method getMethod() {
		return this.method;
	}

	public byte[] getSecret() {
		return this.secret;
	}

	public Compression getCompression() {
		return this.compression;
	}

	public byte[] getData() {
		return this.data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public DataType getDataType() {
		return this.dataType;
	}

	public void setDataType(DataType dataType) {
		this.dataType = dataType;
	}

	public byte[] getMetadataHash() {
		return this.metadataHash;
	}

	public void setMetadataHash(byte[] metadataHash) {
		this.metadataHash = metadataHash;
	}

	public List<PaymentData> getPayments() {
		return this.payments;
	}

	@Override
	public String toString() {
		return "ArbitraryTransactionData{" +
				"version=" + version +
				", service=" + service +
				", nonce=" + nonce +
				", size=" + size +
				", name='" + name + '\'' +
				", identifier='" + identifier + '\'' +
				", method=" + method +
				", compression=" + compression +
				", dataType=" + dataType +
				", type=" + type +
				", timestamp=" + timestamp +
				", fee=" + fee +
				", txGroupId=" + txGroupId +
				", blockHeight=" + blockHeight +
				", blockSequence=" + blockSequence +
				", approvalStatus=" + approvalStatus +
				", approvalHeight=" + approvalHeight +
				'}';
	}
}
