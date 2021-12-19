package org.qortal.block;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.qortal.data.account.AccountBalanceData;
import org.qortal.repository.DataException;

/**
 * Block 212937
 * <p>
 * Somehow a node minted a version of block 212937 that contained one transaction:
 * a PAYMENT transaction that attempted to spend more QORT than that account had as QORT balance.
 * <p>
 * This invalid transaction made block 212937 (rightly) invalid to several nodes,
 * which refused to use that block.
 * However, it seems there were no other nodes minting an alternative, valid block at that time
 * and so the chain stalled for several nodes in the network.
 * <p>
 * Additionally, the invalid block 212937 affected all new installations, regardless of whether
 * they synchronized from scratch (block 1) or used an 'official release' bootstrap.
 * <p>
 * After lengthy diagnosis, it was discovered that
 * the invalid transaction seemed to rely on incorrect balances in a corrupted database.
 * Copies of DB files containing the broken chain were also shared around, exacerbating the problem.
 * <p>
 * There were three options:
 * <ol>
 * <li>roll back the chain to last known valid block 212936 and re-mint empty blocks to current height</li>
 * <li>keep existing chain, but apply database edits at block 212937 to allow current chain to be valid</li>
 * <li>attempt to mint an alternative chain, retaining as many valid transactions as possible</li>
 * </ol>
 * <p>
 * Option 1 was highly undesirable due to knock-on effects from wiping 700+ transactions, some of which
 * might have affect cross-chain trades, although there were no cross-chain trade completed during
 * the decision period.
 * <p>
 * Option 3 was essentially a slightly better version of option 1 and rejected for similar reasons.
 * Attempts at option 3 also rapidly hit cumulative problems with every replacement block due to
 * differing block timestamps making some transactions, and then even some blocks themselves, invalid.
 * <p>
 * This class is the implementation of option 2.
 * <p>
 * The change in account balances are relatively small, see <tt>block-212937-deltas.json</tt> resource
 * for actual values. These values were obtained by exporting the <tt>AccountBalances</tt> table from
 * both versions of the database with chain at block 212936, and then comparing. The values were also
 * tested by syncing both databases up to block 225500, re-exporting and re-comparing.
 * <p>
 * The invalid block 212937 signature is: <tt>2J3GVJjv...qavh6KkQ</tt>.
 * <p>
 * The invalid transaction in block 212937 is:
 * <p>
 * <code><pre>
   {
      "amount" : "0.10788294",
      "approvalStatus" : "NOT_REQUIRED",
      "blockHeight" : 212937,
      "creatorAddress" : "QLdw5uabviLJgRGkRiydAFmAtZzxHfNXSs",
      "fee" : "0.00100000",
      "recipient" : "QZi1mNHDbiLvsytxTgxDr9nhJe4pNZaWpw",
      "reference" : "J6JukdTVuXZ3JYbHatfZzwxG2vSiZwVCPDzW5K7PsVQKRj8XZeDtqnkGCGGjaSQZ9bQMtV44ky88NnGM4YBQKU6",
      "senderPublicKey" : "DBFfbD2M3uh4jPE5PaUcZVvNPfrrJzVB7seeEtBn5SPs",
      "signature" : "qkitxdCEEnKt8w6wRfFixtErbXsxWE6zG2ESNhpqBdScikV1WxeA6WZTTMJVV4tCeZdBFXw3V1X5NVztv6LirWK",
      "timestamp" : 1607863074904,
      "txGroupId" : 0,
      "type" : "PAYMENT"
   }
   </pre></code>
 * <p>
 * Account <tt>QLdw5uabviLJgRGkRiydAFmAtZzxHfNXSs</tt> attempted to spend <tt>0.10888294</tt> (including fees)
 * when their QORT balance was really only <tt>0.10886665</tt>.
 * <p>
 * However, on the broken DB nodes, their balance
 * seemed to be <tt>0.10890293</tt> which was sufficient to make the transaction valid.
 */
public final class Block212937 {

	private static final Logger LOGGER = LogManager.getLogger(Block212937.class);
	private static final String ACCOUNT_DELTAS_SOURCE = "block-212937-deltas.json";

	private static final List<AccountBalanceData> accountDeltas = readAccountDeltas();

	private Block212937() {
		/* Do not instantiate */
	}

	@SuppressWarnings("unchecked")
	private static List<AccountBalanceData> readAccountDeltas() {
		Unmarshaller unmarshaller;

		try {
			// Create JAXB context aware of classes we need to unmarshal
			JAXBContext jc = JAXBContextFactory.createContext(new Class[] {
				AccountBalanceData.class
			}, null);

			// Create unmarshaller
			unmarshaller = jc.createUnmarshaller();

			// Set the unmarshaller media type to JSON
			unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");

			// Tell unmarshaller that there's no JSON root element in the JSON input
			unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);
		} catch (JAXBException e) {
			String message = "Failed to setup unmarshaller to read block 212937 deltas";
			LOGGER.error(message, e);
			throw new RuntimeException(message, e);
		}

		ClassLoader classLoader = BlockChain.class.getClassLoader();
		InputStream in = classLoader.getResourceAsStream(ACCOUNT_DELTAS_SOURCE);
		StreamSource jsonSource = new StreamSource(in);

		try  {
			// Attempt to unmarshal JSON stream to BlockChain config
			return (List<AccountBalanceData>) unmarshaller.unmarshal(jsonSource, AccountBalanceData.class).getValue();
		} catch (UnmarshalException e) {
			String message = "Failed to parse block 212937 deltas";
			LOGGER.error(message, e);
			throw new RuntimeException(message, e);
		} catch (JAXBException e) {
			String message = "Unexpected JAXB issue while processing block 212937 deltas";
			LOGGER.error(message, e);
			throw new RuntimeException(message, e);
		}
	}

	public static void processFix(Block block) throws DataException {
		block.repository.getAccountRepository().modifyAssetBalances(accountDeltas);
	}

	public static void orphanFix(Block block) throws DataException {
		// Create inverse deltas
		List<AccountBalanceData> inverseDeltas = accountDeltas.stream()
				.map(delta -> new AccountBalanceData(delta.getAddress(), delta.getAssetId(), 0 - delta.getBalance()))
				.collect(Collectors.toList());

		block.repository.getAccountRepository().modifyAssetBalances(inverseDeltas);
	}

}
