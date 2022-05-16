package org.qortal.test.common;

import static org.junit.Assert.*;

import java.lang.reflect.Field;

import org.eclipse.jetty.server.Request;
import org.junit.Before;
import org.qortal.api.ApiError;
import org.qortal.api.ApiException;
import org.qortal.repository.DataException;

public class ApiCommon extends Common {

	public static final long MAX_API_RESPONSE_PERIOD = 2_000L; // ms

	public static final Boolean[] ALL_BOOLEAN_VALUES = new Boolean[] { null, true, false };
	public static final Boolean[] TF_BOOLEAN_VALUES = new Boolean[] { true, false };

	public static final Integer[] SAMPLE_LIMIT_VALUES = new Integer[] { null, 0, 1, 20 };
	public static final Integer[] SAMPLE_OFFSET_VALUES = new Integer[] { null, 0, 1, 5 };

	@FunctionalInterface
	public interface SlicedApiCall {
		public abstract void call(Integer limit, Integer offset, Boolean reverse);
	}

	public static class FakeRequest extends Request {
		public FakeRequest() {
			super(null, null);
		}

		@Override
		public String getRemoteAddr() {
			return "127.0.0.1";
		}
	}
	private static final FakeRequest FAKE_REQUEST = new FakeRequest();

	public String aliceAddress;
	public String bobAddress;

	@Before
	public void beforeTests() throws DataException {
		Common.useDefaultSettings();

		this.aliceAddress = Common.getTestAccount(null, "alice").getAddress();
		this.bobAddress = Common.getTestAccount(null, "bob").getAddress();
	}

	public static Object buildResource(Class<?> resourceClass) {
		try {
			Object resource = resourceClass.getDeclaredConstructor().newInstance();

			Field requestField = resourceClass.getDeclaredField("request");
			requestField.setAccessible(true);
			requestField.set(resource, FAKE_REQUEST);

			return resource;
		} catch (Exception e) {
			throw new RuntimeException("Failed to build API resource " + resourceClass.getName() + ": " + e.getMessage(), e);
		}
	}

	public static void assertApiError(ApiError expectedApiError, Runnable apiCall, Long maxResponsePeriod) {
		try {
			long beforeTimestamp = System.currentTimeMillis();
			apiCall.run();

			if (maxResponsePeriod != null) {
				long responsePeriod = System.currentTimeMillis() - beforeTimestamp;
				if (responsePeriod > maxResponsePeriod)
					fail(String.format("API call response period %d ms greater than max allowed (%d ms)", responsePeriod, maxResponsePeriod));
			}
		} catch (ApiException e) {
			ApiError actualApiError = ApiError.fromCode(e.error);
			assertEquals(expectedApiError, actualApiError);
		}
	}

	public static void assertApiError(ApiError expectedApiError, Runnable apiCall) {
		assertApiError(expectedApiError, apiCall, MAX_API_RESPONSE_PERIOD);
	}

	public static void assertNoApiError(Runnable apiCall, Long maxResponsePeriod) {
		try {
			long beforeTimestamp = System.currentTimeMillis();
			apiCall.run();

			if (maxResponsePeriod != null) {
				long responsePeriod = System.currentTimeMillis() - beforeTimestamp;
				if (responsePeriod > maxResponsePeriod)
					fail(String.format("API call response period %d ms greater than max allowed (%d ms)", responsePeriod, maxResponsePeriod));
			}
		} catch (ApiException e) {
			fail("ApiException unexpected");
		}
	}

	public static void assertNoApiError(Runnable apiCall) {
		assertNoApiError(apiCall, MAX_API_RESPONSE_PERIOD);
	}

	public static void assertNoApiError(SlicedApiCall apiCall) {
		for (Integer limit : SAMPLE_LIMIT_VALUES)
			for (Integer offset : SAMPLE_OFFSET_VALUES)
				for (Boolean reverse : ALL_BOOLEAN_VALUES)
					assertNoApiError(() -> apiCall.call(limit, offset, reverse));
	}

}
