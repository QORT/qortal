package org.qortal.api;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.http.HttpServletRequest;

import org.qortal.settings.Settings;

public abstract class Security {

	public static final String API_KEY_HEADER = "X-API-KEY";

	public static void checkApiCallAllowed(HttpServletRequest request) {
		// If API key checking has been disabled, we will allow the request in all cases
		boolean isApiKeyDisabled = Settings.getInstance().isApiKeyDisabled();
		if (isApiKeyDisabled)
			return;

		String expectedApiKey = Settings.getInstance().getApiKey();
		String passedApiKey = request.getHeader(API_KEY_HEADER);

		if ((expectedApiKey != null && !expectedApiKey.equals(passedApiKey)) ||
				(passedApiKey != null && !passedApiKey.equals(expectedApiKey)))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.UNAUTHORIZED);

		InetAddress remoteAddr;
		try {
			remoteAddr = InetAddress.getByName(request.getRemoteAddr());
		} catch (UnknownHostException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.UNAUTHORIZED);
		}

		if (!remoteAddr.isLoopbackAddress())
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.UNAUTHORIZED);
	}

}
