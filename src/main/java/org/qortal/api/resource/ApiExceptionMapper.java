package org.qortal.api.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.server.ParamException;
import org.qortal.settings.Settings;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<RuntimeException> {

	private static final Logger LOGGER = LogManager.getLogger(ApiExceptionMapper.class);

	@Context
	private HttpServletRequest request;

	@Context
	private ServletContext servletContext;

	@Override
	public Response toResponse(RuntimeException e) {
		if (Settings.getInstance().isApiLoggingEnabled())
			LOGGER.info(String.format("Exception %s during API call: %s", e.getClass().getCanonicalName(), request.getRequestURI()));

		if (e instanceof ParamException) {
			ParamException pe = (ParamException) e;
			return Response.status(Response.Status.BAD_REQUEST).entity("Bad parameter \"" + pe.getParameterName() + "\"").build();
		}

		if (e instanceof WebApplicationException)
			return ((WebApplicationException) e).getResponse();

		return Response.serverError().build();
	}

}
