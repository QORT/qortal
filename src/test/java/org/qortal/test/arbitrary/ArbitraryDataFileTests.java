package org.qortal.test.arbitrary;

import org.junit.Before;
import org.junit.Test;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

import java.util.Random;

import static org.junit.Assert.*;

public class ArbitraryDataFileTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testSplitAndJoin() throws DataException {
		String dummyDataString = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
		ArbitraryDataFile arbitraryDataFile = new ArbitraryDataFile(dummyDataString.getBytes(), null, false);
		arbitraryDataFile.save();
		assertTrue(arbitraryDataFile.exists());
		assertEquals(62, arbitraryDataFile.size());
		assertEquals("3eyjYjturyVe61grRX42bprGr3Cvw6ehTy4iknVnosDj", arbitraryDataFile.digest58());

		// Split into 7 chunks, each 10 bytes long
		arbitraryDataFile.split(10);
		assertEquals(7, arbitraryDataFile.chunkCount());

		// Delete the original file
		arbitraryDataFile.delete();
		assertFalse(arbitraryDataFile.exists());
		assertEquals(0, arbitraryDataFile.size());

		// Now rebuild the original file from the chunks
		assertEquals(7, arbitraryDataFile.chunkCount());
		arbitraryDataFile.join();

		// Validate that the original file is intact
		assertTrue(arbitraryDataFile.exists());
		assertEquals(62, arbitraryDataFile.size());
		assertEquals("3eyjYjturyVe61grRX42bprGr3Cvw6ehTy4iknVnosDj", arbitraryDataFile.digest58());
	}

	@Test
	public void testSplitAndJoinWithLargeFiles() throws DataException {
		int fileSize = (int) (5.5f * 1024 * 1024); // 5.5MiB
		byte[] randomData = new byte[fileSize];
		new Random().nextBytes(randomData); // No need for SecureRandom here

		ArbitraryDataFile arbitraryDataFile = new ArbitraryDataFile(randomData, null, false);
		arbitraryDataFile.save();
		assertTrue(arbitraryDataFile.exists());
		assertEquals(fileSize, arbitraryDataFile.size());
		String originalFileDigest = arbitraryDataFile.digest58();

		// Split into chunks using 1MiB chunk size
		arbitraryDataFile.split(1 * 1024 * 1024);
		assertEquals(6, arbitraryDataFile.chunkCount());

		// Delete the original file
		arbitraryDataFile.delete();
		assertFalse(arbitraryDataFile.exists());
		assertEquals(0, arbitraryDataFile.size());

		// Now rebuild the original file from the chunks
		assertEquals(6, arbitraryDataFile.chunkCount());
		arbitraryDataFile.join();

		// Validate that the original file is intact
		assertTrue(arbitraryDataFile.exists());
		assertEquals(fileSize, arbitraryDataFile.size());
		assertEquals(originalFileDigest, arbitraryDataFile.digest58());
	}

}
