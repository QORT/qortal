package org.qortal.test.crosschain.litecoinv3;

import com.google.common.hash.HashCode;
import org.qortal.crosschain.ACCT;
import org.qortal.crosschain.LitecoinACCTv1;
import org.qortal.test.crosschain.ACCTTests;

public class LitecoinACCTv3Tests extends ACCTTests {

	public static final byte[] litecoinPublicKeyHash = HashCode.fromString("bb00bb11bb22bb33bb44bb55bb66bb77bb88bb99").asBytes();
	private static final String SYMBOL = "LTC";
	private static final String NAME = "Litecoin";

	@Override
	protected byte[] getPublicKey() {
		return litecoinPublicKeyHash;
	}

	@Override
	protected byte[] buildQortalAT(String address, byte[] publicKey, long redeemAmount, long foreignAmount, int tradeTimeout) {
		return LitecoinACCTv1.buildQortalAT(address, publicKey, redeemAmount, foreignAmount, tradeTimeout);
	}

	@Override
	protected ACCT getInstance() {
		return LitecoinACCTv1.getInstance();
	}

	@Override
	protected int calcRefundTimeout(long partnersOfferMessageTransactionTimestamp, int lockTimeA) {
		return LitecoinACCTv1.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);
	}

	@Override
	protected byte[] buildTradeMessage(String address, byte[] publicKey, byte[] hashOfSecretA, int lockTimeA, int refundTimeout) {
		return LitecoinACCTv1.buildTradeMessage(address, publicKey, hashOfSecretA, lockTimeA, refundTimeout);
	}

	@Override
	protected byte[] buildRedeemMessage(byte[] secretA, String address) {
		return LitecoinACCTv1.buildRedeemMessage(secretA, address);
	}

	@Override
	protected byte[] getCodeBytesHash() {
		return LitecoinACCTv1.CODE_BYTES_HASH;
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
