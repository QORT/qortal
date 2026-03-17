package org.qortal.test.crosschain.piratechainv3;

import com.google.common.hash.HashCode;
import org.qortal.crosschain.ACCT;
import org.qortal.crosschain.PirateChainACCTv3;
import org.qortal.test.crosschain.ACCTTests;

public class PirateChainACCTv3Tests extends ACCTTests {

	public static final byte[] pirateChainPublicKey = HashCode.fromString("aabb00bb11bb22bb33bb44bb55bb66bb77bb88bb99cc00cc11cc22cc33cc44cc55").asBytes(); // 33 bytes
	private static final String SYMBOL = "ARRR";
	private static final String NAME = "Pirate Chain";

	@Override
	protected byte[] getPublicKey() {
		return pirateChainPublicKey;
	}

	@Override
	protected byte[] buildQortalAT(String address, byte[] publicKey, long redeemAmount, long foreignAmount, int tradeTimeout) {
		return PirateChainACCTv3.buildQortalAT(address, publicKey, redeemAmount, foreignAmount, tradeTimeout);
	}

	@Override
	protected ACCT getInstance() {
		return PirateChainACCTv3.getInstance();
	}

	@Override
	protected int calcRefundTimeout(long partnersOfferMessageTransactionTimestamp, int lockTimeA) {
		return PirateChainACCTv3.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);
	}

	@Override
	protected byte[] buildTradeMessage(String address, byte[] publicKey, byte[] hashOfSecretA, int lockTimeA, int refundTimeout) {
		return PirateChainACCTv3.buildTradeMessage(address, publicKey, hashOfSecretA, lockTimeA, refundTimeout);
	}

	@Override
	protected byte[] buildRedeemMessage(byte[] secretA, String address) {
		return PirateChainACCTv3.buildRedeemMessage(secretA, address);
	}

	@Override
	protected byte[] getCodeBytesHash() {
		return PirateChainACCTv3.CODE_BYTES_HASH;
	}

	@Override
	protected String getSymbol() {
		return SYMBOL;
	}

	@Override
	protected String getName() {
		return NAME;
	}
}
