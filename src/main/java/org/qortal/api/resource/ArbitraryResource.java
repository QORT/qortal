package org.qortal.api.resource;

import com.google.common.primitives.Bytes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;
import org.qortal.api.*;
import org.qortal.api.resource.TransactionsResource.ConfirmationStatus;
import org.qortal.arbitrary.*;
import org.qortal.arbitrary.ArbitraryDataFile.ResourceIdType;
import org.qortal.arbitrary.exception.MissingDataException;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.Controller;
import org.qortal.controller.arbitrary.ArbitraryDataStorageManager;
import org.qortal.data.account.AccountData;
import org.qortal.data.arbitrary.ArbitraryResourceInfo;
import org.qortal.data.arbitrary.ArbitraryResourceNameInfo;
import org.qortal.data.arbitrary.ArbitraryResourceStatus;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.transaction.ArbitraryTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.ArbitraryTransactionTransformer;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.Base58;
import org.qortal.utils.ZipUtils;

@Path("/arbitrary")
@Tag(name = "Arbitrary")
public class ArbitraryResource {

	private static final Logger LOGGER = LogManager.getLogger(ArbitraryResource.class);

	@Context HttpServletRequest request;
	@Context HttpServletResponse response;
	@Context ServletContext context;

	@GET
	@Path("/resources")
	@Operation(
			summary = "List arbitrary resources available on chain, optionally filtered by service and identifier",
			description = "- If the identifier parameter is missing or empty, it will return an unfiltered list of all possible identifiers.\n" +
					"- If an identifier is specified, only resources with a matching identifier will be returned.\n" +
					"- If default is set to true, only resources without identifiers will be returned.",
			responses = {
					@ApiResponse(
							content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ArbitraryResourceInfo.class))
					)
			}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<ArbitraryResourceInfo> getResources(
			@QueryParam("service") Service service,
			@QueryParam("identifier") String identifier,
			@Parameter(description = "Default resources (without identifiers) only") @QueryParam("default") Boolean defaultResource,
			@Parameter(ref = "limit") @QueryParam("limit") Integer limit,
			@Parameter(ref = "offset") @QueryParam("offset") Integer offset,
			@Parameter(ref = "reverse") @QueryParam("reverse") Boolean reverse,
			@Parameter(description = "Include status") @QueryParam("includestatus") Boolean includeStatus) {

		try (final Repository repository = RepositoryManager.getRepository()) {

			// Treat empty identifier as null
			if (identifier != null && identifier.isEmpty()) {
				identifier = null;
			}

			// Ensure that "default" and "identifier" parameters cannot coexist
			boolean defaultRes = Boolean.TRUE.equals(defaultResource);
			if (defaultRes == true && identifier != null) {
				throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "identifier cannot be specified when requesting a default resource");
			}

			List<ArbitraryResourceInfo> resources = repository.getArbitraryRepository()
					.getArbitraryResources(service, identifier, null, defaultRes, limit, offset, reverse);

			if (resources == null) {
				return new ArrayList<>();
			}

			if (includeStatus != null && includeStatus == true) {
				resources = this.addStatusToResources(resources);
			}

			return resources;

		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/resources/search")
	@Operation(
			summary = "Search arbitrary resources available on chain, optionally filtered by service.\n" +
					"If default is set to true, only resources without identifiers will be returned.",
			responses = {
					@ApiResponse(
							content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ArbitraryResourceInfo.class))
					)
			}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<ArbitraryResourceInfo> searchResources(
			@QueryParam("service") Service service,
			@QueryParam("query") String query,
			@Parameter(description = "Default resources (without identifiers) only") @QueryParam("default") Boolean defaultResource,
			@Parameter(ref = "limit") @QueryParam("limit") Integer limit,
			@Parameter(ref = "offset") @QueryParam("offset") Integer offset,
			@Parameter(ref = "reverse") @QueryParam("reverse") Boolean reverse,
			@Parameter(description = "Include status") @QueryParam("includestatus") Boolean includeStatus) {

		try (final Repository repository = RepositoryManager.getRepository()) {

			boolean defaultRes = Boolean.TRUE.equals(defaultResource);

			List<ArbitraryResourceInfo> resources = repository.getArbitraryRepository()
					.searchArbitraryResources(service, query, defaultRes, limit, offset, reverse);

			if (resources == null) {
				return new ArrayList<>();
			}

			if (includeStatus != null && includeStatus == true) {
				resources = this.addStatusToResources(resources);
			}

			return resources;

		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/resources/names")
	@Operation(
			summary = "List arbitrary resources available on chain, grouped by creator's name",
			responses = {
					@ApiResponse(
							content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ArbitraryResourceInfo.class))
					)
			}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<ArbitraryResourceNameInfo> getResourcesGroupedByName(
			@QueryParam("service") Service service,
			@QueryParam("identifier") String identifier,
			@Parameter(description = "Default resources (without identifiers) only") @QueryParam("default") Boolean defaultResource,
			@Parameter(ref = "limit") @QueryParam("limit") Integer limit,
			@Parameter(ref = "offset") @QueryParam("offset") Integer offset,
			@Parameter(ref = "reverse") @QueryParam("reverse") Boolean reverse,
			@Parameter(description = "Include status") @QueryParam("includestatus") Boolean includeStatus) {

		try (final Repository repository = RepositoryManager.getRepository()) {

			// Treat empty identifier as null
			if (identifier != null && identifier.isEmpty()) {
				identifier = null;
			}

			// Ensure that "default" and "identifier" parameters cannot coexist
			boolean defaultRes = Boolean.TRUE.equals(defaultResource);
			if (defaultRes == true && identifier != null) {
				throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "identifier cannot be specified when requesting a default resource");
			}

			List<ArbitraryResourceNameInfo> creatorNames = repository.getArbitraryRepository()
					.getArbitraryResourceCreatorNames(service, identifier, defaultRes, limit, offset, reverse);

			for (ArbitraryResourceNameInfo creatorName : creatorNames) {
				String name = creatorName.name;
				if (name != null) {
					List<ArbitraryResourceInfo> resources = repository.getArbitraryRepository()
							.getArbitraryResources(service, identifier, name, defaultRes, null, null, reverse);

					if (includeStatus != null && includeStatus == true) {
						resources = this.addStatusToResources(resources);
					}
					creatorName.resources = resources;
				}
			}

			return creatorNames;

		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/resource/status/{service}/{name}")
	@Operation(
			summary = "Get status of arbitrary resource with supplied service and name",
			description = "If build is set to true, the resource will be built synchronously before returning the status.",
			responses = {
					@ApiResponse(
							content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ArbitraryResourceStatus.class))
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public ArbitraryResourceStatus getDefaultResourceStatus(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
															@PathParam("service") Service service,
															@PathParam("name") String name,
															@QueryParam("build") Boolean build) {

		Security.requirePriorAuthorizationOrApiKey(request, name, service, null);
		return this.getStatus(service, name, null, build);
	}

	@GET
	@Path("/resource/status/{service}/{name}/{identifier}")
	@Operation(
			summary = "Get status of arbitrary resource with supplied service, name and identifier",
			description = "If build is set to true, the resource will be built synchronously before returning the status.",
			responses = {
					@ApiResponse(
							content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ArbitraryResourceStatus.class))
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public ArbitraryResourceStatus getResourceStatus(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
													 @PathParam("service") Service service,
													 @PathParam("name") String name,
													 @PathParam("identifier") String identifier,
													 @QueryParam("build") Boolean build) {

		Security.requirePriorAuthorizationOrApiKey(request, name, service, identifier);
		return this.getStatus(service, name, identifier, build);
	}


	@GET
	@Path("/search")
	@Operation(
		summary = "Find matching arbitrary transactions",
		description = "Returns transactions that match criteria. At least either service or address or limit <= 20 must be provided. Block height ranges allowed when searching CONFIRMED transactions ONLY.",
		responses = {
			@ApiResponse(
				description = "transactions",
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
		ApiError.INVALID_CRITERIA, ApiError.REPOSITORY_ISSUE
	})
	public List<TransactionData> searchTransactions(@QueryParam("startBlock") Integer startBlock, @QueryParam("blockLimit") Integer blockLimit,
			@QueryParam("txGroupId") Integer txGroupId,
			@QueryParam("service") Service service,
			@QueryParam("name") String name,
			@QueryParam("address") String address, @Parameter(
				description = "whether to include confirmed, unconfirmed or both",
				required = true
			) @QueryParam("confirmationStatus") ConfirmationStatus confirmationStatus, @Parameter(
				ref = "limit"
			) @QueryParam("limit") Integer limit, @Parameter(
				ref = "offset"
			) @QueryParam("offset") Integer offset, @Parameter(
				ref = "reverse"
			) @QueryParam("reverse") Boolean reverse) {
		// Must have at least one of txType / address / limit <= 20
		if (service == null && (address == null || address.isEmpty()) && (limit == null || limit > 20))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		// You can't ask for unconfirmed and impose a block height range
		if (confirmationStatus != ConfirmationStatus.CONFIRMED && (startBlock != null || blockLimit != null))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		List<TransactionType> txTypes = new ArrayList<>();
		txTypes.add(TransactionType.ARBITRARY);

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(startBlock, blockLimit, txGroupId, txTypes,
					service, name, address, confirmationStatus, limit, offset, reverse);

			// Expand signatures to transactions
			List<TransactionData> transactions = new ArrayList<>(signatures.size());
			for (byte[] signature : signatures)
				transactions.add(repository.getTransactionRepository().fromSignature(signature));

			return transactions;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Operation(
		summary = "Build raw, unsigned, ARBITRARY transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = ArbitraryTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION, ApiError.INVALID_DATA, ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String createArbitrary(ArbitraryTransactionData transactionData) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		if (transactionData.getDataType() == null)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = ArbitraryTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/relaymode")
	@Operation(
			summary = "Returns whether relay mode is enabled or not",
			responses = {
					@ApiResponse(
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "boolean"))
					)
			}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public boolean getRelayMode(@HeaderParam(Security.API_KEY_HEADER) String apiKey) {
		Security.checkApiCallAllowed(request);

		return Settings.getInstance().isRelayModeEnabled();
	}

	@GET
	@Path("/hosted/transactions")
	@Operation(
			summary = "List arbitrary transactions hosted by this node",
			responses = {
					@ApiResponse(
							content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ArbitraryTransactionData.class))
					)
			}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<ArbitraryTransactionData> getHostedTransactions(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
																@Parameter(ref = "limit") @QueryParam("limit") Integer limit,
																@Parameter(ref = "offset") @QueryParam("offset") Integer offset) {
		Security.checkApiCallAllowed(request);

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<ArbitraryTransactionData> hostedTransactions = ArbitraryDataStorageManager.getInstance().listAllHostedTransactions(repository, limit, offset);

			return hostedTransactions;

		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/hosted/resources")
	@Operation(
			summary = "List arbitrary resources hosted by this node",
			responses = {
					@ApiResponse(
							content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ArbitraryResourceInfo.class))
					)
			}
	)
	@ApiErrors({ApiError.REPOSITORY_ISSUE})
	public List<ArbitraryResourceInfo> getHostedResources(
			@HeaderParam(Security.API_KEY_HEADER) String apiKey,
			@Parameter(description = "Include status") @QueryParam("includestatus") Boolean includeStatus,
			@Parameter(ref = "limit") @QueryParam("limit") Integer limit,
			@Parameter(ref = "offset") @QueryParam("offset") Integer offset) {
		Security.checkApiCallAllowed(request);

		List<ArbitraryResourceInfo> resources = new ArrayList<>();

		try (final Repository repository = RepositoryManager.getRepository()) {

			List<ArbitraryTransactionData> transactionDataList = ArbitraryDataStorageManager.getInstance().listAllHostedTransactions(repository, limit, offset);
			for (ArbitraryTransactionData transactionData : transactionDataList) {
				ArbitraryResourceInfo arbitraryResourceInfo = new ArbitraryResourceInfo();
				arbitraryResourceInfo.name = transactionData.getName();
				arbitraryResourceInfo.service = transactionData.getService();
				arbitraryResourceInfo.identifier = transactionData.getIdentifier();
				if (!resources.contains(arbitraryResourceInfo)) {
					resources.add(arbitraryResourceInfo);
				}
			}

			if (includeStatus != null && includeStatus == true) {
				resources = this.addStatusToResources(resources);
			}

			return resources;

		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@DELETE
	@Path("/resource/{service}/{name}/{identifier}")
	@Operation(
			summary = "Delete arbitrary resource with supplied service, name and identifier",
			responses = {
					@ApiResponse(
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string"))
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public boolean deleteResource(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
								  @PathParam("service") Service service,
								  @PathParam("name") String name,
								  @PathParam("identifier") String identifier) {

		Security.checkApiCallAllowed(request);
		ArbitraryDataResource resource = new ArbitraryDataResource(name, ResourceIdType.NAME, service, identifier);
		return resource.delete();
	}

	@POST
	@Path("/compute")
	@Operation(
			summary = "Compute nonce for raw, unsigned ARBITRARY transaction",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.TEXT_PLAIN,
							schema = @Schema(
									type = "string",
									description = "raw, unsigned ARBITRARY transaction in base58 encoding",
									example = "raw transaction base58"
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@ApiErrors({ApiError.TRANSACTION_INVALID, ApiError.INVALID_DATA, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	@SecurityRequirement(name = "apiKey")
	public String computeNonce(@HeaderParam(Security.API_KEY_HEADER) String apiKey, String rawBytes58) {
		Security.checkApiCallAllowed(request);

		try (final Repository repository = RepositoryManager.getRepository()) {
			byte[] rawBytes = Base58.decode(rawBytes58);
			// We're expecting unsigned transaction, so append empty signature prior to decoding
			rawBytes = Bytes.concat(rawBytes, new byte[TransactionTransformer.SIGNATURE_LENGTH]);

			TransactionData transactionData = TransactionTransformer.fromBytes(rawBytes);
			if (transactionData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

			if (transactionData.getType() != TransactionType.ARBITRARY)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

			ArbitraryTransaction arbitraryTransaction = (ArbitraryTransaction) Transaction.fromData(repository, transactionData);

			// Quicker validity check first before we compute nonce
			ValidationResult result = arbitraryTransaction.isValid();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			LOGGER.info("Computing nonce...");
			arbitraryTransaction.computeNonce();

			// Re-check, but ignores signature
			result = arbitraryTransaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			// Strip zeroed signature
			transactionData.setSignature(null);

			byte[] bytes = ArbitraryTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}


	@GET
	@Path("/{service}/{name}")
	@Operation(
			summary = "Fetch raw data from file with supplied service, name, and relative path",
			description = "An optional rebuild boolean can be supplied. If true, any existing cached data will be invalidated.",
			responses = {
					@ApiResponse(
							description = "Path to file structure containing requested data",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public HttpServletResponse get(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
								   @PathParam("service") Service service,
								   @PathParam("name") String name,
								   @QueryParam("filepath") String filepath,
								   @QueryParam("rebuild") boolean rebuild,
								   @QueryParam("async") boolean async,
								   @QueryParam("attempts") Integer attempts) {

		// Authentication can be bypassed in the settings, for those running public QDN nodes
		if (!Settings.getInstance().isQDNAuthBypassEnabled()) {
			Security.checkApiCallAllowed(request);
		}

		return this.download(service, name, null, filepath, rebuild, async, attempts);
	}

	@GET
	@Path("/{service}/{name}/{identifier}")
	@Operation(
			summary = "Fetch raw data from file with supplied service, name, identifier, and relative path",
			description = "An optional rebuild boolean can be supplied. If true, any existing cached data will be invalidated.",
			responses = {
					@ApiResponse(
							description = "Path to file structure containing requested data",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public HttpServletResponse get(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
								   @PathParam("service") Service service,
								   @PathParam("name") String name,
								   @PathParam("identifier") String identifier,
								   @QueryParam("filepath") String filepath,
								   @QueryParam("rebuild") boolean rebuild,
								   @QueryParam("async") boolean async,
								   @QueryParam("attempts") Integer attempts) {

		// Authentication can be bypassed in the settings, for those running public QDN nodes
		if (!Settings.getInstance().isQDNAuthBypassEnabled()) {
			Security.checkApiCallAllowed(request);
		}

		return this.download(service, name, identifier, filepath, rebuild, async, attempts);
	}



	// Upload data at supplied path

	@POST
	@Path("/{service}/{name}")
	@Operation(
			summary = "Build raw, unsigned, ARBITRARY transaction, based on a user-supplied path",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.TEXT_PLAIN,
							schema = @Schema(
									type = "string", example = "/Users/user/Documents/MyDirectoryOrFile"
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public String post(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
					   @PathParam("service") String serviceString,
					   @PathParam("name") String name,
					   String path) {
		Security.checkApiCallAllowed(request);

		if (path == null || path.isEmpty()) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Path not supplied");
		}

		return this.upload(Service.valueOf(serviceString), name, null, path, null, null, false);
	}

	@POST
	@Path("/{service}/{name}/{identifier}")
	@Operation(
			summary = "Build raw, unsigned, ARBITRARY transaction, based on a user-supplied path",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.TEXT_PLAIN,
							schema = @Schema(
									type = "string", example = "/Users/user/Documents/MyDirectoryOrFile"
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public String post(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
					   @PathParam("service") String serviceString,
					   @PathParam("name") String name,
					   @PathParam("identifier") String identifier,
					   String path) {
		Security.checkApiCallAllowed(request);

		if (path == null || path.isEmpty()) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Path not supplied");
		}

		return this.upload(Service.valueOf(serviceString), name, identifier, path, null, null, false);
	}



	// Upload base64-encoded data

	@POST
	@Path("/{service}/{name}/base64")
	@Operation(
			summary = "Build raw, unsigned, ARBITRARY transaction, based on user-supplied base64 encoded data",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_OCTET_STREAM,
							schema = @Schema(type = "string", format = "byte")
					)
			),
			responses = {
					@ApiResponse(
							description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public String postBase64EncodedData(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
										@PathParam("service") String serviceString,
										@PathParam("name") String name,
										String base64) {
		Security.checkApiCallAllowed(request);

		if (base64 == null) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Data not supplied");
		}

		return this.upload(Service.valueOf(serviceString), name, null, null, null, base64, false);
	}

	@POST
	@Path("/{service}/{name}/{identifier}/base64")
	@Operation(
			summary = "Build raw, unsigned, ARBITRARY transaction, based on user supplied base64 encoded data",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_OCTET_STREAM,
							schema = @Schema(type = "string", format = "byte")
					)
			),
			responses = {
					@ApiResponse(
							description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public String postBase64EncodedData(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
										@PathParam("service") String serviceString,
										@PathParam("name") String name,
										@PathParam("identifier") String identifier,
										String base64) {
		Security.checkApiCallAllowed(request);

		if (base64 == null) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Data not supplied");
		}

		return this.upload(Service.valueOf(serviceString), name, identifier, null, null, base64, false);
	}


	// Upload zipped data

	@POST
	@Path("/{service}/{name}/zip")
	@Operation(
			summary = "Build raw, unsigned, ARBITRARY transaction, based on user-supplied zip file, encoded as base64",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_OCTET_STREAM,
							schema = @Schema(type = "string", format = "byte")
					)
			),
			responses = {
					@ApiResponse(
							description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public String postZippedData(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
								 @PathParam("service") String serviceString,
								 @PathParam("name") String name,
								 String base64Zip) {
		Security.checkApiCallAllowed(request);

		if (base64Zip == null) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Data not supplied");
		}

		return this.upload(Service.valueOf(serviceString), name, null, null, null, base64Zip, true);
	}

	@POST
	@Path("/{service}/{name}/{identifier}/zip")
	@Operation(
			summary = "Build raw, unsigned, ARBITRARY transaction, based on user supplied zip file, encoded as base64",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_OCTET_STREAM,
							schema = @Schema(type = "string", format = "byte")
					)
			),
			responses = {
					@ApiResponse(
							description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public String postZippedData(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
								 @PathParam("service") String serviceString,
								 @PathParam("name") String name,
								 @PathParam("identifier") String identifier,
								 String base64Zip) {
		Security.checkApiCallAllowed(request);

		if (base64Zip == null) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Data not supplied");
		}

		return this.upload(Service.valueOf(serviceString), name, identifier, null, null, base64Zip, true);
	}



	// Upload plain-text data in string form

	@POST
	@Path("/{service}/{name}/string")
	@Operation(
			summary = "Build raw, unsigned, ARBITRARY transaction, based on a user-supplied string",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.TEXT_PLAIN,
							schema = @Schema(
									type = "string", example = "{\"title\":\"\", \"description\":\"\", \"tags\":[]}"
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public String postString(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
							 @PathParam("service") String serviceString,
							 @PathParam("name") String name,
							 String string) {
		Security.checkApiCallAllowed(request);

		if (string == null || string.isEmpty()) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Data string not supplied");
		}

		return this.upload(Service.valueOf(serviceString), name, null, null, string, null, false);
	}

	@POST
	@Path("/{service}/{name}/{identifier}/string")
	@Operation(
			summary = "Build raw, unsigned, ARBITRARY transaction, based on user supplied string",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.TEXT_PLAIN,
							schema = @Schema(
									type = "string", example = "{\"title\":\"\", \"description\":\"\", \"tags\":[]}"
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "raw, unsigned, ARBITRARY transaction encoded in Base58",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public String postString(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
							 @PathParam("service") String serviceString,
							 @PathParam("name") String name,
							 @PathParam("identifier") String identifier,
							 String string) {
		Security.checkApiCallAllowed(request);

		if (string == null || string.isEmpty()) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Data string not supplied");
		}

		return this.upload(Service.valueOf(serviceString), name, identifier, null, string, null, false);
	}


	// Shared methods

	private String upload(Service service, String name, String identifier, String path, String string, String base64, boolean zipped) {
		// Fetch public key from registered name
		try (final Repository repository = RepositoryManager.getRepository()) {
			NameData nameData = repository.getNameRepository().fromName(name);
			if (nameData == null) {
				String error = String.format("Name not registered: %s", name);
				throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, error);
			}

			if (!Controller.getInstance().isUpToDate()) {
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCKCHAIN_NEEDS_SYNC);
			}

			AccountData accountData = repository.getAccountRepository().getAccount(nameData.getOwner());
			if (accountData == null) {
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.ADDRESS_UNKNOWN);
			}
			byte[] publicKey = accountData.getPublicKey();
			String publicKey58 = Base58.encode(publicKey);

			if (path == null) {
				// See if we have a string instead
				if (string != null) {
					File tempFile = File.createTempFile("qortal-", ".tmp");
					tempFile.deleteOnExit();
					BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toPath().toString()));
					writer.write(string);
					writer.newLine();
					writer.close();
					path = tempFile.toPath().toString();
				}
				// ... or base64 encoded raw data
				else if (base64 != null) {
					File tempFile = File.createTempFile("qortal-", ".tmp");
					tempFile.deleteOnExit();
					Files.write(tempFile.toPath(), Base64.decode(base64));
					path = tempFile.toPath().toString();
				}
				else {
					throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Missing path or data string");
				}
			}

			if (zipped) {
				// Unzip the file
				java.nio.file.Path tempDirectory = Files.createTempDirectory("qortal-");
				tempDirectory.toFile().deleteOnExit();
				LOGGER.info("Unzipping...");
				ZipUtils.unzip(path, tempDirectory.toString());
				path = tempDirectory.toString();

				// Handle directories slightly differently to files
				if (tempDirectory.toFile().isDirectory()) {
					// The actual data will be in a randomly-named subfolder of tempDirectory
					// Remove hidden folders, i.e. starting with "_", as some systems can add them, e.g. "__MACOSX"
					String[] files = tempDirectory.toFile().list((parent, child) -> !child.startsWith("_"));
					if (files.length == 1) { // Single directory or file only
						path = Paths.get(tempDirectory.toString(), files[0]).toString();
					}
				}
			}

			try {
				ArbitraryDataTransactionBuilder transactionBuilder = new ArbitraryDataTransactionBuilder(
						repository, publicKey58, Paths.get(path), name, null, service, identifier
				);

				transactionBuilder.build();
				// Don't compute nonce - this is done by the client (or via POST /arbitrary/compute)
				ArbitraryTransactionData transactionData = transactionBuilder.getArbitraryTransactionData();
				return Base58.encode(ArbitraryTransactionTransformer.toBytes(transactionData));

			} catch (DataException | TransformationException | IllegalStateException e) {
				LOGGER.info("Unable to upload data: {}", e.getMessage());
				throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_DATA, e.getMessage());
			}

		} catch (DataException | IOException e) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.REPOSITORY_ISSUE, e.getMessage());
		}
	}

	private HttpServletResponse download(Service service, String name, String identifier, String filepath, boolean rebuild, boolean async, Integer maxAttempts) {

		ArbitraryDataReader arbitraryDataReader = new ArbitraryDataReader(name, ArbitraryDataFile.ResourceIdType.NAME, service, identifier);
		try {

			int attempts = 0;
			if (maxAttempts == null) {
				maxAttempts = 5;
			}

			// Loop until we have data
			if (async) {
				// Asynchronous
				arbitraryDataReader.loadAsynchronously(false, 1);
			}
			else {
				// Synchronous
				while (!Controller.isStopping()) {
					attempts++;
					if (!arbitraryDataReader.isBuilding()) {
						try {
							arbitraryDataReader.loadSynchronously(rebuild);
							break;
						} catch (MissingDataException e) {
							if (attempts > maxAttempts) {
								// Give up after 5 attempts
								throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Data unavailable. Please try again later.");
							}
						}
					}
					Thread.sleep(3000L);
				}
			}

			java.nio.file.Path outputPath = arbitraryDataReader.getFilePath();
			if (outputPath == null) {
				// Assume the resource doesn't exist
				throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.FILE_NOT_FOUND, "File not found");
			}

			if (filepath == null || filepath.isEmpty()) {
				// No file path supplied - so check if this is a single file resource
				String[] files = ArrayUtils.removeElement(outputPath.toFile().list(), ".qortal");
				if (files.length == 1) {
					// This is a single file resource
					filepath = files[0];
				}
				else {
					throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA,
							"filepath is required for resources containing more than one file");
				}
			}

			// TODO: limit file size that can be read into memory
			java.nio.file.Path path = Paths.get(outputPath.toString(), filepath);
			if (!Files.exists(path)) {
				String message = String.format("No file exists at filepath: %s", filepath);
				throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, message);
			}
			byte[] data = Files.readAllBytes(path);
			response.setContentType(context.getMimeType(path.toString()));
			response.setContentLength(data.length);
			response.getOutputStream().write(data);

			return response;
		} catch (Exception e) {
			LOGGER.debug(String.format("Unable to load %s %s: %s", service, name, e.getMessage()));
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.FILE_NOT_FOUND, e.getMessage());
		}
	}


	private ArbitraryResourceStatus getStatus(Service service, String name, String identifier, Boolean build) {

		// If "build=true" has been specified in the query string, build the resource before returning its status
		if (build != null && build == true) {
			ArbitraryDataReader reader = new ArbitraryDataReader(name, ArbitraryDataFile.ResourceIdType.NAME, service, null);
			try {
				if (!reader.isBuilding()) {
					reader.loadSynchronously(false);
				}
			} catch (Exception e) {
				// No need to handle exception, as it will be reflected in the status
			}
		}

		ArbitraryDataResource resource = new ArbitraryDataResource(name, ResourceIdType.NAME, service, identifier);
		return resource.getStatus(false);
	}

	private List<ArbitraryResourceInfo> addStatusToResources(List<ArbitraryResourceInfo> resources) {
		// Determine and add the status of each resource
		List<ArbitraryResourceInfo> updatedResources = new ArrayList<>();
		for (ArbitraryResourceInfo resourceInfo : resources) {
			ArbitraryDataResource resource = new ArbitraryDataResource(resourceInfo.name, ResourceIdType.NAME,
					resourceInfo.service, resourceInfo.identifier);
			ArbitraryResourceStatus status = resource.getStatus(true);
			if (status != null) {
				resourceInfo.status = status;
			}
			updatedResources.add(resourceInfo);
		}
		return updatedResources;
	}
}
