package org.qortal.api.resource;

import com.google.common.hash.HashCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.api.ApiError;
import org.qortal.api.ApiErrors;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.crypto.Crypto;
import org.qortal.settings.Settings;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.transform.Transformer;
import org.qortal.transform.transaction.TransactionTransformer;
import org.qortal.transform.transaction.TransactionTransformer.Transformation;
import org.qortal.utils.Base58;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Path("/utils")
@Tag(name = "Utilities")
public class UtilsResource {

	@Context
	HttpServletRequest request;

	@POST
	@Path("/frombase64")
	@Operation(
		summary = "Convert base64 data to hex",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "hex string",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION, ApiError.INVALID_DATA})
	public String fromBase64(String base64) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		try {
			return HashCode.fromBytes(Base64.getDecoder().decode(base64.trim())).toString();
		} catch (IllegalArgumentException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);
		}
	}

	@POST
	@Path("/frombase58")
	@Operation(
		summary = "Convert base58 data to hex",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "hex string",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION, ApiError.INVALID_DATA})
	public String base64from58(String base58) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		try {
			return HashCode.fromBytes(Base58.decode(base58.trim())).toString();
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);
		}
	}

	@GET
	@Path("/tobase64/{hex}")
	@Operation(
		summary = "Convert hex to base64",
		responses = {
			@ApiResponse(
				description = "base64",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION})
	public String toBase64(@PathParam("hex") String hex) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		return Base64.getEncoder().encodeToString(HashCode.fromString(hex).asBytes());
	}

	@GET
	@Path("/tobase58/{hex}")
	@Operation(
		summary = "Convert hex to base58",
		responses = {
			@ApiResponse(
				description = "base58",
				content = @Content(
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION})
	public String toBase58(@PathParam("hex") String hex) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		return Base58.encode(HashCode.fromString(hex).asBytes());
	}

	@GET
	@Path("/random")
	@Operation(
		summary = "Generate random data",
		description = "Optionally pass data length, defaults to 32 bytes.",
		responses = {
			@ApiResponse(
				description = "base58 data",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION})
	public String random(@QueryParam("length") Integer length) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		if (length == null)
			length = 32;

		byte[] random = new byte[length];
		new SecureRandom().nextBytes(random);
		return Base58.encode(random);
	}

	@POST
	@Path("/privatekey")
	@Operation(
		summary = "Calculate private key from supplied 16-byte entropy",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "private key in base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION, ApiError.INVALID_DATA})
	public String privateKey(String entropy58) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		byte[] entropy;
		try {
			entropy = Base58.decode(entropy58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);
		}

		if (entropy.length != 16)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

		byte[] privateKey = Crypto.digest(entropy);

		return Base58.encode(privateKey);
	}

	@POST
	@Path("/publickey")
	@Operation(
		summary = "Calculate public key from supplied 32-byte private key",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string"
				)
			)
		),
		responses = {
			@ApiResponse(
				description = "public key in base58",
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "string"
					)
				)
			)
		}
	)
	@ApiErrors({ApiError.NON_PRODUCTION, ApiError.INVALID_DATA, ApiError.INVALID_PRIVATE_KEY})
	public String publicKey(String privateKey58) {
		if (Settings.getInstance().isApiRestricted())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

		byte[] privateKey;
		try {
			privateKey = Base58.decode(privateKey58);
		} catch (NumberFormatException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);
		}

		if (privateKey.length != Transformer.PRIVATE_KEY_LENGTH)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);

		try {
			byte[] publicKey = new PrivateKeyAccount(null, privateKey).getPublicKey();

			return Base58.encode(publicKey);
		} catch (IllegalArgumentException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_PRIVATE_KEY, e);
		}

	}

	@GET
	@Path("/timestamp")
	@Operation(
		summary = "Returns current timestamp as milliseconds from unix epoch",
		responses = {
			@ApiResponse(
				content = @Content(
					mediaType = MediaType.TEXT_PLAIN,
					schema = @Schema(
						type = "number"
					)
				)
			)
		}
	)
	public long getTimestamp() {
		return System.currentTimeMillis();
	}

	@GET
	@Path("/layout/{txtype}")
	@Operation(
		summary = "Returns raw transaction layout based on transaction type",
		description = "Components are returned in sequential order used to build transaction. Components marked with * are part of a repeatable group. For example, multiple payments within a MULTI-PAYMENT transaction.",
		responses = {
			@ApiResponse(
				content = @Content(
					mediaType = MediaType.APPLICATION_JSON,
					array = @ArraySchema(schema = @Schema( implementation = Transformation.class ))
				)
			)
		}
	)
	public List<Transformation> getLayout(@PathParam("txtype") TransactionType txType) {
		return TransactionTransformer.getLayoutByTxType(txType);
	}

}