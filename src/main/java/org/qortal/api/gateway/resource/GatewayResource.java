package org.qortal.api.gateway.resource;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.qortal.api.Security;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.arbitrary.ArbitraryDataFile.ResourceIdType;
import org.qortal.arbitrary.ArbitraryDataReader;
import org.qortal.arbitrary.ArbitraryDataRenderer;
import org.qortal.arbitrary.ArbitraryDataResource;
import org.qortal.arbitrary.misc.Service;
import org.qortal.data.arbitrary.ArbitraryResourceStatus;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;


@Path("/")
@Tag(name = "Gateway")
public class GatewayResource {

    @Context HttpServletRequest request;
    @Context HttpServletResponse response;
    @Context ServletContext context;

    /**
     * We need to allow resource status checking (and building) via the gateway, as the node's API port
     * may not be forwarded and will almost certainly not be authenticated. Since gateways allow for
     * all resources to be loaded except those that are blocked, there is no need for authentication.
     */
    @GET
    @Path("/arbitrary/resource/status/{service}/{name}")
    public ArbitraryResourceStatus getDefaultResourceStatus(@PathParam("service") Service service,
                                                             @PathParam("name") String name,
                                                             @QueryParam("build") Boolean build) {

        return this.getStatus(service, name, null, build);
    }

    @GET
    @Path("/arbitrary/resource/status/{service}/{name}/{identifier}")
    public ArbitraryResourceStatus getResourceStatus(@PathParam("service") Service service,
                                                      @PathParam("name") String name,
                                                      @PathParam("identifier") String identifier,
                                                      @QueryParam("build") Boolean build) {

        return this.getStatus(service, name, identifier, build);
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


    @GET
    public HttpServletResponse getRoot() {
        return ArbitraryDataRenderer.getResponse(response, 200, "");
    }


    @GET
    @Path("{name}/{path:.*}")
    @SecurityRequirement(name = "apiKey")
    public HttpServletResponse getPathByName(@PathParam("name") String name,
                                             @PathParam("path") String inPath) {
        // Block requests from localhost, to prevent websites/apps from running javascript that fetches unvetted data
        Security.disallowLoopbackRequests(request);
        return this.get(name, ResourceIdType.NAME, Service.WEBSITE, inPath, null, "", true, true);
    }

    @GET
    @Path("{name}")
    @SecurityRequirement(name = "apiKey")
    public HttpServletResponse getIndexByName(@PathParam("name") String name) {
        // Block requests from localhost, to prevent websites/apps from running javascript that fetches unvetted data
        Security.disallowLoopbackRequests(request);
        return this.get(name, ResourceIdType.NAME, Service.WEBSITE, "/", null, "", true, true);
    }


    // Optional /site alternative for backwards support

    @GET
    @Path("/site/{name}/{path:.*}")
    public HttpServletResponse getSitePathByName(@PathParam("name") String name,
                                                 @PathParam("path") String inPath) {
        // Block requests from localhost, to prevent websites/apps from running javascript that fetches unvetted data
        Security.disallowLoopbackRequests(request);
        return this.get(name, ResourceIdType.NAME, Service.WEBSITE, inPath, null, "/site", true, true);
    }

    @GET
    @Path("/site/{name}")
    public HttpServletResponse getSiteIndexByName(@PathParam("name") String name) {
        // Block requests from localhost, to prevent websites/apps from running javascript that fetches unvetted data
        Security.disallowLoopbackRequests(request);
        return this.get(name, ResourceIdType.NAME, Service.WEBSITE, "/", null, "/site", true, true);
    }

    
    private HttpServletResponse get(String resourceId, ResourceIdType resourceIdType, Service service, String inPath,
                                    String secret58, String prefix, boolean usePrefix, boolean async) {

        ArbitraryDataRenderer renderer = new ArbitraryDataRenderer(resourceId, resourceIdType, service, inPath,
                secret58, prefix, usePrefix, async, request, response, context);
        return renderer.render();
    }

}
