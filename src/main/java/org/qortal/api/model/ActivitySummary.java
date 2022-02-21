package org.qortal.api.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.qortal.api.TransactionCountMapXmlAdapter;
import org.qortal.transaction.Transaction.TransactionType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ActivitySummary {

	private int blockCount;
	private int assetsIssued;
	private int namesRegistered;

	// Assuming TransactionType values are contiguous so 'length' equals count
	@XmlJavaTypeAdapter(TransactionCountMapXmlAdapter.class)
	private Map<TransactionType, Integer> transactionCountByType = new EnumMap<>(TransactionType.class);
	private int totalTransactionCount = 0;

	public ActivitySummary() {
		// Needed for JAXB
	}

	public int getBlockCount() {
		return this.blockCount;
	}

	public void setBlockCount(int blockCount) {
		this.blockCount = blockCount;
	}

	public int getTotalTransactionCount() {
		return this.totalTransactionCount;
	}

	public int getAssetsIssued() {
		return this.assetsIssued;
	}

	public void setAssetsIssued(int assetsIssued) {
		this.assetsIssued = assetsIssued;
	}

	public int getNamesRegistered() {
		return this.namesRegistered;
	}

	public void setNamesRegistered(int namesRegistered) {
		this.namesRegistered = namesRegistered;
	}

	public Map<TransactionType, Integer> getTransactionCountByType() {
		return Collections.unmodifiableMap(this.transactionCountByType);
	}

	public void setTransactionCountByType(TransactionType transactionType, int transactionCount) {
		this.transactionCountByType.put(transactionType, transactionCount);

		this.totalTransactionCount = this.transactionCountByType.values().stream().mapToInt(Integer::intValue).sum();
	}

	public void setTransactionCountByType(Map<TransactionType, Integer> transactionCountByType) {
		this.transactionCountByType = new EnumMap<>(transactionCountByType);

		this.totalTransactionCount = this.transactionCountByType.values().stream().mapToInt(Integer::intValue).sum();
	}

}
