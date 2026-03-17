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
import java.util.*;
import java.util.stream.Collectors;


/**
 * Due to various bugs - which have been fixed - a small amount of balance drift occurred
 * in the chainstate of running nodes and bootstraps, when compared with a clean sync from genesis.
 * This resulted in a significant number of invalid transactions in the chain history due to
 * subtle balance discrepancies. The sum of all discrepancies that resulted in an invalid
 * transaction is 0.00198322 QORT, so despite the large quantity of transactions, they
 * represent an insignificant amount when summed.
 * <p>
 * This class is responsible for retroactively fixing all the past transactions which
 * are invalid due to the balance discrepancies.
 */


public final class InvalidBalanceBlocks {

	private static final Logger LOGGER = LogManager.getLogger(InvalidBalanceBlocks.class);

	private static final String ACCOUNT_DELTAS_SOURCE = "invalid-transaction-balance-deltas.json";

	private static final List<AccountBalanceData> accountDeltas = readAccountDeltas();
	private static final List<Integer> affectedHeights = getAffectedHeights();

	private InvalidBalanceBlocks() {
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
			String message = "Failed to parse balance deltas";
			LOGGER.error(message, e);
			throw new RuntimeException(message, e);
		} catch (JAXBException e) {
			String message = "Unexpected JAXB issue while processing balance deltas";
			LOGGER.error(message, e);
			throw new RuntimeException(message, e);
		}
	}

	private static List<Integer> getAffectedHeights() {
		List<Integer> heights = new ArrayList<>();
		for (AccountBalanceData accountBalanceData : accountDeltas) {
			if (!heights.contains(accountBalanceData.getHeight())) {
				heights.add(accountBalanceData.getHeight());
			}
		}
		return heights;
	}

	private static List<AccountBalanceData> getAccountDeltasAtHeight(int height) {
		return accountDeltas.stream().filter(a -> a.getHeight() == height).collect(Collectors.toList());
	}

	public static boolean isAffectedBlock(int height) {
		return affectedHeights.contains(Integer.valueOf(height));
	}

	public static void processFix(Block block) throws DataException {
		Integer blockHeight = block.getBlockData().getHeight();
		List<AccountBalanceData> deltas = getAccountDeltasAtHeight(blockHeight);
		if (deltas == null) {
			throw new DataException(String.format("Unable to lookup invalid balance data for block height %d", blockHeight));
		}

		block.repository.getAccountRepository().modifyAssetBalances(deltas);

		LOGGER.info("Applied balance patch for block {}", blockHeight);
	}

	public static void orphanFix(Block block) throws DataException {
		Integer blockHeight = block.getBlockData().getHeight();
		List<AccountBalanceData> deltas = getAccountDeltasAtHeight(blockHeight);
		if (deltas == null) {
			throw new DataException(String.format("Unable to lookup invalid balance data for block height %d", blockHeight));
		}

		// Create inverse delta(s)
		for (AccountBalanceData accountBalanceData : deltas) {
			AccountBalanceData inverseBalanceData = new AccountBalanceData(accountBalanceData.getAddress(), accountBalanceData.getAssetId(), -accountBalanceData.getBalance());
			block.repository.getAccountRepository().modifyAssetBalances(List.of(inverseBalanceData));
		}

		LOGGER.info("Reverted balance patch for block {}", blockHeight);
	}

}
