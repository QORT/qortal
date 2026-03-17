package org.qortal.test.crosschain.ravencoinv3;

import com.google.common.hash.HashCode;
import org.qortal.crosschain.ACCT;
import org.qortal.crosschain.RavencoinACCTv3;
import org.qortal.test.crosschain.ACCTTests;

public class RavencoinACCTv3Tests extends ACCTTests {

	public static final byte[] ravencoinPublicKeyHash = HashCode.fromString("bb00bb11bb22bb33bb44bb55bb66bb77bb88bb99").asBytes();
	private static final String SYMBOL = "RVN";
	private static final String NAME = "Ravencoin";

	@Override
	protected byte[] getPublicKey() {
		return ravencoinPublicKeyHash;
	}

	@Override
	protected byte[] buildQortalAT(String address, byte[] publicKey, long redeemAmount, long foreignAmount, int tradeTimeout) {
		return RavencoinACCTv3.buildQortalAT(address, publicKey, redeemAmount, foreignAmount, tradeTimeout);
	}

	@Override
	protected ACCT getInstance() {
		return RavencoinACCTv3.getInstance();
	}

	@Override
	protected int calcRefundTimeout(long partnersOfferMessageTransactionTimestamp, int lockTimeA) {
		return RavencoinACCTv3.calcRefundTimeout(partnersOfferMessageTransactionTimestamp, lockTimeA);
	}

	@Override
	protected byte[] buildTradeMessage(String address, byte[] publicKey, byte[] hashOfSecretA, int lockTimeA, int refundTimeout) {
		return RavencoinACCTv3.buildTradeMessage(address, publicKey, hashOfSecretA, lockTimeA, refundTimeout);
	}

	@Override
	protected byte[] buildRedeemMessage(byte[] secretA, String address) {
		return RavencoinACCTv3.buildRedeemMessage(secretA, address);
	}

	@Override
	protected byte[] getCodeBytesHash() {
		return RavencoinACCTv3.CODE_BYTES_HASH;
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
