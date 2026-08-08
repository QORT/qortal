package org.qortal.api;

import org.eclipse.persistence.exceptions.XMLMarshalException;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;

import javax.net.ssl.*;
import javax.xml.bind.*;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.*;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiRequest {

	private static final Pattern proxyUrlPattern = Pattern.compile("(https://)([^@:/]+)@([0-9.]{7,15})(/.*)");

	public static class FixedIpSocket extends Socket {
		private final String ipAddress;

		public FixedIpSocket(String ipAddress) {
			this.ipAddress = ipAddress;
		}

		@Override
		public void connect(SocketAddress endpoint, int timeout) throws IOException {
			InetSocketAddress inetEndpoint = (InetSocketAddress) endpoint;
			InetSocketAddress newEndpoint = new InetSocketAddress(ipAddress, inetEndpoint.getPort());
			super.connect(newEndpoint, timeout);
		}
	}

	public static String perform(String uri, Map<String, String> params) {
		if (params != null && !params.isEmpty())
			uri += "?" + getParamsString(params);

		try (InputStream in = fetchStream(uri); Scanner scanner = new Scanner(in, "UTF8")) {
			scanner.useDelimiter("\\A");
			return scanner.hasNext() ? scanner.next() : "";
		} catch (IOException e) {
			return null;
		}
	}

	public static Object perform(String uri, Class<?> responseClass, Map<String, String> params) {
		Unmarshaller unmarshaller = createUnmarshaller(responseClass);

		if (params != null && !params.isEmpty())
			uri += "?" + getParamsString(params);

		try (InputStream in = fetchStream(uri)) {
			StreamSource json = new StreamSource(in);

			// Attempt to unmarshal JSON stream to Settings
			return unmarshaller.unmarshal(json, responseClass).getValue();
		} catch (UnmarshalException e) {
			Throwable linkedException = e.getLinkedException();
			if (linkedException instanceof XMLMarshalException) {
				String message = ((XMLMarshalException) linkedException).getInternalException().getLocalizedMessage();
				throw new RuntimeException(message);
			}

			throw new RuntimeException("Unable to unmarshall API response", e);
		} catch (JAXBException e) {
			throw new RuntimeException("Unable to unmarshall API response", e);
		} catch (IOException e) {
			throw new RuntimeException("Unable to unmarshall API response", e);
		}
	}

	private static Unmarshaller createUnmarshaller(Class<?> responseClass) {
		try {
			// Create JAXB context aware of Settings
			JAXBContext jc = JAXBContextFactory.createContext(new Class[] { responseClass }, null);

			// Create unmarshaller
			Unmarshaller unmarshaller = jc.createUnmarshaller();

			// Set the unmarshaller media type to JSON
			unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");

			// Tell unmarshaller that there's no JSON root element in the JSON input
			unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);

			return unmarshaller;
		} catch (JAXBException e) {
			throw new RuntimeException("Unable to create API unmarshaller", e);
		}
	}

	private static Marshaller createMarshaller(Class<?> objectClass) {
		try {
			// Create JAXB context aware of object's class
			JAXBContext jc = JAXBContextFactory.createContext(new Class[] { objectClass }, null);

			// Create marshaller
			Marshaller marshaller = jc.createMarshaller();

			// Set the marshaller media type to JSON
			marshaller.setProperty(MarshallerProperties.MEDIA_TYPE, "application/json");

			// Tell marshaller not to include JSON root element in the output
			marshaller.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, false);

			return marshaller;
		} catch (JAXBException e) {
			throw new RuntimeException("Unable to create API marshaller", e);
		}
	}

	public static void marshall(Writer writer, Object object) throws IOException {
		Marshaller marshaller = createMarshaller(object.getClass());

		try {
			marshaller.marshal(object, writer);
		} catch (JAXBException e) {
			throw new IOException("Unable to create marshall object for API", e);
		}
	}

	public static String getParamsString(Map<String, String> params) {
		StringBuilder result = new StringBuilder();

		try {
			for (Map.Entry<String, String> entry : params.entrySet()) {
				result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
				result.append("=");
				result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
				result.append("&");
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Cannot encode API request params", e);
		}

		String resultString = result.toString();
		return !resultString.isEmpty() ? resultString.substring(0, resultString.length() - 1) : resultString;
	}

	/**
	 * Returns InputStream for given URI.
	 * <p>
	 * Also accepts special URI form:<br>
	 * <tt>https://&lt;hostname&gt;@&lt;ip-address&gt;/...</tt>

	 * @param uri
	 * @return
	 * @throws IOException
	 */
	public static InputStream fetchStream(String uri) throws IOException {
		String ipAddress = null;

		// Check for special proxy form
		Matcher uriMatcher = proxyUrlPattern.matcher(uri);
		if (uriMatcher.matches()) {
			ipAddress = uriMatcher.group(3);
			uri = uriMatcher.replaceFirst(uriMatcher.group(1) + uriMatcher.group(2) + uriMatcher.group(4));
		}

		URL url = new URL(uri);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();

		con.setRequestMethod("GET");
		con.setConnectTimeout(30000);
		con.setReadTimeout(10000);
		ApiRequest.setConnectionSSL(con, ipAddress);

		int status = con.getResponseCode();

		if (status != 200)
			throw new IOException("Non-OK HTTP response");

		return con.getInputStream();
	}

	public static void setConnectionSSL(HttpURLConnection con, String ipAddress) {
		if (!(con instanceof HttpsURLConnection))
			return;

		HttpsURLConnection httpsCon = (HttpsURLConnection) con;
		URL url = con.getURL();

		httpsCon.setSSLSocketFactory(new org.bouncycastle.jsse.util.CustomSSLSocketFactory(httpsCon.getSSLSocketFactory()) {
			@Override
			public Socket createSocket() throws IOException {
				if (ipAddress == null)
					return super.createSocket();

				return new FixedIpSocket(ipAddress);
			}

			@Override
			protected Socket configureSocket(Socket s) {
				if (s instanceof SSLSocket) {
					SSLSocket ssl = (SSLSocket) s;

					SNIHostName sniHostName = new SNIHostName(url.getHost());
					if (null != sniHostName) {
						SSLParameters sslParameters = new SSLParameters();

						sslParameters.setServerNames(Collections.<SNIServerName>singletonList(sniHostName));
						ssl.setSSLParameters(sslParameters);
					}
				}

				return s;
			}
		});
	}

}
