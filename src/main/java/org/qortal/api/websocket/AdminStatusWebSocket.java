package org.qortal.api.websocket;

import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.qortal.api.model.NodeStatus;
import org.qortal.controller.Controller;
import org.qortal.event.Event;
import org.qortal.event.EventBus;
import org.qortal.event.Listener;

@WebSocket
@SuppressWarnings("serial")
public class AdminStatusWebSocket extends ApiWebSocket implements Listener {

	private static final AtomicReference<String> previousOutput = new AtomicReference<>(null);

	@Override
	public void configure(WebSocketServletFactory factory) {
		factory.register(AdminStatusWebSocket.class);

		try {
			previousOutput.set(buildStatusString());
		} catch (IOException e) {
			// How to fail properly?
			return;
		}

		EventBus.INSTANCE.addListener(this::listen);
	}

	@Override
	public void listen(Event event) {
		if (!(event instanceof Controller.StatusChangeEvent))
			return;

		String newOutput;
		try {
			newOutput = buildStatusString();
		} catch (IOException e) {
			// Ignore this time?
			return;
		}

		if (previousOutput.getAndUpdate(currentValue -> newOutput).equals(newOutput))
			// Output hasn't changed, so don't send anything
			return;

		for (Session session : getSessions())
			this.sendStatus(session, newOutput);
	}

	@OnWebSocketConnect
	@Override
	public void onWebSocketConnect(Session session) {
		this.sendStatus(session, previousOutput.get());

		super.onWebSocketConnect(session);
	}

	@OnWebSocketClose
	@Override
	public void onWebSocketClose(Session session, int statusCode, String reason) {
		super.onWebSocketClose(session, statusCode, reason);
	}

	@OnWebSocketError
	public void onWebSocketError(Session session, Throwable throwable) {
		/* We ignore errors for now, but method here to silence log spam */
	}

	@OnWebSocketMessage
	public void onWebSocketMessage(Session session, String message) {
		/* ignored */
	}

	private static String buildStatusString() throws IOException {
		NodeStatus nodeStatus = new NodeStatus();
		StringWriter stringWriter = new StringWriter();
		marshall(stringWriter, nodeStatus);
		return stringWriter.toString();
	}

	private void sendStatus(Session session, String status) {
		try {
			session.getRemote().sendStringByFuture(status);
		} catch (WebSocketException e) {
			// No output this time?
		}
	}

}
