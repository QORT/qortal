package org.qortal.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class SimpleForeignTransaction {

	public static class AddressAmount {
		public final String address;
		public final long amount;

		protected AddressAmount() {
			/* For JAXB */
			this.address = null;
			this.amount = 0;
		}

		public AddressAmount(String address, long amount) {
			this.address = address;
			this.amount = amount;
		}
	}

	private String txHash;
	private long timestamp;

	private List<AddressAmount> inputs;

	public static class Output {
		public final List<String> addresses;
		public final long amount;

		protected Output() {
			/* For JAXB */
			this.addresses = null;
			this.amount = 0;
		}

		public Output(List<String> addresses, long amount) {
			this.addresses = addresses;
			this.amount = amount;
		}
	}
	private List<Output> outputs;

	private long totalAmount;
	private long fees;

	private Boolean isSentNotReceived;

	protected SimpleForeignTransaction() {
		/* For JAXB */
	}

	private SimpleForeignTransaction(Builder builder) {
		this.txHash = builder.txHash;
		this.timestamp = builder.timestamp;
		this.inputs = Collections.unmodifiableList(builder.inputs);
		this.outputs = Collections.unmodifiableList(builder.outputs);

		Objects.requireNonNull(this.txHash);
		if (timestamp <= 0)
			throw new IllegalArgumentException("timestamp must be positive");

		long totalGrossAmount = this.inputs.stream().map(addressAmount -> addressAmount.amount).reduce(0L, Long::sum);
		this.totalAmount = this.outputs.stream().map(addressAmount -> addressAmount.amount).reduce(0L, Long::sum);

		this.fees = totalGrossAmount - this.totalAmount;

		this.isSentNotReceived = builder.isSentNotReceived;
	}

	public String getTxHash() {
		return this.txHash;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public List<AddressAmount> getInputs() {
		return this.inputs;
	}

	public List<Output> getOutputs() {
		return this.outputs;
	}

	public long getTotalAmount() {
		return this.totalAmount;
	}

	public long getFees() {
		return this.fees;
	}

	public Boolean isSentNotReceived() {
		return this.isSentNotReceived;
	}

	public static class Builder {
		private String txHash;
		private long timestamp;
		private List<AddressAmount> inputs = new ArrayList<>();
		private List<Output> outputs = new ArrayList<>();
		private Boolean isSentNotReceived;

		public Builder txHash(String txHash) {
			this.txHash = Objects.requireNonNull(txHash);
			return this;
		}

		public Builder timestamp(long timestamp) {
			if (timestamp <= 0)
				throw new IllegalArgumentException("timestamp must be positive");

			this.timestamp = timestamp;
			return this;
		}

		public Builder input(String address, long amount) {
			Objects.requireNonNull(address);
			if (amount < 0)
				throw new IllegalArgumentException("amount must be zero or positive");

			AddressAmount input = new AddressAmount(address, amount);
			inputs.add(input);
			return this;
		}

		public Builder output(List<String> addresses, long amount) {
			Objects.requireNonNull(addresses);
			if (amount < 0)
				throw new IllegalArgumentException("amount must be zero or positive");

			Output output = new Output(addresses, amount);
			outputs.add(output);
			return this;
		}

		public Builder isSentNotReceived(Boolean isSentNotReceived) {
			this.isSentNotReceived = isSentNotReceived;
			return this;
		}

		public SimpleForeignTransaction build() {
			return new SimpleForeignTransaction(this);
		}
	}

}
