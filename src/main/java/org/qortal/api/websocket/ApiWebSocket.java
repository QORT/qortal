package org.qortal.api.websocket;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.eclipse.jetty.http.pathmap.UriTemplatePathSpec;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.qortal.api.ApiError;
import org.qortal.api.ApiErrorRoot;

@SuppressWarnings("serial")
abstract class ApiWebSocket extends WebSocketServlet {

	private static final Map<Class<? extends ApiWebSocket>, List<Session>> SESSIONS_BY_CLASS = new HashMap<>();

	protected static String getPathInfo(Session session) {
		ServletUpgradeRequest upgradeRequest = (ServletUpgradeRequest) session.getUpgradeRequest();
		return upgradeRequest.getHttpServletRequest().getPathInfo();
	}

	protected static Map<String, String> getPathParams(Session session, String pathSpec) {
		UriTemplatePathSpec uriTemplatePathSpec = new UriTemplatePathSpec(pathSpec);
		return uriTemplatePathSpec.getPathParams(getPathInfo(session));
	}

	protected static void sendError(Session session, ApiError apiError) {
		ApiErrorRoot apiErrorRoot = new ApiErrorRoot();
		apiErrorRoot.setApiError(apiError);

		StringWriter stringWriter = new StringWriter();
		try {
			marshall(stringWriter, apiErrorRoot);
			session.getRemote().sendString(stringWriter.toString());
		} catch (IOException e) {
			// Remote end probably closed
		}
	}

	protected static void marshall(Writer writer, Object object) throws IOException {
		Marshaller marshaller = createMarshaller(object.getClass());

		try {
			marshaller.marshal(object, writer);
		} catch (JAXBException e) {
			throw new IOException("Unable to create marshall object for websocket", e);
		}
	}

	protected static void marshall(Writer writer, Collection<?> collection) throws IOException {
		// If collection is empty then we're returning "[]" anyway
		if (collection.isEmpty()) {
			writer.append("[]");
			return;
		}

		// Grab an entry from collection so we can determine type
		Object entry = collection.iterator().next();

		Marshaller marshaller = createMarshaller(entry.getClass());

		try {
			marshaller.marshal(collection, writer);
		} catch (JAXBException e) {
			throw new IOException("Unable to create marshall object for websocket", e);
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
			throw new RuntimeException("Unable to create websocket marshaller", e);
		}
	}

	public void onWebSocketConnect(Session session) {
		synchronized (SESSIONS_BY_CLASS) {
			SESSIONS_BY_CLASS.computeIfAbsent(this.getClass(), clazz -> new ArrayList<>()).add(session);
		}
	}

	public void onWebSocketClose(Session session, int statusCode, String reason) {
		synchronized (SESSIONS_BY_CLASS) {
			List<Session> sessions = SESSIONS_BY_CLASS.get(this.getClass());
			if (sessions != null)
				sessions.remove(session);
		}
	}

	protected List<Session> getSessions() {
		synchronized (SESSIONS_BY_CLASS) {
			return new ArrayList<>(SESSIONS_BY_CLASS.get(this.getClass()));
		}
	}

}
