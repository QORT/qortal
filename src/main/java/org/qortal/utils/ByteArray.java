package org.qortal.utils;

import java.util.Arrays;
import java.util.Objects;

public class ByteArray implements Comparable<ByteArray> {

	private int hash;
	public final byte[] value;

	public ByteArray(byte[] value) {
		this.value = Objects.requireNonNull(value);
	}

	public static ByteArray of(byte[] value) {
		return new ByteArray(value);
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
			h = 1;

			for (int i = 0; i < val.length; ++i)
				h = 31 * h + val[i];

			this.hash = h;
		}
		return h;
	}

	@Override
	public int compareTo(ByteArray other) {
		Objects.requireNonNull(other);
		return this.compareToPrimitive(other.value);
	}

	public int compareToPrimitive(byte[] otherValue) {
		byte[] val = this.value;

		if (val.length < otherValue.length)
			return -1;

		if (val.length > otherValue.length)
			return 1;

		for (int i = 0; i < val.length; ++i) {
			int a = val[i] & 0xFF;
			int b = otherValue[i] & 0xFF;
			if (a < b)
				return -1;
			if (a > b)
				return 1;
		}

		return 0;
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
