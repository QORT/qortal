package org.qortal.utils;

public class BitTwiddling {

	/**
	 * Returns bit-mask for values up to, and including, <tt>maxValue</tt>.
	 * <p>
	 * e.g. for values up to 5 (0101b) this returns a mask of 7 (0111b).
	 * <p>
	 * Based on Integer.highestOneBit.
	 * 
	 * @param maxValue
	 * @return mask
	 */
	public static int calcMask(int maxValue) {
		maxValue |= maxValue >> 1;
		maxValue |= maxValue >> 2;
		maxValue |= maxValue >> 4;
		maxValue |= maxValue >> 8;
		maxValue |= maxValue >> 16;
		return maxValue;
	}

	/** Convert int to little-endian byte array */
	public static byte[] toLEByteArray(int value) {
		return new byte[] { (byte) (value), (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24) };
	}

	/** Convert int to big-endian byte array */
	public static byte[] toBEByteArray(int value) {
		return new byte[] { (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) (value) };
	}

	/** Convert long to big-endian byte array */
	public static byte[] toBEByteArray(long value) {
		return new byte[] { (byte) (value >> 56), (byte) (value >> 48), (byte) (value >> 40), (byte) (value >> 32),
				(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) (value) };
	}

	/** Convert little-endian bytes to int */
	public static int intFromLEBytes(byte[] bytes, int offset) {
		return (bytes[offset] & 0xff) | (bytes[offset + 1] & 0xff) << 8 | (bytes[offset + 2] & 0xff) << 16 | (bytes[offset + 3] & 0xff) << 24;
	}

	/** Convert big-endian bytes to long */
	public static long longFromBEBytes(byte[] bytes, int start) {
		return (bytes[start] & 0xffL) << 56 | (bytes[start + 1] & 0xffL) << 48 | (bytes[start + 2] & 0xffL) << 40 | (bytes[start + 3] & 0xffL) << 32
				| (bytes[start + 4] & 0xffL) << 24 | (bytes[start + 5] & 0xffL) << 16 | (bytes[start + 6] & 0xffL) << 8 | (bytes[start + 7] & 0xffL);
	}

}
