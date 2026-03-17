package org.qortal.block;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.naming.Name;
import org.qortal.repository.DataException;

import java.util.HashMap;
import java.util.Map;

/**
 * Invalid Name Registration Blocks
 * <p>
 * A node minted a version of block 535658 that contained one transaction:
 * a REGISTER_NAME transaction that attempted to register a name that was already registered.
 * <p>
 * This invalid transaction made block 535658 (rightly) invalid to several nodes,
 * which refused to use that block.
 * However, it seems there were no other nodes minting an alternative, valid block at that time
 * and so the chain stalled for several nodes in the network.
 * <p>
 * Additionally, the invalid block 535658 affected all new installations, regardless of whether
 * they synchronized from scratch (block 1) or used an 'official release' bootstrap.
 * <p>
 * The diagnosis found the following:
 * - The original problem occurred in block 535205 where for some unknown reason many nodes didn't
 *   add the name from a REGISTER_NAME transaction to their Names table.
 * - As a result, those nodes had a corrupt db, because they weren't holding a record of the name.
 * - This invalid db then caused them to treat a candidate for block 535658 as valid when it
 *   should have been invalid.
 * - As such, the chain continued on with a technically invalid block in it, for a subset of the network
 * <p>
 * As with block 212937, there were three options, but the only feasible one was to apply edits to block
 * 535658 to make it valid. There were several cross-chain trades completed after this block, so doing
 * any kind of rollback was out of the question.
 * <p>
 * To complicate things further, a custom data field was used for the first REGISTER_NAME transaction,
 * and the default data field was used for the second. So it was important that all nodes ended up with
 * the exact same data regardless of how they arrived there.
 * <p>
 * The invalid block 535658 signature is: <tt>3oiuDhok...NdXvCLEV</tt>.
 * <p>
 * The invalid transaction in block 212937 is:
 * <p>
 * <code><pre>
	 {
		 "type": "REGISTER_NAME",
		 "timestamp": 1630739437517,
		 "reference": "4peRechwSPxP6UkRj9Y8ox9YxkWb34sWk5zyMc1WyMxEsACxD4Gmm7LZVsQ6Skpze8QCSBMZasvEZg6RgdqkyADW",
		 "fee": "0.00100000",
		 "signature": "2t1CryCog8KPDBarzY5fDCKu499nfnUcGrz4Lz4w5wNb5nWqm7y126P48dChYY7huhufcBV3RJPkgKP4Ywxc1gXx",
		 "txGroupId": 0,
		 "blockHeight": 535658,
		 "approvalStatus": "NOT_REQUIRED",
		 "creatorAddress": "Qbx9ojxv7XNi1xDMWzzw7xDvd1zYW6SKFB",
		 "registrantPublicKey": "HJqGEf6cW695Xun4ydhkB2excGFwsDxznhNCRHZStyyx",
		 "name": "Qplay",
		 "data": "Registered Name on the Qortal Chain"
	 }
   </pre></code>
 * <p>
 * Account <tt>Qbx9ojxv7XNi1xDMWzzw7xDvd1zYW6SKFB</tt> attempted to register the name <tt>Qplay</tt>
 * when they had already registered it 12 hours before in block <tt>535205</tt>.
 * <p>
 * However, on the broken DB nodes, their Names table was missing a record for the `Qplay` name
 * which was sufficient to make the transaction valid.
 *
 * This problem then occurred two more times, in blocks 536140 and 541334
 * To reduce duplication, I have combined all three block fixes into a single class
 *
 */
public final class InvalidNameRegistrationBlocks {

	private static final Logger LOGGER = LogManager.getLogger(InvalidNameRegistrationBlocks.class);

	public static Map<Integer, String> invalidBlocksNamesMap = new HashMap<Integer, String>()
	{
		{
			put(535658, "Qplay");
			put(536140, "Qweb");
			put(541334, "Qithub");
		}
	};

	private InvalidNameRegistrationBlocks() {
		/* Do not instantiate */
	}

	public static boolean isAffectedBlock(int height) {
		return (invalidBlocksNamesMap.containsKey(height));
	}

	public static void processFix(Block block) throws DataException {
		Integer blockHeight = block.getBlockData().getHeight();
		String invalidName = invalidBlocksNamesMap.get(blockHeight);
		if (invalidName == null) {
			throw new DataException(String.format("Unable to lookup invalid name for block height %d", blockHeight));
		}

		// Unregister the existing name record if it exists
		// This ensures that the duplicate name is considered valid, and therefore
		// the second (i.e. duplicate) REGISTER_NAME transaction data is applied.
		// Both were issued by the same user account, so there is no conflict.
		Name name = new Name(block.repository, invalidName);
		name.unregister();

		LOGGER.debug("Applied name registration patch for block {}", blockHeight);
	}

	// Note:
	// There is no need to write an orphanFix() method, as we do not have
	// the necessary ATStatesData to orphan back this far anyway

}
