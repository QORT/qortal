package org.qortal.api.resource;

import com.google.common.primitives.Ints;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.qortal.account.Account;
import org.qortal.api.ApiError;
import org.qortal.api.ApiErrors;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.model.BlockMintingInfo;
import org.qortal.api.model.BlockSignerSummary;
import org.qortal.block.Block;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.AccountData;
import org.qortal.data.block.BlockData;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.data.block.DecodedOnlineAccountData;
import org.qortal.data.network.OnlineAccountData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.BlockArchiveReader;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.transform.TransformationException;
import org.qortal.transform.block.BlockTransformer;
import org.qortal.utils.Base58;
import org.qortal.utils.Blocks;
import org.qortal.utils.Triple;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Path("/blocks")
@Tag(name = "Blocks")
public class BlocksResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/signature/{signature}")
	@Operation(
		summary = "Fetch block using base58 signature",
		description = "Returns the block that matches the given signature",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.BLOCK_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public BlockData getBlock(@PathParam("signature") String signature58,
							  @QueryParam("includeOnlineSignatures") Boolean includeOnlineSignatures) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
		    // Check the database first
			BlockData blockData = repository.getBlockRepository().fromSignature(signature);
			if (blockData != null) {
				if (includeOnlineSignatures == null || !includeOnlineSignatures) {
					blockData.setOnlineAccountsSignatures(null);
				}
				return blockData;
			}

            // Not found, so try the block archive
			blockData = repository.getBlockArchiveRepository().fromSignature(signature);
			if (blockData != null) {
				if (includeOnlineSignatures == null || !includeOnlineSignatures) {
					blockData.setOnlineAccountsSignatures(null);
				}
				return blockData;
			}

			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/signature/{signature}/data")
	@Operation(
			summary = "Fetch serialized, base58 encoded block data using base58 signature",
			description = "Returns serialized data for the block that matches the given signature, and an optional block serialization version",
			responses = {
					@ApiResponse(
							description = "the block data",
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string"))
					)
			}
	)
	@ApiErrors({
			ApiError.INVALID_SIGNATURE, ApiError.BLOCK_UNKNOWN, ApiError.INVALID_DATA, ApiError.REPOSITORY_ISSUE
	})
	public String getSerializedBlockData(@PathParam("signature") String signature58, @QueryParam("version") Integer version) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {

			// Default to version 1
			if (version == null) {
				version = 1;
			}

            // Check the database first
			BlockData blockData = repository.getBlockRepository().fromSignature(signature);
			if (blockData != null) {
                Block block = new Block(repository, blockData);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                bytes.write(Ints.toByteArray(block.getBlockData().getHeight()));

				switch (version) {
					case 1:
						bytes.write(BlockTransformer.toBytes(block));
						break;

					case 2:
						bytes.write(BlockTransformer.toBytesV2(block));
						break;

					default:
						throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
				}

                return Base58.encode(bytes.toByteArray());
            }

            // Not found, so try the block archive
            Triple<byte[], Integer, Integer> serializedBlock = BlockArchiveReader.getInstance().fetchSerializedBlockBytesForSignature(signature, false, repository);
            if (serializedBlock != null) {
				byte[] bytes = serializedBlock.getA();
				Integer serializationVersion = serializedBlock.getB();
				if (version != serializationVersion) {
					// TODO: we could quite easily reserialize the block with the requested version
					throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Block is not stored using requested serialization version.");
				}
				return Base58.encode(bytes);
            }

            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA, e);
		} catch (DataException | IOException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/signature/{signature}/transactions")
	@Operation(
		summary = "Fetch block using base58 signature",
		description = "Returns the block that matches the given signature",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = TransactionData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.BLOCK_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public List<TransactionData> getBlockTransactions(@PathParam("signature") String signature58, @Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Check if the block exists in either the database or archive
			int height = repository.getBlockRepository().getHeightFromSignature(signature);
			if (height == 0) {
				height = repository.getBlockArchiveRepository().getHeightFromSignature(signature);
				if (height == 0) {
					// Not found in either the database or archive
					throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
				}
			}

			List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, null, height, height);

			// Expand signatures to transactions
			List<TransactionData> transactions = new ArrayList<>(signatures.size());
			for (byte[] s : signatures) {
				transactions.add(repository.getTransactionRepository().fromSignature(s));
			}

			return transactions;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/first")
	@Operation(
		summary = "Fetch genesis block",
		description = "Returns the genesis block",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public BlockData getFirstBlock() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Check the database first
			BlockData blockData = repository.getBlockRepository().fromHeight(1);
			if (blockData != null) {
				return blockData;
			}

			// Try the archive
			blockData = repository.getBlockArchiveRepository().fromHeight(1);
			if (blockData != null) {
				return blockData;
			}

			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/last")
	@Operation(
		summary = "Fetch last/newest block in blockchain",
		description = "Returns the last valid block",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public BlockData getLastBlock(@QueryParam("includeOnlineSignatures") Boolean includeOnlineSignatures) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = repository.getBlockRepository().getLastBlock();

			if (includeOnlineSignatures == null || !includeOnlineSignatures) {
				blockData.setOnlineAccountsSignatures(null);
			}

			return blockData;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/child/{signature}")
	@Operation(
		summary = "Fetch child block using base58 signature of parent block",
		description = "Returns the child block of the block that matches the given signature",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.BLOCK_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public BlockData getChild(@PathParam("signature") String signature58) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData childBlockData = null;

			// Check if block exists in database
			BlockData blockData = repository.getBlockRepository().fromSignature(signature);
			if (blockData != null) {
				return repository.getBlockRepository().fromReference(signature);
			}

			// Not found, so try the archive
			// This also checks that the parent block exists
			// It will return null if either the parent or child don't exit
			childBlockData = repository.getBlockArchiveRepository().fromReference(signature);

			// Check child block exists
			if (childBlockData == null) {
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
			}

			// Check child block's reference matches the supplied signature
			if (!Arrays.equals(childBlockData.getReference(), signature)) {
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
			}

			return childBlockData;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/height")
	@Operation(
		summary = "Current blockchain height",
		description = "Returns the block height of the last block.",
		responses = {
			@ApiResponse(
				description = "the height",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "number"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public int getHeight() {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getBlockRepository().getBlockchainHeight();
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/height/{signature}")
	@Operation(
		summary = "Height of specific block",
		description = "Returns the block height of the block that matches the given signature",
		responses = {
			@ApiResponse(
				description = "the height",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "number"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.BLOCK_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public int getHeight(@PathParam("signature") String signature58) {
		// Decode signature
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Firstly check the database
			BlockData blockData = repository.getBlockRepository().fromSignature(signature);
			if (blockData != null) {
				return blockData.getHeight();
			}

			// Not found, so try the archive
			blockData = repository.getBlockArchiveRepository().fromSignature(signature);
			if (blockData != null) {
				return blockData.getHeight();
			}

			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);

		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/byheight/{height}")
	@Operation(
		summary = "Fetch block using block height",
		description = "Returns the block with given height",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.BLOCK_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public BlockData getByHeight(@PathParam("height") int height,
								 @QueryParam("includeOnlineSignatures") Boolean includeOnlineSignatures) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Firstly check the database
			BlockData blockData = repository.getBlockRepository().fromHeight(height);
			if (blockData != null) {
				if (includeOnlineSignatures == null || !includeOnlineSignatures) {
					blockData.setOnlineAccountsSignatures(null);
				}
				return blockData;
			}

			// Not found, so try the archive
			blockData = repository.getBlockArchiveRepository().fromHeight(height);
			if (blockData != null) {
				if (includeOnlineSignatures == null || !includeOnlineSignatures) {
					blockData.setOnlineAccountsSignatures(null);
				}
				return blockData;
			}

			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);

		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/byheight/{height}/mintinginfo")
	@Operation(
			summary = "Fetch block minter info using block height",
			description = "Returns the minter info for the block with given height",
			responses = {
					@ApiResponse(
							description = "the block",
							content = @Content(
									schema = @Schema(
											implementation = BlockData.class
									)
							)
					)
			}
	)
	@ApiErrors({
			ApiError.BLOCK_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public BlockMintingInfo getBlockMintingInfoByHeight(@PathParam("height") int height) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			// Try the database
			BlockData blockData = repository.getBlockRepository().fromHeight(height);
			if (blockData == null) {

				// Not found, so try the archive
				blockData = repository.getBlockArchiveRepository().fromHeight(height);
				if (blockData == null) {

					// Still not found
					throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
				}
			}

			Block block = new Block(repository, blockData);
			BlockData parentBlockData = repository.getBlockRepository().fromSignature(blockData.getReference());
			if (parentBlockData == null) {
				// Parent block not found - try the archive
				parentBlockData = repository.getBlockArchiveRepository().fromSignature(blockData.getReference());
				if (parentBlockData == null) {

					// Still not found
					throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
				}
			}

			String minterAddress = Account.getRewardShareMintingAddress(repository, blockData.getMinterPublicKey());
			int minterLevel = Account.getRewardShareEffectiveMintingLevel(repository, blockData.getMinterPublicKey());
			if (minterLevel == 0)
				// This may be unavailable when requesting a trimmed block
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

			BigInteger distance = block.calcKeyDistance(parentBlockData.getHeight(), parentBlockData.getSignature(), blockData.getMinterPublicKey(), minterLevel);
			double ratio = new BigDecimal(distance).divide(new BigDecimal(block.MAX_DISTANCE), 40, RoundingMode.DOWN).doubleValue();
			long timestamp = block.calcTimestamp(parentBlockData, blockData.getMinterPublicKey(), minterLevel);
			long timeDelta = timestamp - parentBlockData.getTimestamp();

			BlockMintingInfo blockMintingInfo = new BlockMintingInfo();
			blockMintingInfo.minterPublicKey = blockData.getMinterPublicKey();
			blockMintingInfo.minterAddress = minterAddress;
			blockMintingInfo.minterLevel = minterLevel;
			blockMintingInfo.onlineAccountsCount = blockData.getOnlineAccountsCount();
			blockMintingInfo.maxDistance = new BigDecimal(block.MAX_DISTANCE);
			blockMintingInfo.keyDistance = distance;
			blockMintingInfo.keyDistanceRatio = ratio;
			blockMintingInfo.timestamp = timestamp;
			blockMintingInfo.timeDelta = timeDelta;

			return blockMintingInfo;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/timestamp/{timestamp}")
	@Operation(
		summary = "Fetch nearest block before given timestamp",
		responses = {
			@ApiResponse(
				description = "the block",
				content = @Content(
					schema = @Schema(
						implementation = BlockData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.BLOCK_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public BlockData getByTimestamp(@PathParam("timestamp") long timestamp,
									@QueryParam("includeOnlineSignatures") Boolean includeOnlineSignatures) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			BlockData blockData = null;

			// Try the Blocks table
			int height = repository.getBlockRepository().getHeightFromTimestamp(timestamp);
			if (height > 1) {
				// Found match in Blocks table
				blockData = repository.getBlockRepository().fromHeight(height);
				if (includeOnlineSignatures == null || !includeOnlineSignatures) {
					blockData.setOnlineAccountsSignatures(null);
				}
				return blockData;
			}

			// Not found in Blocks table, so try the archive
			height = repository.getBlockArchiveRepository().getHeightFromTimestamp(timestamp);
			if (height > 1) {
				// Found match in archive
				blockData = repository.getBlockArchiveRepository().fromHeight(height);
			}

			// Ensure block exists
			if (blockData == null) {
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
			}

			if (includeOnlineSignatures == null || !includeOnlineSignatures) {
				blockData.setOnlineAccountsSignatures(null);
			}

			return blockData;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/range/{height}")
	@Operation(
		summary = "Fetch blocks starting with given height",
		description = "Returns blocks starting with given height.",
		responses = {
			@ApiResponse(
				description = "blocks",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = BlockData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public List<BlockData> getBlockRange(@PathParam("height") int height,
										 @Parameter(ref = "count") @QueryParam("count") int count,
										 @Parameter(ref = "reverse") @QueryParam("reverse") Boolean reverse,
										 @QueryParam("includeOnlineSignatures") Boolean includeOnlineSignatures) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			List<BlockData> blocks = new ArrayList<>();
			boolean shouldReverse = (reverse != null && reverse);

			int i = 0;
			while (i < count) {
				BlockData blockData = repository.getBlockRepository().fromHeight(height);
				if (blockData == null) {
					// Not found - try the archive
					blockData = repository.getBlockArchiveRepository().fromHeight(height);
					if (blockData == null) {
						// Run out of blocks!
						break;
					}
				}
				if (includeOnlineSignatures == null || !includeOnlineSignatures) {
					blockData.setOnlineAccountsSignatures(null);
				}

				blocks.add(blockData);

				height = shouldReverse ? height - 1 : height + 1;
				i++;
			}

			return blocks;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/signer/{address}")
	@Operation(
		summary = "Fetch block summaries for blocks signed by address",
		responses = {
			@ApiResponse(
				description = "block summaries",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = BlockSummaryData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.PUBLIC_KEY_NOT_FOUND, ApiError.REPOSITORY_ISSUE})
	public List<BlockSummaryData> getBlockSummariesBySigner(@PathParam("address") String address, @Parameter(
			ref = "limit"
			) @QueryParam("limit") Integer limit, @Parameter(
				ref = "offset"
			) @QueryParam("offset") Integer offset, @Parameter(
				ref = "reverse"
			) @QueryParam("reverse") Boolean reverse) {
		if (!Crypto.isValidAddress(address))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			// Get public key from address
			AccountData accountData = repository.getAccountRepository().getAccount(address);
			if (accountData == null || accountData.getPublicKey() == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.PUBLIC_KEY_NOT_FOUND);


			List<BlockSummaryData> summaries = repository.getBlockRepository()
					.getBlockSummariesBySigner(accountData.getPublicKey(), limit, offset, reverse);

			// Add any from the archive
			List<BlockSummaryData> archivedSummaries = repository.getBlockArchiveRepository()
					.getBlockSummariesBySigner(accountData.getPublicKey(), limit, offset, reverse);
			if (archivedSummaries != null && !archivedSummaries.isEmpty()) {
				summaries.addAll(archivedSummaries);
			}
			else {
				summaries = archivedSummaries;
			}

			// Sort the results (because they may have been obtained from two places)
			if (reverse != null && reverse) {
				summaries.sort((s1, s2) -> Integer.valueOf(s2.getHeight()).compareTo(Integer.valueOf(s1.getHeight())));
			}
			else {
				summaries.sort(Comparator.comparing(s -> Integer.valueOf(s.getHeight())));
			}

			return summaries;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/signers")
	@Operation(
		summary = "Show summary of block signers",
		description = "Returns count of blocks signed, optionally limited to minters/recipients in passed address(es).",
		responses = {
			@ApiResponse(
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = BlockSignerSummary.class
						)
					)
				)
			)
		}
	)
	public List<BlockSignerSummary> getBlockSigners(@QueryParam("address") List<String> addresses,
			@Parameter(
				ref = "limit"
			) @QueryParam("limit") Integer limit, @Parameter(
				ref = "offset"
			) @QueryParam("offset") Integer offset, @Parameter(
				ref = "reverse"
			) @QueryParam("reverse") Boolean reverse) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			for (String address : addresses)
				if (!Crypto.isValidAddress(address))
					throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

			// This method pulls data from both Blocks and BlockArchive, so no need to query serparately
			return repository.getBlockArchiveRepository().getBlockSigners(addresses, limit, offset, reverse);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/summaries")
	@Operation(
		summary = "Fetch only summary info about a range of blocks",
		description = "Specify up to 2 out 3 of: start, end and count. If neither start nor end are specified, then end is assumed to be latest block. Where necessary, count is assumed to be 50.",
		responses = {
			@ApiResponse(
				description = "blocks",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = BlockSummaryData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public List<BlockSummaryData> getBlockSummaries(
			@QueryParam("start") Integer startHeight,
			@QueryParam("end") Integer endHeight,
			@Parameter(ref = "count") @QueryParam("count") Integer count) {
		// Check up to 2 out of 3 params
		if (startHeight != null && endHeight != null && count != null)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		// Check values
		if ((startHeight != null && startHeight < 1) || (endHeight != null && endHeight < 1) || (count != null && count < 1))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		try (final Repository repository = RepositoryManager.getRepository()) {

			/*
			 * start	end		count		result
			 * 10		40		null		blocks 10 to 39 (excludes end block, ignore count)
			 *
			 * null		null	null		blocks 1 to 50 (assume count=50, maybe start=1)
			 * 30		null	null		blocks 30 to 79 (assume count=50)
			 * 30		null	10			blocks 30 to 39
			 *
			 * null		null	50			last 50 blocks? so if max(blocks.height) is 200, then blocks 151 to 200
			 * null		200		null		blocks 150 to 199 (excludes end block, assume count=50)
			 * null		200		10			blocks 190 to 199 (excludes end block)
			 */

			List<BlockSummaryData> blockSummaries = new ArrayList<>();

			// Use the latest X blocks if only a count is specified
			if (startHeight == null && endHeight == null && count != null) {
				BlockData chainTip = repository.getBlockRepository().getLastBlock();
				startHeight = chainTip.getHeight() - count;
				endHeight = chainTip.getHeight();
			}

			// ... otherwise default the start height to 1
			if (startHeight == null && endHeight == null) {
				startHeight = 1;
			}

			// Default the count to 50
			if (count == null) {
				count = 50;
			}

			// If both a start and end height exist, ignore the count
			if (startHeight != null && endHeight != null) {
				if (startHeight > 0 && endHeight > 0) {
					count = Integer.MAX_VALUE;
				}
			}

			// Derive start height from end height if missing
			if (startHeight == null || startHeight == 0) {
				if (endHeight != null && endHeight > 0) {
					if (count != null) {
						startHeight = endHeight - count;
					}
				}
			}

			for (/* count already set */; count > 0; --count, ++startHeight) {
				if (endHeight != null && startHeight >= endHeight) {
					break;
				}
				BlockData blockData = repository.getBlockRepository().fromHeight(startHeight);
				if (blockData == null) {
					// Not found - try the archive
					blockData = repository.getBlockArchiveRepository().fromHeight(startHeight);
					if (blockData == null) {
						// Run out of blocks!
						break;
					}
				}

				if (blockData != null) {
					BlockSummaryData blockSummaryData = new BlockSummaryData(blockData);
					blockSummaries.add(blockSummaryData);
				}
			}

			return blockSummaries;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/onlineaccounts/{height}")
	@Operation(
			summary = "Get online accounts for block",
			description = "Returns the online accounts who submitted signatures for this block",
			responses = {
					@ApiResponse(
							description = "online accounts",
							content = @Content(
									array = @ArraySchema(
											schema = @Schema(
													implementation = DecodedOnlineAccountData.class
											)
									)
							)
					)
			}
	)
	@ApiErrors({
			ApiError.BLOCK_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public Set<DecodedOnlineAccountData> getOnlineAccounts(@PathParam("height") int height) {

		try (final Repository repository = RepositoryManager.getRepository()) {

			// get block from database
			BlockData blockData = repository.getBlockRepository().fromHeight(height);

			// if block data is not in the database, then try the archive
			if (blockData == null) {
				blockData = repository.getBlockArchiveRepository().fromHeight(height);

				// if the block is not in the database or the archive, then the block is unknown
				if( blockData == null ) {
					throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);
				}
			}

			Set<DecodedOnlineAccountData> onlineAccounts = Blocks.getDecodedOnlineAccountsForBlock(repository, blockData);

			return onlineAccounts;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE);
		}
	}
}