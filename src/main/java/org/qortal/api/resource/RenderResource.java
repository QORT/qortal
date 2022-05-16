package org.qortal.api.resource;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.nio.file.Paths;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.api.ApiError;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.Security;
import org.qortal.arbitrary.misc.Service;
import org.qortal.arbitrary.*;
import org.qortal.arbitrary.exception.MissingDataException;
import org.qortal.controller.arbitrary.ArbitraryDataRenderManager;
import org.qortal.data.transaction.ArbitraryTransactionData.*;
import org.qortal.repository.DataException;
import org.qortal.settings.Settings;
import org.qortal.arbitrary.ArbitraryDataFile.*;
import org.qortal.utils.Base58;


@Path("/render")
@Tag(name = "Render")
public class RenderResource {

    private static final Logger LOGGER = LogManager.getLogger(RenderResource.class);

    @Context HttpServletRequest request;
    @Context HttpServletResponse response;
    @Context ServletContext context;

    @POST
    @Path("/preview")
    @Operation(
            summary = "Generate preview URL based on a user-supplied path and service",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.TEXT_PLAIN,
                            schema = @Schema(
                                    type = "string", example = "/Users/user/Documents/MyStaticWebsite"
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            description = "a temporary URL to preview the website",
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
    public String preview(@HeaderParam(Security.API_KEY_HEADER) String apiKey, String directoryPath) {
        Security.checkApiCallAllowed(request);
        Method method = Method.PUT;
        Compression compression = Compression.ZIP;

        ArbitraryDataWriter arbitraryDataWriter = new ArbitraryDataWriter(Paths.get(directoryPath), null, Service.WEBSITE, null, method, compression);
        try {
            arbitraryDataWriter.save();
        } catch (IOException | DataException | InterruptedException | MissingDataException e) {
            LOGGER.info("Unable to create arbitrary data file: {}", e.getMessage());
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE);
        } catch (RuntimeException e) {
            LOGGER.info("Unable to create arbitrary data file: {}", e.getMessage());
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_DATA);
        }

        ArbitraryDataFile arbitraryDataFile = arbitraryDataWriter.getArbitraryDataFile();
        if (arbitraryDataFile != null) {
            String digest58 = arbitraryDataFile.digest58();
            if (digest58 != null) {
                return "http://localhost:12393/render/hash/" + digest58 + "?secret=" + Base58.encode(arbitraryDataFile.getSecret());
            }
        }
        return "Unable to generate preview URL";
    }

    @POST
    @Path("/authorize/{resourceId}")
    @SecurityRequirement(name = "apiKey")
    public boolean authorizeResource(@HeaderParam(Security.API_KEY_HEADER) String apiKey, @PathParam("resourceId") String resourceId) {
        Security.checkApiCallAllowed(request);
        Security.disallowLoopbackRequestsIfAuthBypassEnabled(request);
        ArbitraryDataResource resource = new ArbitraryDataResource(resourceId, null, null, null);
        ArbitraryDataRenderManager.getInstance().addToAuthorizedResources(resource);
        return true;
    }

    @POST
    @Path("authorize/{service}/{resourceId}")
    @SecurityRequirement(name = "apiKey")
    public boolean authorizeResource(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
                                     @PathParam("service") Service service,
                                     @PathParam("resourceId") String resourceId) {
        Security.checkApiCallAllowed(request);
        Security.disallowLoopbackRequestsIfAuthBypassEnabled(request);
        ArbitraryDataResource resource = new ArbitraryDataResource(resourceId, null, service, null);
        ArbitraryDataRenderManager.getInstance().addToAuthorizedResources(resource);
        return true;
    }

    @POST
    @Path("authorize/{service}/{resourceId}/{identifier}")
    @SecurityRequirement(name = "apiKey")
    public boolean authorizeResource(@HeaderParam(Security.API_KEY_HEADER) String apiKey,
                                     @PathParam("service") Service service,
                                     @PathParam("resourceId") String resourceId,
                                     @PathParam("identifier") String identifier) {
        Security.checkApiCallAllowed(request);
        Security.disallowLoopbackRequestsIfAuthBypassEnabled(request);
        ArbitraryDataResource resource = new ArbitraryDataResource(resourceId, null, service, identifier);
        ArbitraryDataRenderManager.getInstance().addToAuthorizedResources(resource);
        return true;
    }

    @GET
    @Path("/signature/{signature}")
    @SecurityRequirement(name = "apiKey")
    public HttpServletResponse getIndexBySignature(@PathParam("signature") String signature) {
        Security.requirePriorAuthorization(request, signature, Service.WEBSITE, null);
        return this.get(signature, ResourceIdType.SIGNATURE, null, "/", null, "/render/signature", true, true);
    }

    @GET
    @Path("/signature/{signature}/{path:.*}")
    @SecurityRequirement(name = "apiKey")
    public HttpServletResponse getPathBySignature(@PathParam("signature") String signature, @PathParam("path") String inPath) {
        Security.requirePriorAuthorization(request, signature, Service.WEBSITE, null);
        return this.get(signature, ResourceIdType.SIGNATURE, null, inPath,null, "/render/signature", true, true);
    }

    @GET
    @Path("/hash/{hash}")
    @SecurityRequirement(name = "apiKey")
    public HttpServletResponse getIndexByHash(@PathParam("hash") String hash58, @QueryParam("secret") String secret58) {
        Security.requirePriorAuthorization(request, hash58, Service.WEBSITE, null);
        return this.get(hash58, ResourceIdType.FILE_HASH, Service.WEBSITE, "/", secret58, "/render/hash", true, false);
    }

    @GET
    @Path("/hash/{hash}/{path:.*}")
    @SecurityRequirement(name = "apiKey")
    public HttpServletResponse getPathByHash(@PathParam("hash") String hash58, @PathParam("path") String inPath,
                                             @QueryParam("secret") String secret58) {
        Security.requirePriorAuthorization(request, hash58, Service.WEBSITE, null);
        return this.get(hash58, ResourceIdType.FILE_HASH, Service.WEBSITE, inPath, secret58, "/render/hash", true, false);
    }

    @GET
    @Path("{service}/{name}/{path:.*}")
    @SecurityRequirement(name = "apiKey")
    public HttpServletResponse getPathByName(@PathParam("service") Service service,
                                             @PathParam("name") String name,
                                             @PathParam("path") String inPath) {
        Security.requirePriorAuthorization(request, name, service, null);
        String prefix = String.format("/render/%s", service);
        return this.get(name, ResourceIdType.NAME, service, inPath, null, prefix, true, true);
    }

    @GET
    @Path("{service}/{name}")
    @SecurityRequirement(name = "apiKey")
    public HttpServletResponse getIndexByName(@PathParam("service") Service service,
                                              @PathParam("name") String name) {
        Security.requirePriorAuthorization(request, name, service, null);
        String prefix = String.format("/render/%s", service);
        return this.get(name, ResourceIdType.NAME, service, "/", null, prefix, true, true);
    }



    private HttpServletResponse get(String resourceId, ResourceIdType resourceIdType, Service service, String inPath,
                                    String secret58, String prefix, boolean usePrefix, boolean async) {

        ArbitraryDataRenderer renderer = new ArbitraryDataRenderer(resourceId, resourceIdType, service, inPath,
                secret58, prefix, usePrefix, async, request, response, context);
        return renderer.render();
    }

}
