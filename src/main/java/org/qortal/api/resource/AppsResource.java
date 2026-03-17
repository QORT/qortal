package org.qortal.api.resource;

import com.google.common.io.Resources;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.qortal.api.ApiError;
import org.qortal.api.ApiExceptionFactory;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;


@Path("/apps")
@Tag(name = "Apps")
public class AppsResource {

    @Context HttpServletRequest request;
    @Context HttpServletResponse response;
    @Context ServletContext context;

    @GET
    @Path("/q-apps.js")
    @Hidden // For internal Q-App API use only
    @Operation(
            summary = "Javascript interface for Q-Apps",
            responses = {
                    @ApiResponse(
                            description = "javascript",
                            content = @Content(
                                    mediaType = MediaType.TEXT_PLAIN,
                                    schema = @Schema(
                                            type = "string"
                                    )
                            )
                    )
            }
    )
    public String getQAppsJs() {
        URL url = Resources.getResource("q-apps/q-apps.js");
        try {
            return Resources.toString(url, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.FILE_NOT_FOUND);
        }
    }

    @GET
    @Path("/q-apps-gateway.js")
    @Hidden // For internal Q-App API use only
    @Operation(
            summary = "Gateway-specific interface for Q-Apps",
            responses = {
                    @ApiResponse(
                            description = "javascript",
                            content = @Content(
                                    mediaType = MediaType.TEXT_PLAIN,
                                    schema = @Schema(
                                            type = "string"
                                    )
                            )
                    )
            }
    )
    public String getQAppsGatewayJs() {
        URL url = Resources.getResource("q-apps/q-apps-gateway.js");
        try {
            return Resources.toString(url, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.FILE_NOT_FOUND);
        }
    }

}
