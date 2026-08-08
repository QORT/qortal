package org.qortal.test.crosschain;

import org.junit.Ignore;
import org.junit.Test;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.crosschain.Dogecoin;

public class DogecoinTests extends BitcoinyTests {

	@Override
	protected String getCoinName() {
		return "Dogecoin";
	}

	@Override
	protected String getCoinSymbol() {
		return "DOGE";
	}

	@Override
	protected Bitcoiny getCoin() {
		return Dogecoin.getInstance();
	}

	@Override
	protected void resetCoinForTesting() {
		Dogecoin.resetForTesting();
	}

	@Override
	protected String getDeterministicKey58() {
		return "dgpv51eADS3spNJh9drNeW1Tc1P9z2LyaQRXPBortsq6yice1k47C2u2Prvgxycr2ihNBWzKZ2LthcBBGiYkWZ69KUTVkcLVbnjq7pD8mnApEru";
	}

	@Override
	protected String getDeterministicPublicKey58() {
		return "dgub8rqf3khHiPeYE3cNn3Y4DQQ411nAnFpuSUPt5k5GJZQsydsTLkaf4onaWn4N8pHvrV3oNMEATKoPGTFZwm2Uhh7Dy9gYwA7rkSv6oLofbag";
	}

	@Override
	protected String getRecipient() {
		return null;
	}

	@Test
	@Ignore(value = "Doesn't work, to be fixed later")
	public void testFindHtlcSecret() {}

	@Test
	@Ignore(value = "No testnet nodes available, so we can't regularly test buildSpend yet")
	public void testBuildSpend() {}
}
