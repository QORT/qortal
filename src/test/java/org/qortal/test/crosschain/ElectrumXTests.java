package org.qortal.test.crosschain;

import static org.junit.Assert.*;

import java.security.Security;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Address;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.ScriptBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.Test;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.crosschain.BitcoinyTransaction;
import org.qortal.crosschain.ElectrumX;
import org.qortal.crosschain.TransactionHash;
import org.qortal.crosschain.UnspentOutput;
import org.qortal.crosschain.Bitcoin.BitcoinNet;
import org.qortal.crosschain.ElectrumX.Server.ConnectionType;
import org.qortal.utils.BitTwiddling;

import com.google.common.hash.HashCode;

public class ElectrumXTests {

	static {
		// This must go before any calls to LogManager/Logger
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

		Security.insertProviderAt(new BouncyCastleProvider(), 0);
		Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);
	}

	private static final Map<ElectrumX.Server.ConnectionType, Integer> DEFAULT_ELECTRUMX_PORTS = new EnumMap<>(ElectrumX.Server.ConnectionType.class);
	static {
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.TCP, 50001);
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.SSL, 50002);
	}

	private ElectrumX getInstance() {
		return new ElectrumX("Bitcoin-" + BitcoinNet.TEST3.name(), BitcoinNet.TEST3.getGenesisHash(), BitcoinNet.TEST3.getServers(), DEFAULT_ELECTRUMX_PORTS);
	}

	@Test
	public void testInstance() {
		ElectrumX electrumX = getInstance();
		assertNotNull(electrumX);
	}

	@Test
	public void testGetCurrentHeight() throws ForeignBlockchainException {
		ElectrumX electrumX = getInstance();

		int height = electrumX.getCurrentHeight();

		assertTrue(height > 10000);
		System.out.println("Current TEST3 height: " + height);
	}

	@Test
	public void testInvalidRequest() {
		ElectrumX electrumX = getInstance();
		try {
			electrumX.getRawBlockHeaders(-1, -1);
		} catch (ForeignBlockchainException e) {
			// Should throw due to negative start block height
			return;
		}

		fail("Negative start block height should cause error");
	}

	@Test
	public void testGetRecentBlocks() throws ForeignBlockchainException {
		ElectrumX electrumX = getInstance();

		int height = electrumX.getCurrentHeight();
		assertTrue(height > 10000);

		List<byte[]> recentBlockHeaders = electrumX.getRawBlockHeaders(height - 11, 11);

		System.out.println(String.format("Returned %d recent blocks", recentBlockHeaders.size()));
		for (int i = 0; i < recentBlockHeaders.size(); ++i) {
			byte[] blockHeader = recentBlockHeaders.get(i);

			// Timestamp(int) is at 4 + 32 + 32 = 68 bytes offset
			int offset = 4 + 32 + 32;
			int timestamp = BitTwiddling.intFromLEBytes(blockHeader, offset);
			System.out.println(String.format("Block %d timestamp: %d", height + i, timestamp));
		}
	}

	@Test
	public void testGetP2PKHBalance() throws ForeignBlockchainException {
		ElectrumX electrumX = getInstance();

		Address address = Address.fromString(TestNet3Params.get(), "n3GNqMveyvaPvUbH469vDRadqpJMPc84JA");
		byte[] script = ScriptBuilder.createOutputScript(address).getProgram();
		long balance = electrumX.getConfirmedBalance(script);

		assertTrue(balance > 0L);

		System.out.println(String.format("TestNet address %s has balance: %d sats / %d.%08d BTC", address, balance, (balance / 100000000L), (balance % 100000000L)));
	}

	@Test
	public void testGetP2SHBalance() throws ForeignBlockchainException {
		ElectrumX electrumX = getInstance();

		Address address = Address.fromString(TestNet3Params.get(), "2N4szZUfigj7fSBCEX4PaC8TVbC5EvidaVF");
		byte[] script = ScriptBuilder.createOutputScript(address).getProgram();
		long balance = electrumX.getConfirmedBalance(script);

		assertTrue(balance > 0L);

		System.out.println(String.format("TestNet address %s has balance: %d sats / %d.%08d BTC", address, balance, (balance / 100000000L), (balance % 100000000L)));
	}

	@Test
	public void testGetUnspentOutputs() throws ForeignBlockchainException {
		ElectrumX electrumX = getInstance();

		Address address = Address.fromString(TestNet3Params.get(), "2N4szZUfigj7fSBCEX4PaC8TVbC5EvidaVF");
		byte[] script = ScriptBuilder.createOutputScript(address).getProgram();
		List<UnspentOutput> unspentOutputs = electrumX.getUnspentOutputs(script, false);

		assertFalse(unspentOutputs.isEmpty());

		for (UnspentOutput unspentOutput : unspentOutputs)
			System.out.println(String.format("TestNet address %s has unspent output at tx %s, output index %d", address, HashCode.fromBytes(unspentOutput.hash), unspentOutput.index));
	}

	@Test
	public void testGetRawTransaction() throws ForeignBlockchainException {
		ElectrumX electrumX = getInstance();

		byte[] txHash = HashCode.fromString("7653fea9ffcd829d45ed2672938419a94951b08175982021e77d619b553f29af").asBytes();

		byte[] rawTransactionBytes = electrumX.getRawTransaction(txHash);

		assertFalse(rawTransactionBytes.length == 0);
	}

	@Test
	public void testGetUnknownRawTransaction() {
		ElectrumX electrumX = getInstance();

		byte[] txHash = HashCode.fromString("f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0").asBytes();

		try {
			electrumX.getRawTransaction(txHash);
			fail("Bitcoin transaction should be unknown and hence throw exception");
		} catch (ForeignBlockchainException e) {
			if (!(e instanceof ForeignBlockchainException.NotFoundException))
				fail("Bitcoin transaction should be unknown and hence throw NotFoundException");
		}
	}

	@Test
	public void testGetTransaction() throws ForeignBlockchainException {
		ElectrumX electrumX = getInstance();

		String txHash = "7653fea9ffcd829d45ed2672938419a94951b08175982021e77d619b553f29af";

		BitcoinyTransaction transaction = electrumX.getTransaction(txHash);

		assertNotNull(transaction);
		assertTrue(transaction.txHash.equals(txHash));
	}

	@Test
	public void testGetUnknownTransaction() {
		ElectrumX electrumX = getInstance();

		String txHash = "f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0";

		try {
			electrumX.getTransaction(txHash);
			fail("Bitcoin transaction should be unknown and hence throw exception");
		} catch (ForeignBlockchainException e) {
			if (!(e instanceof ForeignBlockchainException.NotFoundException))
				fail("Bitcoin transaction should be unknown and hence throw NotFoundException");
		}
	}

	@Test
	public void testGetAddressTransactions() throws ForeignBlockchainException {
		ElectrumX electrumX = getInstance();

		Address address = Address.fromString(TestNet3Params.get(), "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE");
		byte[] script = ScriptBuilder.createOutputScript(address).getProgram();

		List<TransactionHash> transactionHashes = electrumX.getAddressTransactions(script, false);

		assertFalse(transactionHashes.isEmpty());
	}

}
