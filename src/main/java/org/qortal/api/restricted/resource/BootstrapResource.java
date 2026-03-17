package org.qortal.api.restricted.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.api.ApiError;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.Security;
import org.qortal.repository.Bootstrap;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;


@Path("/bootstrap")
@Tag(name = "Bootstrap")
public class BootstrapResource {

	private static final Logger LOGGER = LogManager.getLogger(BootstrapResource.class);

	@Context
	HttpServletRequest request;

	@POST
	@Path("/create")
	@Operation(
		summary = "Create bootstrap",
		description = "Builds a bootstrap file for distribution",
		responses = {
			@ApiResponse(
				description = "path to file on success, an exception on failure",
				content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string"))
			)
		}
	)
	@SecurityRequirement(name = "apiKey")
	public String createBootstrap(@HeaderParam(Security.API_KEY_HEADER) String apiKey) {
		Security.checkApiCallAllowed(request);

		try (final Repository repository = RepositoryManager.getRepository()) {

			Bootstrap bootstrap = new Bootstrap(repository);
			try {
				bootstrap.checkRepositoryState();
			} catch (DataException e) {
				LOGGER.info("Not ready to create bootstrap: {}", e.getMessage());
				throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.REPOSITORY_ISSUE, e.getMessage());
			}
			bootstrap.validateBlockchain();
			return bootstrap.create();

		} catch (Exception e) {
			LOGGER.info("Unable to create bootstrap", e);
			throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.REPOSITORY_ISSUE, e.getMessage());
		}
	}

	@GET
	@Path("/validate")
	@Operation(
			summary = "Validate blockchain",
			description = "Useful to check database integrity prior to creating or after installing a bootstrap. " +
					"This process is intensive and can take over an hour to run.",
			responses = {
					@ApiResponse(
							description = "true if valid, false if invalid",
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "boolean"))
					)
			}
	)
	@SecurityRequirement(name = "apiKey")
	public boolean validateBootstrap(@HeaderParam(Security.API_KEY_HEADER) String apiKey) {
		Security.checkApiCallAllowed(request);

		try (final Repository repository = RepositoryManager.getRepository()) {

			Bootstrap bootstrap = new Bootstrap(repository);
			return bootstrap.validateCompleteBlockchain();

		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE);
		}
	}
}
