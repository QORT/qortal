package org.qortal.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.api.ApiError;
import org.qortal.api.ApiErrors;
import org.qortal.api.ApiException;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.model.SimpleTransactionSignRequest;
import org.qortal.controller.Controller;
import org.qortal.data.transaction.TransactionData;
import org.qortal.globalization.Translator;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.transaction.Transaction;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transaction.Transaction.ValidationResult;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.utils.Base58;

import com.google.common.primitives.Bytes;

@Path("/transactions")
@Tag(name = "Transactions")
public class TransactionsResource {

	@Context
	HttpServletRequest request;

	@GET
	@Path("/signature/{signature}")
	@Operation(
		summary = "Fetch transaction using transaction signature",
		description = "Returns transaction",
		responses = {
			@ApiResponse(
				description = "a transaction",
				content = @Content(
					schema = @Schema(
						implementation = TransactionData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.TRANSACTION_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public TransactionData getTransactionBySignature(@PathParam("signature") String signature58) {
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
			if (transactionData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSACTION_UNKNOWN);

			return transactionData;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/signature/{signature}/raw")
	@Operation(
		summary = "Fetch raw, base58-encoded, transaction using transaction signature",
		description = "Returns transaction",
		responses = {
			@ApiResponse(
				description = "raw transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.TRANSACTION_UNKNOWN, ApiError.REPOSITORY_ISSUE, ApiError.TRANSFORMATION_ERROR
	})
	public String getRawTransactionBySignature(@PathParam("signature") String signature58) {
		byte[] signature;
		try {
			signature = Base58.decode(signature58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
			if (transactionData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSACTION_UNKNOWN);

			byte[] transactionBytes = TransactionTransformer.toBytes(transactionData);

			return Base58.encode(transactionBytes);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		}
	}

	@GET
	@Path("/reference/{reference}")
	@Operation(
		summary = "Fetch transaction using transaction reference",
		description = "Returns transaction",
		responses = {
			@ApiResponse(
				description = "a transaction",
				content = @Content(
					schema = @Schema(
						implementation = TransactionData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_REFERENCE, ApiError.TRANSACTION_UNKNOWN, ApiError.REPOSITORY_ISSUE
	})
	public TransactionData getTransactionByReference(@PathParam("reference") String reference58) {
		byte[] reference;
		try {
			reference = Base58.decode(reference58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_REFERENCE, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			TransactionData transactionData = repository.getTransactionRepository().fromReference(reference);
			if (transactionData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSACTION_UNKNOWN);

			return transactionData;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/block/{signature}")
	@Operation(
		summary = "Fetch transactions using block signature",
		description = "Returns list of transactions",
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
			if (repository.getBlockRepository().getHeightFromSignature(signature) == 0)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCK_UNKNOWN);

			return repository.getBlockRepository().getTransactionsFromSignature(signature, limit, offset, reverse);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/unconfirmed")
	@Operation(
		summary = "List unconfirmed transactions",
		description = "Returns transactions",
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
		ApiError.REPOSITORY_ISSUE
	})
	public List<TransactionData> getUnconfirmedTransactions(@Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getTransactionRepository().getUnconfirmedTransactions(limit, offset, reverse);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/pending")
	@Operation(
		summary = "List transactions pending group approval",
		description = "Returns transactions that are pending group-admin approval",
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
		ApiError.REPOSITORY_ISSUE
	})
	public List<TransactionData> getPendingTransactions(@QueryParam("txGroupId") Integer txGroupId, @Parameter(
		ref = "limit"
	) @QueryParam("limit") Integer limit, @Parameter(
		ref = "offset"
	) @QueryParam("offset") Integer offset, @Parameter(
		ref = "reverse"
	) @QueryParam("reverse") Boolean reverse) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			return repository.getTransactionRepository().getApprovalPendingTransactions(txGroupId, limit, offset, reverse);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	public enum ConfirmationStatus {
		CONFIRMED,
		UNCONFIRMED,
		BOTH;
	}

	@GET
	@Path("/search")
	@Operation(
		summary = "Find matching transactions",
		description = "Returns transactions that match criteria. At least either txType or address or limit <= 20 must be provided. Block height ranges allowed when searching CONFIRMED transactions ONLY.",
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
			@QueryParam("txType") List<TransactionType> txTypes, @QueryParam("address") String address, @Parameter(
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
		if ((txTypes == null || txTypes.isEmpty()) && (address == null || address.isEmpty()) && (limit == null || limit > 20))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		// You can't ask for unconfirmed and impose a block height range
		if (confirmationStatus != ConfirmationStatus.CONFIRMED && (startBlock != null || blockLimit != null))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(startBlock, blockLimit, txGroupId,
					txTypes, null, address, confirmationStatus, limit, offset, reverse);

			// Expand signatures to transactions
			List<TransactionData> transactions = new ArrayList<>(signatures.size());
			for (byte[] signature : signatures)
				transactions.add(repository.getTransactionRepository().fromSignature(signature));

			return transactions;
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@GET
	@Path("/creator/{publickey}")
	@Operation(
		summary = "Find matching transactions created by account with given public key",
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
	public List<TransactionData> findCreatorsTransactions(@PathParam("publickey") String publicKey58,
			@Parameter(
				description = "whether to include confirmed, unconfirmed or both",
				required = true
			) @QueryParam("confirmationStatus") ConfirmationStatus confirmationStatus, @Parameter(
				ref = "limit"
			) @QueryParam("limit") Integer limit, @Parameter(
				ref = "offset"
			) @QueryParam("offset") Integer offset, @Parameter(
				ref = "reverse"
			) @QueryParam("reverse") Boolean reverse) {
		// Decode public key
		byte[] publicKey;
		try {
			publicKey = Base58.decode(publicKey58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_PUBLIC_KEY, e);
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null,
					publicKey, confirmationStatus, limit, offset, reverse);

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
	@Path("/sign")
	@Operation(
		summary = "Sign a raw, unsigned transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = SimpleTransactionSignRequest.class
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "raw, signed transaction encoded in Base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.NON_PRODUCTION, ApiError.INVALID_PRIVATE_KEY, ApiError.TRANSFORMATION_ERROR
	})
	public String signTransaction(SimpleTransactionSignRequest signRequest) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		if (signRequest.transactionBytes.length == 0)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.JSON);

		try {
			// Append null signature on the end before transformation
			byte[] rawBytes = Bytes.concat(signRequest.transactionBytes, new byte[TransactionTransformer.SIGNATURE_LENGTH]);

			TransactionData transactionData = TransactionTransformer.fromBytes(rawBytes);
			if (transactionData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

			PrivateKeyAccount signer = new PrivateKeyAccount(null, signRequest.privateKey);

			Transaction transaction = Transaction.fromData(null, transactionData);
			transaction.sign(signer);

			byte[] signedBytes = TransactionTransformer.toBytes(transactionData);

			return Base58.encode(signedBytes);
		} catch (IllegalArgumentException e) {
			// Invalid private key
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_PRIVATE_KEY);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		}
	}

	@POST
	@Path("/process")
	@Operation(
		summary = "Submit raw, signed transaction for processing and adding to blockchain",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string",
					description = "raw, signed transaction in base58 encoding",
					example = "raw transaction base58"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "true if accepted, false otherwise",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.BLOCKCHAIN_NEEDS_SYNC, ApiError.INVALID_SIGNATURE, ApiError.INVALID_DATA, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE
	})
	public String processTransaction(String rawBytes58) {
		if (!Controller.getInstance().isUpToDate())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.BLOCKCHAIN_NEEDS_SYNC);

		byte[] rawBytes = Base58.decode(rawBytes58);

		TransactionData transactionData;
		try {
			transactionData = TransactionTransformer.fromBytes(rawBytes);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		}

		if (transactionData == null)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

		try (final Repository repository = RepositoryManager.getRepository()) {
			Transaction transaction = Transaction.fromData(repository, transactionData);

			if (!transaction.isSignatureValid())
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE);

			ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
			if (!blockchainLock.tryLock(30, TimeUnit.SECONDS))
				throw createTransactionInvalidException(request, ValidationResult.NO_BLOCKCHAIN_LOCK);

			try {
				ValidationResult result = transaction.importAsUnconfirmed();
				if (result != ValidationResult.OK)
					throw createTransactionInvalidException(request, result);
			} finally {
				blockchainLock.unlock();
			}

			return "true";
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA, e);
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		} catch (InterruptedException e) {
			throw createTransactionInvalidException(request, ValidationResult.NO_BLOCKCHAIN_LOCK);
		}
	}

	@POST
	@Path("/decode")
	@Operation(
		summary = "Decode a raw, signed/unsigned transaction",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string",
					description = "raw, unsigned/signed transaction in base58 encoding",
					example = "raw transaction base58"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "a transaction",
				content = @Content(
					schema = @Schema(
						implementation = TransactionData.class
					)
				)
			)
		}
	)
	@ApiErrors({
		ApiError.INVALID_SIGNATURE, ApiError.INVALID_DATA, ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE
	})
	public TransactionData decodeTransaction(String rawBytes58, @QueryParam("ignoreValidityChecks") boolean ignoreValidityChecks) {
		try (final Repository repository = RepositoryManager.getRepository()) {
			byte[] rawBytes = Base58.decode(rawBytes58);
			boolean hasSignature = true;

			TransactionData transactionData;
			try {
				transactionData = TransactionTransformer.fromBytes(rawBytes);
			} catch (TransformationException e) {
				// Maybe we're missing a signature, so append one and try one more time
				rawBytes = Bytes.concat(rawBytes, new byte[TransactionTransformer.SIGNATURE_LENGTH]);
				hasSignature = false;
				transactionData = TransactionTransformer.fromBytes(rawBytes);
			}

			if (transactionData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

			Transaction transaction = Transaction.fromData(repository, transactionData);

			if (!ignoreValidityChecks) {
				if (hasSignature && !transaction.isSignatureValid())
					throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_SIGNATURE);

				ValidationResult result = transaction.isValid();
				if (result != ValidationResult.OK)
					throw createTransactionInvalidException(request, result);
			}

			if (!hasSignature)
				transactionData.setSignature(null);

			return transactionData;
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA, e);
		} catch (TransformationException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
		} catch (ApiException e) {
			throw e;
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	public static ApiException createTransactionInvalidException(HttpServletRequest request, ValidationResult result) {
		String translatedResult = Translator.INSTANCE.translate("TransactionValidity", request.getLocale().getLanguage(), result.name());
		return ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSACTION_INVALID, null, translatedResult, result.name());
	}

}
