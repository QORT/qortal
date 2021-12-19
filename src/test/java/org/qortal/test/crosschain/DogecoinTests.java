package org.qortal.test.crosschain;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.store.BlockStoreException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.qortal.crosschain.BitcoinyHTLC;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.crosschain.Dogecoin;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

import java.util.Arrays;

import static org.junit.Assert.*;

public class DogecoinTests extends Common {

	private Dogecoin dogecoin;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings(); // TestNet3
		dogecoin = Dogecoin.getInstance();
	}

	@After
	public void afterTest() {
		Dogecoin.resetForTesting();
		dogecoin = null;
	}

	@Test
	public void testGetMedianBlockTime() throws BlockStoreException, ForeignBlockchainException {
		long before = System.currentTimeMillis();
		System.out.println(String.format("Dogecoin median blocktime: %d", dogecoin.getMedianBlockTime()));
		long afterFirst = System.currentTimeMillis();

		System.out.println(String.format("Dogecoin median blocktime: %d", dogecoin.getMedianBlockTime()));
		long afterSecond = System.currentTimeMillis();

		long firstPeriod = afterFirst - before;
		long secondPeriod = afterSecond - afterFirst;

		System.out.println(String.format("1st call: %d ms, 2nd call: %d ms", firstPeriod, secondPeriod));

		assertTrue("2nd call should be quicker than 1st", secondPeriod < firstPeriod);
		assertTrue("2nd call should take less than 5 seconds", secondPeriod < 5000L);
	}

	@Test
	@Ignore(value = "Doesn't work, to be fixed later")
	public void testFindHtlcSecret() throws ForeignBlockchainException {
		// This actually exists on TEST3 but can take a while to fetch
		String p2shAddress = "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";

		byte[] expectedSecret = "This string is exactly 32 bytes!".getBytes();
		byte[] secret = BitcoinyHTLC.findHtlcSecret(dogecoin, p2shAddress);

		assertNotNull("secret not found", secret);
		assertTrue("secret incorrect", Arrays.equals(expectedSecret, secret));
	}

	@Test
	@Ignore(value = "No testnet nodes available, so we can't regularly test buildSpend yet")
	public void testBuildSpend() {
		String xprv58 = "dgpv51eADS3spNJh9drNeW1Tc1P9z2LyaQRXPBortsq6yice1k47C2u2Prvgxycr2ihNBWzKZ2LthcBBGiYkWZ69KUTVkcLVbnjq7pD8mnApEru";

		String recipient = "DP1iFao33xdEPa5vaArpj7sykfzKNeiJeX";
		long amount = 1000L;

		Transaction transaction = dogecoin.buildSpend(xprv58, recipient, amount);
		assertNotNull("insufficient funds", transaction);

		// Check spent key caching doesn't affect outcome

		transaction = dogecoin.buildSpend(xprv58, recipient, amount);
		assertNotNull("insufficient funds", transaction);
	}

	@Test
	public void testGetWalletBalance() {
		String xprv58 = "dgpv51eADS3spNJh9drNeW1Tc1P9z2LyaQRXPBortsq6yice1k47C2u2Prvgxycr2ihNBWzKZ2LthcBBGiYkWZ69KUTVkcLVbnjq7pD8mnApEru";

		Long balance = dogecoin.getWalletBalance(xprv58);

		assertNotNull(balance);

		System.out.println(dogecoin.format(balance));

		// Check spent key caching doesn't affect outcome

		Long repeatBalance = dogecoin.getWalletBalance(xprv58);

		assertNotNull(repeatBalance);

		System.out.println(dogecoin.format(repeatBalance));

		assertEquals(balance, repeatBalance);
	}

	@Test
	public void testGetUnusedReceiveAddress() throws ForeignBlockchainException {
		String xprv58 = "dgpv51eADS3spNJh9drNeW1Tc1P9z2LyaQRXPBortsq6yice1k47C2u2Prvgxycr2ihNBWzKZ2LthcBBGiYkWZ69KUTVkcLVbnjq7pD8mnApEru";

		String address = dogecoin.getUnusedReceiveAddress(xprv58);

		assertNotNull(address);

		System.out.println(address);
	}

}
