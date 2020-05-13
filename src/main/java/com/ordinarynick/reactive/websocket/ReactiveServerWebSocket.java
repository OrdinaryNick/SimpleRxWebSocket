package com.ordinarynick.reactive.websocket;

import javax.websocket.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;

/**
 * Abstract class for all reactive server web sockets. You must implement this class to use {@link
 * javax.websocket.server.ServerEndpoint} as reactive web socket.
 *
 * @param <T> Type which is expected in communication with {@link ClientEndpoint}.
 */
public abstract class ReactiveServerWebSocket<T> extends ReactiveWebSocket<T> {

	/**
	 * All connected {@link ClientEndpoint} {@link Session}s.
	 */
	private final Collection<Session> clientSessions = new HashSet<>();

	/**
	 * Prepare this web socket.
	 *
	 * @param tClass Class which is expected in communication with {@link ClientEndpoint}.
	 */
	protected ReactiveServerWebSocket(final Class<T> tClass) {
		super(tClass);
	}

	/**
	 * Method to react on client socket connection.
	 *
	 * @param session Session of client socket, which currently was connected.
	 *
	 * @see OnOpen
	 */
	@OnOpen
	public void onOpen(final Session session) {
		clientSessions.add(session);
	}

	/**
	 * Will receive message from {@link ClientEndpoint}Do not override this method, override method {@link
	 * #onMessage(Session, Object)} instead.
	 *
	 * @param session Session of client socket, which sent the message.
	 *
	 * @see OnMessage
	 */
	@OnMessage
	public void onMessage(final Session session, final String message) {
		final T entity = gson.fromJson(message, tClass);
		subscribers.forEach(subscriber -> subscriber.onNext(entity));
		onMessage(session, entity);
	}

	/**
	 * Receives client message from {@link ClientEndpoint} as object.
	 *
	 * @param session Session of client socket, which sent the message.
	 * @param entity  Entity which was sent.
	 */
	protected void onMessage(final Session session, final T entity) {
	}

	/**
	 * Method to react any error during communication with client socket.
	 *
	 * @param session Session of client socket, were was problem.
	 * @param t       Error which occurs.
	 *
	 * @see OnError
	 */
	@OnError
	public void onError(final Session session, final Throwable t) {
		// TODO Maybe send error to observers?
	}

	/**
	 * Method to react, when client socket going to be disconnect.
	 *
	 * @param session Session of client socket, which will be disconnected.
	 *
	 * @see OnClose
	 */
	@OnClose
	public void onClose(final Session session) {
		clientSessions.remove(session);
	}

	/**
	 * Accept entity and sent it to all connected {@link ClientEndpoint}.
	 *
	 * @param entity Entity which will be sent.
	 */
	@Override
	public void onNext(final T entity) {
		broadcast(entity);
	}

	/**
	 * Sent object to all clients.
	 *
	 * @param entity Entity which will be sent.
	 */
	public void broadcast(final T entity) {
		clientSessions.forEach(session -> sentEntity(entity, session));
	}

	/**
	 * Sent entity to some connected {@link ClientEndpoint}s.
	 *
	 * @param entity          Entity which will be sent.
	 * @param filterPredicate Predicate for {@link Session} to filter connected {@link ClientEndpoint}s.
	 */
	public void sentEntity(final T entity, final Predicate<Session> filterPredicate) {
		clientSessions.stream().filter(filterPredicate).forEach(session -> super.sentEntity(entity, session));
	}
}
