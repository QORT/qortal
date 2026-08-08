package org.qortal.utils;

import java.util.Arrays;
import java.util.Objects;

public class ByteArray implements Comparable<ByteArray> {

	private int hash;
	public final byte[] value;

	private ByteArray(byte[] value) {
		this.value = value;
	}

	public static ByteArray wrap(byte[] value) {
		return new ByteArray(Objects.requireNonNull(value));
	}

	public static ByteArray copyOf(byte[] value) {
		return new ByteArray(Arrays.copyOf(value, value.length));
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;

		if (other instanceof byte[])
			return Arrays.equals(this.value, (byte[]) other);

		if (other instanceof ByteArray)
			return Arrays.equals(this.value, ((ByteArray) other).value);

		return false;
	}

	@Override
	public int hashCode() {
		int h = this.hash;
		byte[] val = this.value;

		if (h == 0 && val.length > 0) {
			this.hash = h = Arrays.hashCode(val);
		}
		return h;
	}

	@Override
	public int compareTo(ByteArray other) {
		Objects.requireNonNull(other);
		return this.compareToPrimitive(other.value);
	}

	public int compareToPrimitive(byte[] otherValue) {
		return Arrays.compareUnsigned(this.value, otherValue);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder(3 + this.value.length * 6);
		sb.append("[");

		if (this.value.length > 0)
			sb.append(this.value[0]);

		for (int i = 1; i < this.value.length; ++i)
			sb.append(", ").append(this.value[i]);

		return sb.append("]").toString();
	}

}
