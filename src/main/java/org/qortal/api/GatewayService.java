package org.qortal.api;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.qortal.api.resource.AnnotationPostProcessor;
import org.qortal.api.resource.ApiDefinition;
import org.qortal.settings.Settings;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;

public class GatewayService {

	private static GatewayService instance;

	private final ResourceConfig config;
	private Server server;

	private GatewayService() {
		this.config = new ResourceConfig();
		this.config.packages("org.qortal.api.gateway.resource");
		this.config.register(OpenApiResource.class);
		this.config.register(ApiDefinition.class);
		this.config.register(AnnotationPostProcessor.class);
	}

	public static GatewayService getInstance() {
		if (instance == null)
			instance = new GatewayService();

		return instance;
	}

	public Iterable<Class<?>> getResources() {
		return this.config.getClasses();
	}

	public void start() {
		try {
			// Create API server

			// SSL support if requested
			String keystorePathname = Settings.getInstance().getSslKeystorePathname();
			String keystorePassword = Settings.getInstance().getSslKeystorePassword();

			if (keystorePathname != null && keystorePassword != null) {
				// SSL version
				if (!Files.isReadable(Path.of(keystorePathname)))
					throw new RuntimeException("Failed to start SSL API due to broken keystore");

				// BouncyCastle-specific SSLContext build
				SSLContext sslContext = SSLContext.getInstance("TLS", "BCJSSE");
				KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("PKIX", "BCJSSE");

				KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType(), "BC");

				try (InputStream keystoreStream = Files.newInputStream(Paths.get(keystorePathname))) {
					keyStore.load(keystoreStream, keystorePassword.toCharArray());
				}

				keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
				sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

				SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
				sslContextFactory.setSslContext(sslContext);

				this.server = new Server();

				HttpConfiguration httpConfig = new HttpConfiguration();
				httpConfig.setSecureScheme("https");
				httpConfig.setSecurePort(Settings.getInstance().getGatewayPort());

				SecureRequestCustomizer src = new SecureRequestCustomizer();
				httpConfig.addCustomizer(src);

				HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(httpConfig);
				SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());

				ServerConnector portUnifiedConnector = new ServerConnector(this.server,
						new DetectorConnectionFactory(sslConnectionFactory),
						httpConnectionFactory);
				portUnifiedConnector.setHost(Settings.getInstance().getBindAddress());
				portUnifiedConnector.setPort(Settings.getInstance().getGatewayPort());

				this.server.addConnector(portUnifiedConnector);
			} else {
				// Non-SSL
				InetAddress bindAddr = InetAddress.getByName(Settings.getInstance().getBindAddress());
				InetSocketAddress endpoint = new InetSocketAddress(bindAddr, Settings.getInstance().getGatewayPort());
				this.server = new Server(endpoint);
			}

			// Error handler
			ErrorHandler errorHandler = new ApiErrorHandler();
			this.server.setErrorHandler(errorHandler);

			// Request logging
			if (Settings.getInstance().isGatewayLoggingEnabled()) {
				RequestLogWriter logWriter = new RequestLogWriter("gateway-requests.log");
				logWriter.setAppend(true);
				logWriter.setTimeZone("UTC");
				RequestLog requestLog = new CustomRequestLog(logWriter, CustomRequestLog.EXTENDED_NCSA_FORMAT);
				this.server.setRequestLog(requestLog);
			}

			// Access handler (currently no whitelist is used)
			InetAccessHandler accessHandler = new InetAccessHandler();
			this.server.setHandler(accessHandler);

			// URL rewriting
			RewriteHandler rewriteHandler = new RewriteHandler();
			accessHandler.setHandler(rewriteHandler);

			// Context
			ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
			context.setContextPath("/");
			rewriteHandler.setHandler(context);

			// Cross-origin resource sharing
			FilterHolder corsFilterHolder = new FilterHolder(CrossOriginFilter.class);
			corsFilterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
			corsFilterHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET, POST, DELETE");
			corsFilterHolder.setInitParameter(CrossOriginFilter.CHAIN_PREFLIGHT_PARAM, "false");
			context.addFilter(corsFilterHolder, "/*", null);

			// API servlet
			ServletContainer container = new ServletContainer(this.config);
			ServletHolder apiServlet = new ServletHolder(container);
			apiServlet.setInitOrder(1);
			context.addServlet(apiServlet, "/*");

			// Start server
			this.server.start();
		} catch (Exception e) {
			// Failed to start
			throw new RuntimeException("Failed to start API", e);
		}
	}

	public void stop() {
		try {
			// Stop server
			this.server.stop();
		} catch (Exception e) {
			// Failed to stop
		}

		this.server = null;
	}

}
