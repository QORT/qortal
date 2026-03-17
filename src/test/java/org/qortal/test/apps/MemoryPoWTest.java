package org.qortal.test.apps;

import org.qortal.crypto.MemoryPoW;

import java.util.Random;

public class MemoryPoWTest {

	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("usage: MemoryPoW <buffer-size-MB> <difficulty>");
			System.exit(2);
		}

		int workBufferLength = Integer.parseInt(args[0]) * 1024 * 1024;
		int difficulty = Integer.parseInt(args[1]);

		Random random = new Random();

		byte[] data = new byte[256];
		int[] times = new int[100];

		int timesS1 = 0;
		int timesS2 = 0;

		int maxNonce = 0;

		for (int i = 0; i < times.length; ++i) {
			random.nextBytes(data);

			long startTime = System.currentTimeMillis();
			int nonce = MemoryPoW.compute2(data, workBufferLength, difficulty);
			times[i] = (int) (System.currentTimeMillis() - startTime);

			timesS1 += times[i];
			timesS2 += (times[i] * times[i]);

			if (nonce > maxNonce)
				maxNonce = nonce;
		}

		double stddev = Math.sqrt( ((double) times.length * timesS2 - timesS1 * timesS1) / ((double) times.length * (times.length - 1)) );
		System.out.println(String.format("%d timings, mean: %d ms, stddev: %.2f ms", times.length, timesS1 / times.length, stddev));

		System.out.println(String.format("Max nonce: %d", maxNonce));
	}

}
