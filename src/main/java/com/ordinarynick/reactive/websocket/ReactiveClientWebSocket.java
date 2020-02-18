package com.ordinarynick.reactive.websocket;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client socket for {@link javax.websocket.server.ServerEndpoint} of web sockets. This class behaves like {@link
 * io.reactivex.subjects.Subject}. It can receives and can sends entities in JSON as {@link String}.
 *
 * @param <T> Type of {@link Class} which is expected in {@link OnMessage} method.
 *
 * @author Ordinary Nick
 * @version 1.0.0
 * @since reactive.websocket-1.0.0
 */
public class ReactiveClientWebSocket<T> extends ReactiveWebSocket<T> {

	/** Container for {@link ClientEndpoint}. */
	private final WebSocketContainer webSocketContainer;

	/** {@link Session} for communication with {@link javax.websocket.server.ServerEndpoint}. */
	private AtomicReference<Session> session = new AtomicReference<>();

	/** {@link URI} of {@link javax.websocket.server.ServerEndpoint} for connecting to it. */
	private final URI uri;

	/** {@link ClientEndpoint} for {@link javax.websocket.server.ServerEndpoint}. */
	private final ClientSocket clientSocket = new ClientSocket();

	/**
	 * Creates {@link ReactiveClientWebSocket} from url {@link String} with type of {@link Class}-
	 *
	 * @param url    Url of {@link javax.websocket.server.ServerEndpoint}.
	 * @param tClass Expected {@link Class} in communication.
	 *
	 * @since reactive.websocket-1.0.0
	 */
	public ReactiveClientWebSocket(final String url, final Class<T> tClass) {
		this(URI.create(url), tClass);
	}

	/**
	 * Creates {@link ReactiveClientWebSocket} from {@link URI} with type of {@link Class}-
	 *
	 * @param uri    {@link URI} of {@link javax.websocket.server.ServerEndpoint}.
	 * @param tClass Expected {@link Class} in communication.
	 *
	 * @since reactive.websocket-1.0.0
	 */
	public ReactiveClientWebSocket(final URI uri, final Class<T> tClass) {
		super(tClass);

		this.uri = uri;
		this.webSocketContainer = ContainerProvider.getWebSocketContainer();
	}

	/**
	 * Sends data to {@link javax.websocket.server.ServerEndpoint}.
	 *
	 * @param t {@link Class} T entity which will be sent to {@link javax.websocket.server.ServerEndpoint}.
	 *
	 * @since reactive.websocket-1.0.0
	 */
	public void onNext(final T t) {
		try {
			final Session session = this.session.get();

			if (session != null) {
				if (session.isOpen()) {
					session.getBasicRemote().sendText(gson.toJson(t));
				} else {
					throw new IllegalStateException("Session is already closed.");
				}
			}
		} catch (final IOException ignored) {
			// FIXME Do something with exception (log or sent as error or something else)
		}
	}

	@Override
	protected void subscribeActual(final Observer<? super T> observer) {
		super.subscribeActual(observer);

		connect();
	}

	@Override
	public void onSubscribe(final Disposable disposable) {
		super.onSubscribe(disposable);

		connect();
	}

	/**
	 * Connects to {@link javax.websocket.server.ServerEndpoint} if is not already connected.
	 *
	 * @since reactive.websocket-1.0.0
	 */
	private void connect() {
		try {
			if (session.get() == null) {
				session.set(webSocketContainer.connectToServer(clientSocket, uri));
			}
		} catch (DeploymentException | IOException ignored) {
			// FIXME Do something with exception (log or sent as error or something else)
		}
	}

	/**
	 * Close opened connections to {@link javax.websocket.server.ServerEndpoint}, unsubscribe all subscribed
	 * {@link io.reactivex.Observable}s and complete {@link io.reactivex.Observable} for all subscribed {@link Observer}s.
	 *
	 * @throws Exception is thrown when was problem with closing.
	 * @since reactive.websocket-1.0.0
	 */
	public void close() throws Exception {
		observers.forEach(Observer::onComplete);
		disposables.forEach(disposable -> {
			if (!disposable.isDisposed()) {
				disposable.dispose();
			}
		});

		if (session.get() != null) {
			session.getAndSet(null).close();
		}
	}

	/**
	 * Helper class for connecting to {@link javax.websocket.server.ServerEndpoint} on {@link #uri} and defining what to
	 * do when client receives message from {@link javax.websocket.server.ServerEndpoint}.
	 *
	 * @since reactive.websocket-1.0.0
	 */
	@ClientEndpoint
	public class ClientSocket {

		/**
		 * Receive message from {@link javax.websocket.server.ServerEndpoint} and send it to all {@link Observer}s and all
		 * {@link Consumer}s.
		 *
		 * @since reactive.websocket-1.0.0
		 */
		@OnMessage
		public void onMessage(final String message) {
			final T entity = gson.fromJson(message, tClass);
			observers.forEach(observer -> observer.onNext(entity));
		}

		// FIXME Should be here also @OnError, etc.?
	}

}
