package org.qortal.test.apps;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyTest {
	private static final Pattern proxyUrlPattern = Pattern.compile("(https://)([^@:/]+)@([0-9.]{7,15})(/.*)");

	public static void main(String args[]) {
		String uri = "https://raw.githubusercontent.com@151.101.16.133/QORT/qortal/master/pom.xml";

		try (InputStream in = fetchStream(uri)) {
			int byteCount = 0;
			byte[] buffer = new byte[1024 * 1024];
			do {
				int nread = in.read(buffer);
				if (nread == -1)
					break;

				byteCount += nread;
			} while (true);

			System.out.println(String.format("Fetched %d bytes", byteCount));
		} catch (IOException e) {
			throw new RuntimeException("Failed: ", e);
		}
	}

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
		con.setConnectTimeout(5000);
		con.setReadTimeout(5000);
		setConnectionSSL(con, ipAddress);

		int status = con.getResponseCode();

		if (status != 200)
			throw new IOException("Bad response");

		return con.getInputStream();
	}

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
