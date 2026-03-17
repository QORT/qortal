package org.qortal.test.crosschain;

import org.qortal.crosschain.Bitcoin;
import org.qortal.crosschain.Bitcoiny;

public class BitcoinTests extends BitcoinyTests {

	@Override
	protected String getCoinName() {
		return "Bitcoin";
	}

	@Override
	protected String getCoinSymbol() {
		return "BTC";
	}

	@Override
	protected Bitcoiny getCoin() {
		return Bitcoin.getInstance();
	}

	@Override
	protected void resetCoinForTesting() {
		Bitcoin.resetForTesting();
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
}
