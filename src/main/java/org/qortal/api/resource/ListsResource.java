package org.qortal.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.qortal.api.*;
import org.qortal.api.model.ListRequest;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.AccountData;
import org.qortal.list.ResourceListManager;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;


@Path("/lists")
@Tag(name = "Lists")
public class ListsResource {

	@Context
	HttpServletRequest request;


	/* Address blacklist */

	@POST
	@Path("/blacklist/addresses")
	@Operation(
			summary = "Add one or more QORT addresses to the local blacklist",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON,
							schema = @Schema(
									implementation = ListRequest.class
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "Returns true if all addresses were processed, false if any couldn't be " +
									"processed, or an exception on failure. If false or an exception is returned, " +
									"the list will not be updated, and the request will need to be re-issued.",
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "boolean"))
					)
			}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.ADDRESS_UNKNOWN, ApiError.REPOSITORY_ISSUE})
	public String addAddressesToBlacklist(ListRequest listRequest) {
		Security.checkApiCallAllowed(request);

		if (listRequest == null || listRequest.items == null) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
		}

		int successCount = 0;
		int errorCount = 0;

		try (final Repository repository = RepositoryManager.getRepository()) {

			for (String address : listRequest.items) {

				if (!Crypto.isValidAddress(address)) {
					errorCount++;
					continue;
				}

				AccountData accountData = repository.getAccountRepository().getAccount(address);
				// Not found?
				if (accountData == null) {
					errorCount++;
					continue;
				}

				// Valid address, so go ahead and blacklist it
				boolean success = ResourceListManager.getInstance().addToList("blacklist", "addresses", address, false);
				if (success) {
					successCount++;
				}
				else {
					errorCount++;
				}
			}
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}

		if (successCount > 0 && errorCount == 0) {
			// All were successful, so save the blacklist
			ResourceListManager.getInstance().saveList("blacklist", "addresses");
			return "true";
		}
		else {
			// Something went wrong, so revert
			ResourceListManager.getInstance().revertList("blacklist", "addresses");
			return "false";
		}
	}

	@DELETE
	@Path("/blacklist/addresses")
	@Operation(
			summary = "Remove one or more QORT addresses from the local blacklist",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON,
							schema = @Schema(
									implementation = ListRequest.class
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "Returns true if all addresses were processed, false if any couldn't be " +
									"processed, or an exception on failure. If false or an exception is returned, " +
									"the list will not be updated, and the request will need to be re-issued.",
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "boolean"))
					)
			}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.ADDRESS_UNKNOWN, ApiError.REPOSITORY_ISSUE})
	public String removeAddressesFromBlacklist(ListRequest listRequest) {
		Security.checkApiCallAllowed(request);

		if (listRequest == null || listRequest.items == null) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
		}

		int successCount = 0;
		int errorCount = 0;

		try (final Repository repository = RepositoryManager.getRepository()) {

			for (String address : listRequest.items) {

				if (!Crypto.isValidAddress(address)) {
					errorCount++;
					continue;
				}

				AccountData accountData = repository.getAccountRepository().getAccount(address);
				// Not found?
				if (accountData == null) {
					errorCount++;
					continue;
				}

				// Valid address, so go ahead and blacklist it
				// Don't save as we will do this at the end of the process
				boolean success = ResourceListManager.getInstance().removeFromList("blacklist", "addresses", address, false);
				if (success) {
					successCount++;
				}
				else {
					errorCount++;
				}
			}
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}

		if (successCount > 0 && errorCount == 0) {
			// All were successful, so save the blacklist
			ResourceListManager.getInstance().saveList("blacklist", "addresses");
			return "true";
		}
		else {
			// Something went wrong, so revert
			ResourceListManager.getInstance().revertList("blacklist", "addresses");
			return "false";
		}
	}

	@GET
	@Path("/blacklist/addresses")
	@Operation(
			summary = "Fetch the list of blacklisted addresses",
			responses = {
					@ApiResponse(
							description = "A JSON array of addresses",
							content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = String.class)))
					)
			}
	)
	public String getAddressBlacklist() {
		Security.checkApiCallAllowed(request);
		return ResourceListManager.getInstance().getJSONStringForList("blacklist", "addresses");
	}

}
