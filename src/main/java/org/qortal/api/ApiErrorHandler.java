package org.qortal.api;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.qortal.settings.Settings;

public class ApiErrorHandler extends ErrorHandler {

	private static final Logger LOGGER = LogManager.getLogger(ApiErrorHandler.class);

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		if (Settings.getInstance().isApiLoggingEnabled()) {
			String requestURI = request.getRequestURI();

			String queryString = request.getQueryString();
			if (queryString != null)
				requestURI += "?" + queryString;

			Throwable th = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
			if (th != null) {
				LOGGER.error(String.format("Unexpected %s during request %s", th.getClass().getCanonicalName(), requestURI));
			} else {
				LOGGER.error(String.format("Unexpected error during request %s", requestURI));
			}
		}

		super.handle(target, baseRequest, request, response);
	}

}
