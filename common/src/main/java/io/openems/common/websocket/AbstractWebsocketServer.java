package io.openems.common.websocket;

import java.net.InetSocketAddress;
import java.util.Optional;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.openems.common.session.Session;
import io.openems.common.session.SessionData;
import io.openems.common.session.SessionManager;
import io.openems.common.utils.JsonUtils;

public abstract class AbstractWebsocketServer<S extends Session<D>, D extends SessionData, M extends SessionManager<S, D>>
		extends WebSocketServer {
	private final Logger log = LoggerFactory.getLogger(AbstractWebsocketServer.class);
	protected final M sessionManager;
	protected final BiMap<WebSocket, S> websockets = Maps.synchronizedBiMap(HashBiMap.create());

	protected abstract void _onMessage(WebSocket websocket, JsonObject jMessage, Optional<JsonArray> jMessageIdOpt,
			Optional<String> deviceNameOpt);

	protected abstract void _onOpen(WebSocket websocket, ClientHandshake handshake);

	public AbstractWebsocketServer(int port, M sessionManager) {
		super(new InetSocketAddress(port));
		this.sessionManager = sessionManager;
	}

	/**
	 * Open event of websocket.
	 */
	@Override
	public final void onOpen(WebSocket websocket, ClientHandshake handshake) {
		try {
			this._onOpen(websocket, handshake);
		} catch (Throwable e) {
			log.error("onOpen-Error [" + handshake.toString() + "]: " + e.getMessage());
		}
	}

	/**
	 * Close event of websocket. Removes the websocket. Keeps the session
	 */
	@Override
	public void onClose(WebSocket websocket, int code, String reason, boolean remote) {
		S session = this.websockets.get(websocket);
		String sessionString;
		if (session == null) {
			sessionString = "";
		} else {
			sessionString = session.toString();
		}
		log.info("Websocket closed. " + sessionString + " Code [" + code + "] Reason [" + reason + "]");
		this.websockets.remove(websocket);
	}

	/**
	 * Error event of websocket. Logs the error.
	 */
	@Override
	public final void onError(WebSocket websocket, Exception ex) {
		S session = this.websockets.get(websocket);
		String sessionString;
		if (session == null) {
			sessionString = "";
		} else {
			sessionString = session.toString();
		}
		log.warn("Websocket error. " + sessionString + ": " + ex.getMessage());
	}

	/**
	 * Message event of websocket. Handles a new message.
	 */
	@Override
	public final void onMessage(WebSocket websocket, String message) {
		try {
			JsonObject jMessage = (new JsonParser()).parse(message).getAsJsonObject();
			Optional<JsonArray> jMessageId = JsonUtils.getAsOptionalJsonArray(jMessage, "id");
			Optional<String> deviceNameOpt = JsonUtils.getAsOptionalString(jMessage, "device");
			this._onMessage(websocket, jMessage, jMessageId, deviceNameOpt);
		} catch (Throwable e) {
			log.error("onMessage-Error [" + message + "]: " + e.getMessage());
		}
	}

	/**
	 * Get cookie from handshake
	 *
	 * @param handshake
	 * @return cookie as JsonObject
	 */
	protected JsonObject parseCookieFromHandshake(ClientHandshake handshake) {
		JsonObject j = new JsonObject();
		if (handshake.hasFieldValue("cookie")) {
			String cookieString = handshake.getFieldValue("cookie");
			for (String cookieVariable : cookieString.split("; ")) {
				String[] keyValue = cookieVariable.split("=");
				if (keyValue.length == 2) {
					j.addProperty(keyValue[0], keyValue[1]);
				}
			}
		}
		return j;
	}

	@Override
	public final void onStart() {
		// nothing to do
	}

	/**
	 * Returns the Websocket for the given token
	 *
	 * @param name
	 * @return
	 */
	public Optional<WebSocket> getWebsocketByToken(String token) {
		Optional<S> sessionOpt = (Optional<S>) this.sessionManager.getSessionByToken(token);
		if (!sessionOpt.isPresent()) {
			return Optional.empty();
		}
		S session = sessionOpt.get();
		return Optional.ofNullable(this.websockets.inverse().get(session));
	}

}
