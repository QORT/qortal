package org.qortal.block;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.qortal.data.account.AccountBalanceData;
import org.qortal.repository.DataException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Block 1333492
 * <p>
 * As described in InvalidBalanceBlocks.java, legacy bugs caused a small drift in account balances.
 * This block adjusts any remaining differences between a clean reindex/resync and a recent bootstrap.
 * <p>
 * The block height 1333492 isn't significant - it's simply the height of a recent bootstrap at the
 * time of development, so that the account balances could be accessed and compared against the same
 * block in a reindexed db.
 * <p>
 * As with InvalidBalanceBlocks, the discrepancies are insignificant, except for a single
 * account which has a 3.03 QORT discrepancy. This was due to the account being the first recipient
 * of a name sale and encountering an early bug in this area.
 * <p>
 * The total offset for this block is 3.02816514 QORT.
 */
public final class Block1333492 {

	private static final Logger LOGGER = LogManager.getLogger(Block1333492.class);
	private static final String ACCOUNT_DELTAS_SOURCE = "block-1333492-deltas.json";

	private static final List<AccountBalanceData> accountDeltas = readAccountDeltas();

	private Block1333492() {
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
			String message = "Failed to setup unmarshaller to read block 1333492 deltas";
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
			String message = "Failed to parse block 1333492 deltas";
			LOGGER.error(message, e);
			throw new RuntimeException(message, e);
		} catch (JAXBException e) {
			String message = "Unexpected JAXB issue while processing block 1333492 deltas";
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
