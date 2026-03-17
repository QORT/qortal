package org.qortal.api.gateway.resource;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.qortal.api.ApiError;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.Security;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.arbitrary.ArbitraryDataFile.ResourceIdType;
import org.qortal.arbitrary.ArbitraryDataReader;
import org.qortal.arbitrary.ArbitraryDataRenderer;
import org.qortal.arbitrary.ArbitraryDataResource;
import org.qortal.arbitrary.misc.Service;
import org.qortal.data.arbitrary.ArbitraryResourceStatus;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


@Path("/")
@Tag(name = "Gateway")
public class GatewayResource {

    @Context HttpServletRequest request;
    @Context HttpServletResponse response;
    @Context ServletContext context;

    private ArbitraryResourceStatus getStatus(Service service, String name, String identifier, Boolean build) {

        // If "build=true" has been specified in the query string, build the resource before returning its status
        if (build != null && build) {
            try {
                ArbitraryDataReader reader = new ArbitraryDataReader(name, ArbitraryDataFile.ResourceIdType.NAME, service, null);
                if (!reader.isBuilding()) {
                    reader.loadSynchronously(false);
                }
            } catch (Exception e) {
                // No need to handle exception, as it will be reflected in the status
            }
        }

        try (final Repository repository = RepositoryManager.getRepository()) {
            ArbitraryDataResource resource = new ArbitraryDataResource(name, ResourceIdType.NAME, service, identifier);
            return resource.getStatus(repository);

        } catch (DataException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
        }
    }


    @GET
    public HttpServletResponse getRoot() {
        return ArbitraryDataRenderer.getResponse(response, 200, "");
    }


    @GET
    @Path("{path:.*}")
    @SecurityRequirement(name = "apiKey")
    public HttpServletResponse getPath(@PathParam("path") String inPath) {
        // Block requests from localhost, to prevent websites/apps from running javascript that fetches unvetted data
        Security.disallowLoopbackRequests(request);
        return this.parsePath(inPath, "gateway", null, true, true);
    }

    
    private HttpServletResponse parsePath(String inPath, String qdnContext, String secret58, boolean includeResourceIdInPrefix, boolean async) {

        if (inPath == null || inPath.isEmpty()) {
            // Assume not a real file
            return ArbitraryDataRenderer.getResponse(response, 404, "Error 404: File Not Found");
        }

        // Default service is WEBSITE
        Service service = Service.WEBSITE;
        String name = null;
        String identifier = null;
        String outPath = "";
        List<String> prefixParts = new ArrayList<>();

        if (!inPath.contains("/")) {
            // Assume entire inPath is a registered name
            name = inPath;
        }
        else {
            // Parse the path to determine what we need to load
            List<String> parts = new LinkedList<>(Arrays.asList(inPath.split("/")));

            // Check if the first element is a service
            try {
                Service parsedService = Service.valueOf(parts.get(0).toUpperCase());
                if (parsedService != null) {
                    // First element matches a service, so we can assume it is one
                    service = parsedService;
                    parts.remove(0);
                    prefixParts.add(service.name());
                }
            } catch (IllegalArgumentException e) {
                // Not a service
            }

            if (parts.isEmpty()) {
                // We need more than just a service
                return ArbitraryDataRenderer.getResponse(response, 404, "Error 404: File Not Found");
            }

            // Service is removed, so assume first element is now a registered name
            name = parts.get(0);
            parts.remove(0);

            if (!parts.isEmpty()) {
                // Name is removed, so check if the first element is now an identifier
                ArbitraryResourceStatus status = this.getStatus(service, name, parts.get(0), false);
                if (status.getTotalChunkCount() > 0) {
                    // Matched service, name and identifier combination - so assume this is an identifier and can be removed
                    identifier = parts.get(0);
                    parts.remove(0);
                    prefixParts.add(identifier);
                }
            }

            if (!parts.isEmpty()) {
                // outPath can be built by combining any remaining parts
                outPath = String.join("/", parts);
            }
        }

        String prefix = StringUtils.join(prefixParts, "/");
        if (prefix != null && !prefix.isEmpty()) {
            prefix = "/" + prefix;
        }

        ArbitraryDataRenderer renderer = new ArbitraryDataRenderer(name, ResourceIdType.NAME, service, identifier, outPath,
                secret58, prefix, includeResourceIdInPrefix, async, qdnContext, request, response, context);
        return renderer.render();
    }

}
