package org.qortal.crypto;

import java.nio.ByteBuffer;

public class MemoryPoW {

	public static Integer compute2(byte[] data, int workBufferLength, long difficulty) {
		// Hash data with SHA256
		byte[] hash = Crypto.digest(data);

		long[] longHash = new long[4];
		ByteBuffer byteBuffer = ByteBuffer.wrap(hash);
		longHash[0] = byteBuffer.getLong();
		longHash[1] = byteBuffer.getLong();
		longHash[2] = byteBuffer.getLong();
		longHash[3] = byteBuffer.getLong();
		byteBuffer = null;

		int longBufferLength = workBufferLength / 8;
		long[] workBuffer = new long[longBufferLength];
		long[] state = new long[4];

		long seed = 8682522807148012L;
		long seedMultiplier = 1181783497276652981L;

		// For each nonce...
		int nonce = -1;
		long result = 0;
		do {
			++nonce;

			// If we've been interrupted, exit fast with invalid value
			if (Thread.currentThread().isInterrupted())
				return -1;

			seed *= seedMultiplier; // per nonce

			state[0] = longHash[0] ^ seed;
			state[1] = longHash[1] ^ seed;
			state[2] = longHash[2] ^ seed;
			state[3] = longHash[3] ^ seed;

			// Fill work buffer with random
			for (int i = 0; i < workBuffer.length; ++i)
				workBuffer[i] = xoshiro256p(state);

			// Random bounce through whole buffer
			result = workBuffer[0];
			for (int i = 0; i < 1024; ++i) {
				int index = (int) (xoshiro256p(state) & Integer.MAX_VALUE) % workBuffer.length;
				result ^= workBuffer[index];
			}

			// Return if final value > difficulty
		} while (Long.numberOfLeadingZeros(result) < difficulty);

		return nonce;
	}

	public static boolean verify2(byte[] data, int workBufferLength, long difficulty, int nonce) {
		// Hash data with SHA256
		byte[] hash = Crypto.digest(data);

		long[] longHash = new long[4];
		ByteBuffer byteBuffer = ByteBuffer.wrap(hash);
		longHash[0] = byteBuffer.getLong();
		longHash[1] = byteBuffer.getLong();
		longHash[2] = byteBuffer.getLong();
		longHash[3] = byteBuffer.getLong();
		byteBuffer = null;

		int longBufferLength = workBufferLength / 8;
		long[] workBuffer = new long[longBufferLength];
		long[] state = new long[4];

		long seed = 8682522807148012L;
		long seedMultiplier = 1181783497276652981L;

		for (int i = 0; i <= nonce; ++i)
			seed *= seedMultiplier;

		state[0] = longHash[0] ^ seed;
		state[1] = longHash[1] ^ seed;
		state[2] = longHash[2] ^ seed;
		state[3] = longHash[3] ^ seed;

		// Fill work buffer with random
		for (int i = 0; i < workBuffer.length; ++i)
			workBuffer[i] = xoshiro256p(state);

		// Random bounce through whole buffer
		long result = workBuffer[0];
		for (int i = 0; i < 1024; ++i) {
			int index = (int) (xoshiro256p(state) & Integer.MAX_VALUE) % workBuffer.length;
			result ^= workBuffer[index];
		}

		return Long.numberOfLeadingZeros(result) >= difficulty;
	}

	private static final long xoshiro256p(long[] state) {
		final long result = state[0] + state[3];
		final long temp = state[1] << 17;

		state[2] ^= state[0];
		state[3] ^= state[1];
		state[1] ^= state[2];
		state[0] ^= state[3];

		state[2] ^= temp;
		state[3] = (state[3] << 45) | (state[3] >>> (64 - 45)); // rol64(s[3], 45);

		return result;
	}

}
