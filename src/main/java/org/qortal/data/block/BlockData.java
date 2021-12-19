package org.qortal.data.block;

import com.google.common.primitives.Bytes;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.qortal.crypto.Crypto;

// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class BlockData implements Serializable {

	private static final long serialVersionUID = -7678329659124664620L;

	// Properties

	private byte[] signature;
	private int version;
	private byte[] reference;
	private int transactionCount;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long totalFees;

	private byte[] transactionsSignature;
	private Integer height;
	private long timestamp;
	private byte[] minterPublicKey;
	private byte[] minterSignature;
	private int atCount;

	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	private long atFees;

	private byte[] encodedOnlineAccounts;
	private int onlineAccountsCount;
	private Long onlineAccountsTimestamp;
	private byte[] onlineAccountsSignatures;

	// Constructors

	// necessary for JAX-RS serialization
	protected BlockData() {
	}

	public BlockData(int version, byte[] reference, int transactionCount, long totalFees, byte[] transactionsSignature, Integer height, long timestamp,
			byte[] minterPublicKey, byte[] minterSignature, int atCount, long atFees,
			byte[] encodedOnlineAccounts, int onlineAccountsCount, Long onlineAccountsTimestamp, byte[] onlineAccountsSignatures) {
		this.version = version;
		this.reference = reference;
		this.transactionCount = transactionCount;
		this.totalFees = totalFees;
		this.transactionsSignature = transactionsSignature;
		this.height = height;
		this.timestamp = timestamp;
		this.minterPublicKey = minterPublicKey;
		this.minterSignature = minterSignature;
		this.atCount = atCount;
		this.atFees = atFees;
		this.encodedOnlineAccounts = encodedOnlineAccounts;
		this.onlineAccountsCount = onlineAccountsCount;
		this.onlineAccountsTimestamp = onlineAccountsTimestamp;
		this.onlineAccountsSignatures = onlineAccountsSignatures;

		if (this.minterSignature != null && this.transactionsSignature != null)
			this.signature = Bytes.concat(this.minterSignature, this.transactionsSignature);
		else
			this.signature = null;
	}

	public BlockData(int version, byte[] reference, int transactionCount, long totalFees, byte[] transactionsSignature, Integer height, long timestamp,
			byte[] minterPublicKey, byte[] minterSignature, int atCount, long atFees) {
		this(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp, minterPublicKey, minterSignature, atCount, atFees,
				null, 0, null, null);
	}

	public BlockData(BlockData other) {
		this.version = other.version;
		this.reference = other.reference;
		this.transactionCount = other.transactionCount;
		this.totalFees = other.totalFees;
		this.transactionsSignature = other.transactionsSignature;
		this.height = other.height;
		this.timestamp = other.timestamp;
		this.minterPublicKey = other.minterPublicKey;
		this.minterSignature = other.minterSignature;
		this.atCount = other.atCount;
		this.atFees = other.atFees;
		this.encodedOnlineAccounts = other.encodedOnlineAccounts;
		this.onlineAccountsCount = other.onlineAccountsCount;
		this.onlineAccountsTimestamp = other.onlineAccountsTimestamp;
		this.onlineAccountsSignatures = other.onlineAccountsSignatures;
		this.signature = other.signature;
	}

	// Getters/setters

	public byte[] getSignature() {
		return this.signature;
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	public int getVersion() {
		return this.version;
	}

	public byte[] getReference() {
		return this.reference;
	}

	public void setReference(byte[] reference) {
		this.reference = reference;
	}

	public int getTransactionCount() {
		return this.transactionCount;
	}

	public void setTransactionCount(int transactionCount) {
		this.transactionCount = transactionCount;
	}

	public long getTotalFees() {
		return this.totalFees;
	}

	public void setTotalFees(long totalFees) {
		this.totalFees = totalFees;
	}

	public byte[] getTransactionsSignature() {
		return this.transactionsSignature;
	}

	public void setTransactionsSignature(byte[] transactionsSignature) {
		this.transactionsSignature = transactionsSignature;
	}

	public Integer getHeight() {
		return this.height;
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] getMinterPublicKey() {
		return this.minterPublicKey;
	}

	public byte[] getMinterSignature() {
		return this.minterSignature;
	}

	public void setMinterSignature(byte[] minterSignature) {
		this.minterSignature = minterSignature;
	}

	public int getATCount() {
		return this.atCount;
	}

	public void setATCount(int atCount) {
		this.atCount = atCount;
	}

	public long getATFees() {
		return this.atFees;
	}

	public void setATFees(long atFees) {
		this.atFees = atFees;
	}

	public byte[] getEncodedOnlineAccounts() {
		return this.encodedOnlineAccounts;
	}

	public int getOnlineAccountsCount() {
		return this.onlineAccountsCount;
	}

	public Long getOnlineAccountsTimestamp() {
		return this.onlineAccountsTimestamp;
	}

	public void setOnlineAccountsTimestamp(Long onlineAccountsTimestamp) {
		this.onlineAccountsTimestamp = onlineAccountsTimestamp;
	}

	public byte[] getOnlineAccountsSignatures() {
		return this.onlineAccountsSignatures;
	}

	public void setOnlineAccountsSignatures(byte[] onlineAccountsSignatures) {
		this.onlineAccountsSignatures = onlineAccountsSignatures;
	}

	// JAXB special

	@XmlElement(name = "minterAddress")
	protected String getMinterAddress() {
		return Crypto.toAddress(this.minterPublicKey);
	}

}
