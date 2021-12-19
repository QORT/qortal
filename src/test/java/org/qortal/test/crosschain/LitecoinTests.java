package org.qortal.test.crosschain;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.store.BlockStoreException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.crosschain.Litecoin;
import org.qortal.crosschain.BitcoinyHTLC;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

public class LitecoinTests extends Common {

	private Litecoin litecoin;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings(); // TestNet3
		litecoin = Litecoin.getInstance();
	}

	@After
	public void afterTest() {
		Litecoin.resetForTesting();
		litecoin = null;
	}

	@Test
	public void testGetMedianBlockTime() throws BlockStoreException, ForeignBlockchainException {
		long before = System.currentTimeMillis();
		System.out.println(String.format("Bitcoin median blocktime: %d", litecoin.getMedianBlockTime()));
		long afterFirst = System.currentTimeMillis();

		System.out.println(String.format("Bitcoin median blocktime: %d", litecoin.getMedianBlockTime()));
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
		byte[] secret = BitcoinyHTLC.findHtlcSecret(litecoin, p2shAddress);

		assertNotNull("secret not found", secret);
		assertTrue("secret incorrect", Arrays.equals(expectedSecret, secret));
	}

	@Test
	public void testBuildSpend() {
		String xprv58 = "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";

		String recipient = "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";
		long amount = 1000L;

		Transaction transaction = litecoin.buildSpend(xprv58, recipient, amount);
		assertNotNull("insufficient funds", transaction);

		// Check spent key caching doesn't affect outcome

		transaction = litecoin.buildSpend(xprv58, recipient, amount);
		assertNotNull("insufficient funds", transaction);
	}

	@Test
	public void testGetWalletBalance() {
		String xprv58 = "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";

		Long balance = litecoin.getWalletBalance(xprv58);

		assertNotNull(balance);

		System.out.println(litecoin.format(balance));

		// Check spent key caching doesn't affect outcome

		Long repeatBalance = litecoin.getWalletBalance(xprv58);

		assertNotNull(repeatBalance);

		System.out.println(litecoin.format(repeatBalance));

		assertEquals(balance, repeatBalance);
	}

	@Test
	public void testGetUnusedReceiveAddress() throws ForeignBlockchainException {
		String xprv58 = "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";

		String address = litecoin.getUnusedReceiveAddress(xprv58);

		assertNotNull(address);

		System.out.println(address);
	}

}
