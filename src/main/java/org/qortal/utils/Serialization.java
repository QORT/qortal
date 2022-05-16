package org.qortal.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.qortal.transform.TransformationException;
import org.qortal.transform.Transformer;

import com.google.common.primitives.Ints;

public class Serialization {

	/**
	 * Convert BigDecimal, unscaled, to byte[] then prepend with zero bytes to specified length.
	 * 
	 * @param amount
	 * @param length
	 * @return byte[]
	 * @throws IOException
	 */
	public static byte[] serializeBigDecimal(BigDecimal amount, int length) throws IOException {
		// Note: we call .setScale(8) here to normalize values, especially values from API as they can have varying scale
		// (At least until the BigDecimal XmlAdapter works - see data/package-info.java)
		byte[] amountBytes = amount.setScale(8).unscaledValue().toByteArray();
		byte[] output = new byte[length];

		// To retain sign of 'amount', we might need to explicitly fill 'output' with leading 1s
		if (amount.signum() == -1)
			// Negative values: fill output with 1s
			Arrays.fill(output, (byte) 0xff);

		System.arraycopy(amountBytes, 0, output, length - amountBytes.length, amountBytes.length);

		return output;
	}

	/**
	 * Convert BigDecimal, unscaled, to byte[] then prepend with zero bytes to fixed length of 8.
	 * 
	 * @param amount
	 * @return byte[]
	 * @throws IOException
	 */
	public static byte[] serializeBigDecimal(BigDecimal amount) throws IOException {
		return serializeBigDecimal(amount, 8);
	}

	/**
	 * Write to ByteBuffer a BigDecimal, unscaled, prepended with zero bytes to specified length.
	 * 
	 * @param ByteArrayOutputStream
	 * @param amount
	 * @param length
	 * @throws IOException
	 */
	public static void serializeBigDecimal(ByteArrayOutputStream bytes, BigDecimal amount, int length) throws IOException {
		bytes.write(serializeBigDecimal(amount, length));
	}

	/**
	 * Write to ByteBuffer a BigDecimal, unscaled, prepended with zero bytes to fixed length of 8.
	 * 
	 * @param ByteArrayOutputStream
	 * @param amount
	 * @throws IOException
	 */
	public static void serializeBigDecimal(ByteArrayOutputStream bytes, BigDecimal amount) throws IOException {
		serializeBigDecimal(bytes, amount, 8);
	}

	public static BigDecimal deserializeBigDecimal(ByteBuffer byteBuffer, int length) {
		byte[] bytes = new byte[length];
		byteBuffer.get(bytes);
		return new BigDecimal(new BigInteger(bytes), 8);
	}

	public static BigDecimal deserializeBigDecimal(ByteBuffer byteBuffer) {
		return Serialization.deserializeBigDecimal(byteBuffer, 8);
	}

	public static void serializeAddress(ByteArrayOutputStream bytes, String address) throws IOException {
		bytes.write(Base58.decode(address));
	}

	public static String deserializeAddress(ByteBuffer byteBuffer) {
		byte[] bytes = new byte[Transformer.ADDRESS_LENGTH];
		byteBuffer.get(bytes);
		return Base58.encode(bytes);
	}

	public static byte[] deserializePublicKey(ByteBuffer byteBuffer) {
		byte[] bytes = new byte[Transformer.PUBLIC_KEY_LENGTH];
		byteBuffer.get(bytes);
		return bytes;
	}

	/**
	 * Original serializeSizedString() method used in various transaction types
	 * @param bytes
	 * @param string
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	public static void serializeSizedString(ByteArrayOutputStream bytes, String string) throws UnsupportedEncodingException, IOException {
		byte[] stringBytes = string.getBytes(StandardCharsets.UTF_8);
		bytes.write(Ints.toByteArray(stringBytes.length));
		bytes.write(stringBytes);
	}

	/**
	 * Original deserializeSizedString() method used in various transaction types
	 * @param byteBuffer
	 * @param maxSize
	 * @return
	 * @throws TransformationException
	 */
	public static String deserializeSizedString(ByteBuffer byteBuffer, int maxSize) throws TransformationException {
		int size = byteBuffer.getInt();
		if (size > maxSize)
			throw new TransformationException("Serialized string too long");

		if (size > byteBuffer.remaining())
			throw new TransformationException("Byte data too short for serialized string");

		byte[] bytes = new byte[size];
		byteBuffer.get(bytes);

		return new String(bytes, StandardCharsets.UTF_8);
	}

	/**
	 * Alternate version of serializeSizedString() added for ARBITRARY transactions.
	 * These two methods can ultimately be merged together once unit tests can
	 * confirm that they process data identically.
	 * @param bytes
	 * @param string
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	public static void serializeSizedStringV2(ByteArrayOutputStream bytes, String string) throws UnsupportedEncodingException, IOException {
		byte[] stringBytes = null;
		int stringBytesLength = 0;

		if (string != null) {
			stringBytes = string.getBytes(StandardCharsets.UTF_8);
			stringBytesLength = stringBytes.length;
		}
		bytes.write(Ints.toByteArray(stringBytesLength));
		if (stringBytesLength > 0) {
			bytes.write(stringBytes);
		}
	}

	/**
	 * Alternate version of serializeSizedString() added for ARBITRARY transactions.
	 * The main difference is that blank strings are returned as null.
	 * These two methods can ultimately be merged together once any differences are
	 * solved, and unit tests can confirm that they process data identically.
	 * @param byteBuffer
	 * @param maxSize
	 * @return
	 * @throws TransformationException
	 */
	public static String deserializeSizedStringV2(ByteBuffer byteBuffer, int maxSize) throws TransformationException {
		int size = byteBuffer.getInt();
		if (size > maxSize)
			throw new TransformationException("Serialized string too long");

		if (size > byteBuffer.remaining())
			throw new TransformationException("Byte data too short for serialized string");

		if (size == 0)
			return null;

		byte[] bytes = new byte[size];
		byteBuffer.get(bytes);

		return new String(bytes, StandardCharsets.UTF_8);
	}

}
