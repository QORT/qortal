package org.qortal.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ciyam.at.MachineState;
import org.qortal.api.ApiError;
import org.qortal.api.ApiErrors;
import org.qortal.api.ApiException;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.model.AtCreationRequest;
import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.DeployAtTransactionTransformer;
import org.qortal.utils.Base58;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;


@Path("/at")
@Tag(name = "Automated Transactions")
public class AtResource {
	private static final Logger logger = LoggerFactory.getLogger(AtResource.class);

	@Context
	HttpServletRequest request;

	@GET
	@Path("/byfunction/{codehash}")
	@Operation(
		summary = "Find automated transactions with matching functionality (code hash)",
		responses = {
			@ApiResponse(
				description = "automated transactions",
				content = @Content(
					array = @ArraySchema(
						schema = @Schema(
							implementation = ATData.class
						)
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_CRITERIA, ApiError.REPOSITORY_ISSUE
	})
	public List<ATData> getByFunctionality(
			@PathParam("codehash")
			String codeHash58,
			@Parameter(description = "whether to include ATs that can run, or not, or both (if omitted)")
			@QueryParam("isExecutable")
			Boolean isExecutable,
			@Parameter( ref = "limit") @QueryParam("limit") Integer limit,
			@Parameter( ref = "offset" ) @QueryParam("offset") Integer offset,
			@Parameter( ref = "reverse" ) @QueryParam("reverse") Boolean reverse) {
		// Decode codeHash
		byte[] codeHash;
		try {
			codeHash = Base58.decode(codeHash58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA, e);
		}

		// codeHash must be present and have correct length
		if (codeHash == null || codeHash.length != 32)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		// Impose a limit on 'limit'
		if (limit != null && limit > 100)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getATRepository().getATsByFunctionality(codeHash, isExecutable, limit, offset, reverse);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/{ataddress}")
	@Operation(
		summary = "Fetch info associated with AT address",
		responses = {
			@ApiResponse(
				description = "automated transaction",
				content = @Content(
					schema = @Schema(implementation = ATData.class)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public ATData getByAddress(@PathParam("ataddress") String atAddress) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getATRepository().fromATAddress(atAddress);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/{ataddress}/data")
	@Operation(
		summary = "Fetch data segment associated with AT address",
		responses = {
			@ApiResponse(
				description = "automated transaction",
				content = @Content(
					schema = @Schema(implementation = byte[].class)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.REPOSITORY_ISSUE
	})
	public byte[] getDataByAddress(@PathParam("ataddress") String atAddress) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);
			byte[] stateData = atStateData.getStateData();

			byte[] dataBytes = MachineState.extractDataBytes(stateData);

			return dataBytes;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/create")
	@Operation(
			summary = "Create base58-encoded AT creation bytes from the provided parameters",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON,
							schema = @Schema(
									implementation = AtCreationRequest.class
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "AT creation bytes suitable for use in a DEPLOY_AT transaction",
							content = @Content(
									mediaType = MediaType.TEXT_PLAIN,
									schema = @Schema(
											type = "string"
									)
							)
					)
			}
	)
	public String create(AtCreationRequest atCreationRequest) {
		if (atCreationRequest.getCiyamAtVersion() < 2) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "ciyamAtVersion must be at least 2");
		}
		if (atCreationRequest.getCodeBytes() == null) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Valid codeBytesBase64 must be supplied");
		}
		if (atCreationRequest.getDataBytes() == null) {
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.INVALID_CRITERIA, "Valid dataBytesBase64 must be supplied");
		}

		byte[] creationBytes = MachineState.toCreationBytes(
				atCreationRequest.getCiyamAtVersion(),
				atCreationRequest.getCodeBytes(),
				atCreationRequest.getDataBytes(),
				atCreationRequest.getNumCallStackPages(),
				atCreationRequest.getNumUserStackPages(),
				atCreationRequest.getMinActivationAmount()
		);
		return Base58.encode(creationBytes);
	}
	@POST
	@Operation(
		summary = "Build raw, unsigned, DEPLOY_AT transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = DeployAtTransactionData.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, unsigned, DEPLOY_AT transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION, ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
	public String createDeployAt(DeployAtTransactionData transactionData) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			ValidationResult result = transaction.isValidUnconfirmed();
			if (result != ValidationResult.OK)
				throw TransactionsResource.createTransactionInvalidException(request, result);

			byte[] bytes = DeployAtTransactionTransformer.toBytes(transactionData);
			return Base58.encode(bytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

}