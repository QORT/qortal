package org.qortal.test.crosschain;

import org.junit.Ignore;
import org.junit.Test;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.crosschain.Litecoin;

public class LitecoinTests extends BitcoinyTests {

	@Override
	protected String getCoinName() {
		return "Litecoin";
	}

	@Override
	protected String getCoinSymbol() {
		return "LTC";
	}

	@Override
	protected Bitcoiny getCoin() {
		return Litecoin.getInstance();
	}

	@Override
	protected void resetCoinForTesting() {
		Litecoin.resetForTesting();
	}

	@Override
	protected String getDeterministicKey58() {
		return "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";
	}

	@Override
	protected String getDeterministicPublicKey58() {
		return "tpubDCxs3oB9X7XJYkQGU6gfPwd4h3NEiBGA8mfD1aEbZiG5x3BTH4cJqszDP6dtoHPPjZNEj5jPxuSWHCvjg9AHz4dNg6w5vQhv1B8KwWKpxoz";
	}

	@Override
	protected String getRecipient() {
		return "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";
	}

	@Test
	@Ignore(value = "Doesn't work, to be fixed later")
	public void testFindHtlcSecret() {}
}
