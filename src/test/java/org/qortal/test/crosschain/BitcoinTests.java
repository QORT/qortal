package org.qortal.test.crosschain;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.store.BlockStoreException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.crosschain.Bitcoin;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.crosschain.BitcoinyHTLC;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

public class BitcoinTests extends Common {

	private Bitcoin bitcoin;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings(); // TestNet3
		bitcoin = Bitcoin.getInstance();
	}

	@After
	public void afterTest() {
		Bitcoin.resetForTesting();
		bitcoin = null;
	}

	@Test
	public void testGetMedianBlockTime() throws BlockStoreException, ForeignBlockchainException {
		System.out.println(String.format("Starting BTC instance..."));
		System.out.println(String.format("BTC instance started"));

		long before = System.currentTimeMillis();
		System.out.println(String.format("Bitcoin median blocktime: %d", bitcoin.getMedianBlockTime()));
		long afterFirst = System.currentTimeMillis();

		System.out.println(String.format("Bitcoin median blocktime: %d", bitcoin.getMedianBlockTime()));
		long afterSecond = System.currentTimeMillis();

		long firstPeriod = afterFirst - before;
		long secondPeriod = afterSecond - afterFirst;

		System.out.println(String.format("1st call: %d ms, 2nd call: %d ms", firstPeriod, secondPeriod));

		assertTrue("2nd call should be quicker than 1st", secondPeriod < firstPeriod);
		assertTrue("2nd call should take less than 5 seconds", secondPeriod < 5000L);
	}

	@Test
	public void testFindHtlcSecret() throws ForeignBlockchainException {
		// This actually exists on TEST3 but can take a while to fetch
		String p2shAddress = "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";

		byte[] expectedSecret = "This string is exactly 32 bytes!".getBytes();
		byte[] secret = BitcoinyHTLC.findHtlcSecret(bitcoin, p2shAddress);

		assertNotNull(secret);
		assertTrue("secret incorrect", Arrays.equals(expectedSecret, secret));
	}

	@Test
	public void testBuildSpend() {
		String xprv58 = "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";

		String recipient = "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";
		long amount = 1000L;

		Transaction transaction = bitcoin.buildSpend(xprv58, recipient, amount);
		assertNotNull(transaction);

		// Check spent key caching doesn't affect outcome

		transaction = bitcoin.buildSpend(xprv58, recipient, amount);
		assertNotNull(transaction);
	}

	@Test
	public void testGetWalletBalance() {
		String xprv58 = "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";

		Long balance = bitcoin.getWalletBalance(xprv58);

		assertNotNull(balance);

		System.out.println(bitcoin.format(balance));

		// Check spent key caching doesn't affect outcome

		Long repeatBalance = bitcoin.getWalletBalance(xprv58);

		assertNotNull(repeatBalance);

		System.out.println(bitcoin.format(repeatBalance));

		assertEquals(balance, repeatBalance);
	}

	@Test
	public void testGetUnusedReceiveAddress() throws ForeignBlockchainException {
		String xprv58 = "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";

		String address = bitcoin.getUnusedReceiveAddress(xprv58);

		assertNotNull(address);

		System.out.println(address);
	}

}
