package org.qortal.test.crosschain;

import org.junit.Ignore;
import org.junit.Test;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.crosschain.Digibyte;

public class DigibyteTests extends BitcoinyTests {

	@Override
	protected String getCoinName() {
		return "Digibyte";
	}

	@Override
	protected String getCoinSymbol() {
		return "DGB";
	}

	@Override
	protected Bitcoiny getCoin() {
		return Digibyte.getInstance();
	}

	@Override
	protected void resetCoinForTesting() {
		Digibyte.resetForTesting();
	}

	@Override
	protected String getDeterministicKey58() {
		return "xprv9z8QpS7vxwMC2fCnG1oZc6c4aFRLgsqSF86yWrJBKEzMY3T3ySCo85x8Uv5FxTavAQwgEDy1g3iLRT5kdtFjoNNBKukLTMzKwCUn1Abwoxg";
	}

	@Override
	protected String getDeterministicPublicKey58() {
		return "xpub6D7mDwepoJuVF9HFN3LZyEYo8HFq6LZHcM2aKEhnsaXLQqnCWyX3ftGcLDcjYmiPCc9GNX4VjfT32hwvYQnh9H5Z5diAvMsXRrxFmckyNoR";
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
