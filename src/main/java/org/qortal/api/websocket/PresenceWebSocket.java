package org.qortal.api.websocket;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.qortal.controller.Controller;
import org.qortal.crypto.Crypto;
import org.qortal.data.transaction.PresenceTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.event.Event;
import org.qortal.event.EventBus;
import org.qortal.event.Listener;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.transaction.PresenceTransaction.PresenceType;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

@WebSocket
@SuppressWarnings("serial")
public class PresenceWebSocket extends ApiWebSocket implements Listener {

	@XmlAccessorType(XmlAccessType.FIELD)
	@SuppressWarnings("unused")
	private static class PresenceInfo {
		private final PresenceType presenceType;
		private final String publicKey;
		private final long timestamp;
		private final String address;

		protected PresenceInfo() {
			this.presenceType = null;
			this.publicKey = null;
			this.timestamp = 0L;
			this.address = null;
		}

		public PresenceInfo(PresenceType presenceType, String pubKey58, long timestamp) {
			this.presenceType = presenceType;
			this.publicKey = pubKey58;
			this.timestamp = timestamp;
			this.address = Crypto.toAddress(Base58.decode(this.publicKey));
		}

		public PresenceType getPresenceType() {
			return this.presenceType;
		}

		public String getPublicKey() {
			return this.publicKey;
		}

		public long getTimestamp() {
			return this.timestamp;
		}

		public String getAddress() {
			return this.address;
		}
	}

	/** Outer map key is PresenceType (enum), inner map key is public key in base58, inner map value is timestamp */
	private static final Map<PresenceType, Map<String, Long>> currentEntries = Collections.synchronizedMap(new EnumMap<>(PresenceType.class));

	/** (Optional) PresenceType used for filtering by that Session. */
	private static final Map<Session, PresenceType> sessionPresenceTypes = Collections.synchronizedMap(new HashMap<>());

	@Override
	public void configure(WebSocketServletFactory factory) {
		factory.register(PresenceWebSocket.class);

		try (final Repository repository = RepositoryManager.getRepository()) {
			populateCurrentInfo(repository);
		} catch (DataException e) {
			// How to fail properly?
			return;
		}

		EventBus.INSTANCE.addListener(this::listen);
	}

	@Override
	public void listen(Event event) {
		// We use NewBlockEvent as a proxy for 1-minute timer
		if (!(event instanceof Controller.NewTransactionEvent) && !(event instanceof Controller.NewBlockEvent))
			return;

		removeOldEntries();

		if (event instanceof Controller.NewBlockEvent)
			// We only wanted a chance to cull old entries
			return;

		TransactionData transactionData = ((Controller.NewTransactionEvent) event).getTransactionData();

		if (transactionData.getType() != TransactionType.PRESENCE)
			return;

		PresenceTransactionData presenceData = (PresenceTransactionData) transactionData;
		PresenceType presenceType = presenceData.getPresenceType();

		// Put/replace for this publickey making sure we keep newest timestamp
		String pubKey58 = Base58.encode(presenceData.getCreatorPublicKey());
		long ourTimestamp = presenceData.getTimestamp();
		long computedTimestamp = mergePresence(presenceType, pubKey58, ourTimestamp);

		if (computedTimestamp != ourTimestamp)
			// nothing changed
			return;

		List<PresenceInfo> presenceInfo = Collections.singletonList(new PresenceInfo(presenceType, pubKey58, computedTimestamp));

		// Notify sessions
		for (Session session : getSessions()) {
			PresenceType sessionPresenceType = sessionPresenceTypes.get(session);

			if (sessionPresenceType == null || sessionPresenceType == presenceType)
				sendPresenceInfo(session, presenceInfo);
		}
	}

	@OnWebSocketConnect
	@Override
	public void onWebSocketConnect(Session session) {
		Map<String, List<String>> queryParams = session.getUpgradeRequest().getParameterMap();
		List<String> presenceTypes = queryParams.get("presenceType");

		// We only support ONE presenceType
		String presenceTypeName = presenceTypes == null || presenceTypes.isEmpty() ? null : presenceTypes.get(0);

		PresenceType presenceType = presenceTypeName == null ? null : PresenceType.fromString(presenceTypeName);

		// Make sure that if caller does give a presenceType, that it is a valid/known one.
		if (presenceTypeName != null && presenceType == null) {
			session.close(4003, "unknown presenceType: " + presenceTypeName);
			return;
		}

		// Save session's requested PresenceType, if given
		if (presenceType != null)
			sessionPresenceTypes.put(session, presenceType);

		List<PresenceInfo> presenceInfo;

		synchronized (currentEntries) {
			presenceInfo = currentEntries.entrySet().stream()
					.filter(entry -> presenceType == null ? true : entry.getKey() == presenceType)
					.flatMap(entry -> entry.getValue().entrySet().stream().map(innerEntry -> new PresenceInfo(entry.getKey(), innerEntry.getKey(), innerEntry.getValue())))
					.collect(Collectors.toList());
		}

		if (!sendPresenceInfo(session, presenceInfo)) {
			session.close(4002, "websocket issue");
			return;
		}

		super.onWebSocketConnect(session);
	}

	@OnWebSocketClose
	@Override
	public void onWebSocketClose(Session session, int statusCode, String reason) {
		// clean up
		sessionPresenceTypes.remove(session);

		super.onWebSocketClose(session, statusCode, reason);
	}

	@OnWebSocketError
	public void onWebSocketError(Session session, Throwable throwable) {
		/* ignored */
	}

	@OnWebSocketMessage
	public void onWebSocketMessage(Session session, String message) {
		/* ignored */
	}

	private boolean sendPresenceInfo(Session session, List<PresenceInfo> presenceInfo) {
		try {
			StringWriter stringWriter = new StringWriter();
			marshall(stringWriter, presenceInfo);

			String output = stringWriter.toString();
			session.getRemote().sendStringByFuture(output);
		} catch (IOException e) {
			// No output this time?
			return false;
		}

		return true;
	}

	private static void populateCurrentInfo(Repository repository) throws DataException {
		// We want ALL PRESENCE transactions

		List<TransactionData> presenceTransactionsData = repository.getTransactionRepository().getUnconfirmedTransactions(TransactionType.PRESENCE, null);

		for (TransactionData transactionData : presenceTransactionsData) {
			PresenceTransactionData presenceData = (PresenceTransactionData) transactionData;

			PresenceType presenceType = presenceData.getPresenceType();

			// Put/replace for this publickey making sure we keep newest timestamp
			String pubKey58 = Base58.encode(presenceData.getCreatorPublicKey());
			long ourTimestamp = presenceData.getTimestamp();

			mergePresence(presenceType, pubKey58, ourTimestamp);
		}
	}

	private static long mergePresence(PresenceType presenceType, String pubKey58, long ourTimestamp) {
		Map<String, Long> typedPubkeyTimestamps = currentEntries.computeIfAbsent(presenceType, someType -> Collections.synchronizedMap(new HashMap<>()));
		return typedPubkeyTimestamps.compute(pubKey58, (somePubKey58, currentTimestamp) -> (currentTimestamp == null || currentTimestamp < ourTimestamp) ? ourTimestamp : currentTimestamp);
	}

	private static void removeOldEntries() {
		long now = NTP.getTime();

		currentEntries.entrySet().forEach(entry -> {
			long expiryThreshold = now - entry.getKey().getLifetime();
			entry.getValue().entrySet().removeIf(pubkeyTimestamp -> pubkeyTimestamp.getValue() < expiryThreshold);
		});
	}

}
