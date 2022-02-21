package org.qortal.api;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.checkerframework.checker.units.qual.A;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.rewrite.handler.RedirectPatternRule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.DetectorConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.RequestLogWriter;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.qortal.api.resource.AnnotationPostProcessor;
import org.qortal.api.resource.ApiDefinition;
import org.qortal.api.websocket.ActiveChatsWebSocket;
import org.qortal.api.websocket.AdminStatusWebSocket;
import org.qortal.api.websocket.BlocksWebSocket;
import org.qortal.api.websocket.ChatMessagesWebSocket;
import org.qortal.api.websocket.PresenceWebSocket;
import org.qortal.api.websocket.TradeBotWebSocket;
import org.qortal.api.websocket.TradeOffersWebSocket;
import org.qortal.settings.Settings;

public class ApiService {

	private static ApiService instance;

	private final ResourceConfig config;
	private Server server;
	private ApiKey apiKey;

	private ApiService() {
		this.config = new ResourceConfig();
		this.config.packages("org.qortal.api.resource");
		this.config.register(OpenApiResource.class);
		this.config.register(ApiDefinition.class);
		this.config.register(AnnotationPostProcessor.class);
	}

	public static ApiService getInstance() {
		if (instance == null)
			instance = new ApiService();

		return instance;
	}

	public Iterable<Class<?>> getResources() {
		return this.config.getClasses();
	}

	public void setApiKey(ApiKey apiKey) {
		this.apiKey = apiKey;
	}

	public ApiKey getApiKey() {
		return this.apiKey;
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
				httpConfig.setSecurePort(Settings.getInstance().getApiPort());

				SecureRequestCustomizer src = new SecureRequestCustomizer();
				httpConfig.addCustomizer(src);

				HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(httpConfig);
				SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());

				ServerConnector portUnifiedConnector = new ServerConnector(this.server,
						new DetectorConnectionFactory(sslConnectionFactory),
						httpConnectionFactory);
				portUnifiedConnector.setHost(Settings.getInstance().getBindAddress());
				portUnifiedConnector.setPort(Settings.getInstance().getApiPort());

				this.server.addConnector(portUnifiedConnector);
			} else {
				// Non-SSL
				InetAddress bindAddr = InetAddress.getByName(Settings.getInstance().getBindAddress());
				InetSocketAddress endpoint = new InetSocketAddress(bindAddr, Settings.getInstance().getApiPort());
				this.server = new Server(endpoint);
			}

			// Error handler
			ErrorHandler errorHandler = new ApiErrorHandler();
			this.server.setErrorHandler(errorHandler);

			// Request logging
			if (Settings.getInstance().isApiLoggingEnabled()) {
				RequestLogWriter logWriter = new RequestLogWriter("API-requests.log");
				logWriter.setAppend(true);
				logWriter.setTimeZone("UTC");
				RequestLog requestLog = new CustomRequestLog(logWriter, CustomRequestLog.EXTENDED_NCSA_FORMAT);
				this.server.setRequestLog(requestLog);
			}

			// IP address based access control
			InetAccessHandler accessHandler = new InetAccessHandler();
			for (String pattern : Settings.getInstance().getApiWhitelist()) {
				accessHandler.include(pattern);
			}
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

			if (Settings.getInstance().isApiDocumentationEnabled()) {
				// Swagger-UI static content
				ClassLoader loader = this.getClass().getClassLoader();
				ServletHolder swaggerUIServlet = new ServletHolder("static-swagger-ui", DefaultServlet.class);
				swaggerUIServlet.setInitParameter("resourceBase", loader.getResource("resources/swagger-ui/").toString());
				swaggerUIServlet.setInitParameter("dirAllowed", "true");
				swaggerUIServlet.setInitParameter("pathInfoOnly", "true");
				context.addServlet(swaggerUIServlet, "/api-documentation/*");

				rewriteHandler.addRule(new RedirectPatternRule("", "/api-documentation/")); // redirect empty path to API docs
				rewriteHandler.addRule(new RedirectPatternRule("/api-documentation", "/api-documentation/")); // redirect to add trailing slash if missing
			} else {
				// Simple pages that explains that API documentation is disabled
				ClassLoader loader = this.getClass().getClassLoader();
				ServletHolder swaggerUIServlet = new ServletHolder("api-docs-disabled", DefaultServlet.class);
				swaggerUIServlet.setInitParameter("resourceBase", loader.getResource("api-docs-disabled/").toString());
				swaggerUIServlet.setInitParameter("dirAllowed", "true");
				swaggerUIServlet.setInitParameter("pathInfoOnly", "true");
				context.addServlet(swaggerUIServlet, "/api-documentation/*");

				rewriteHandler.addRule(new RedirectPatternRule("", "/api-documentation/")); // redirect empty path to API docs
				rewriteHandler.addRule(new RedirectPatternRule("/api-documentation", "/api-documentation/")); // redirect to add trailing slash if missing
			}

			context.addServlet(AdminStatusWebSocket.class, "/websockets/admin/status");
			context.addServlet(BlocksWebSocket.class, "/websockets/blocks");
			context.addServlet(ActiveChatsWebSocket.class, "/websockets/chat/active/*");
			context.addServlet(ChatMessagesWebSocket.class, "/websockets/chat/messages");
			context.addServlet(TradeOffersWebSocket.class, "/websockets/crosschain/tradeoffers");
			context.addServlet(TradeBotWebSocket.class, "/websockets/crosschain/tradebot");
			context.addServlet(PresenceWebSocket.class, "/websockets/presence");

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
