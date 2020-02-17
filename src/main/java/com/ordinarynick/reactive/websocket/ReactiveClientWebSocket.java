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
 * io.reactivex.subjects.Subject}. It receives and sends entities in json string.
 *
 * @param <T> Type of class which is expected in {@link OnMessage} method.
 */
public class ReactiveClientWebSocket<T> extends ReactiveWebSocket<T> {

	/**
	 * Container for {@link ClientEndpoint}.
	 */
	private final WebSocketContainer webSocketContainer;
	/**
	 * Session for communication with {@link javax.websocket.server.ServerEndpoint}.
	 */
	private AtomicReference<Session> session = new AtomicReference<>();
	/**
	 * Uri of {@link javax.websocket.server.ServerEndpoint} for connecting to it.
	 */
	private final URI uri;
	/**
	 * {@link ClientEndpoint} for {@link javax.websocket.server.ServerEndpoint}.
	 */
	private final ClientSocket clientSocket = new ClientSocket();

	/**
	 * Create {@link io.reactivex.subjects.Subject} like {@link ClientEndpoint}.
	 *
	 * @param url    Url of {@link javax.websocket.server.ServerEndpoint}.
	 * @param tClass Expected {@link Class} in communication.
	 */
	public ReactiveClientWebSocket(final String url, final Class<T> tClass) {
		this(URI.create(url), tClass);
	}

	/**
	 * Create {@link io.reactivex.subjects.Subject} like {@link ClientEndpoint}.
	 *
	 * @param uri    Uri of {@link javax.websocket.server.ServerEndpoint}.
	 * @param tClass Expected {@link Class} in communication.
	 */
	public ReactiveClientWebSocket(final URI uri, final Class<T> tClass) {
		super(tClass);
		this.uri = uri;
		this.webSocketContainer = ContainerProvider.getWebSocketContainer();
	}

	/**
	 * Send data to {@link javax.websocket.server.ServerEndpoint}.
	 *
	 * @param t Entity which will be sent.
	 */
	public void onNext(final T t) {
		try {
			if (session.get() != null) {
				session.get().getBasicRemote().sendText(gson.toJson(t));
			}
		} catch (final IOException ignored) {
			// TODO Maybe do, something with exception
		}
	}


	/**
	 * Subscribe observer to web socket and tries to connect to {@link javax.websocket.server.ServerEndpoint} if not
	 * connected.
	 *
	 * @param observer Observer, which will be subscribed.
	 */
	@Override
	protected void subscribeActual(final Observer<? super T> observer) {
		super.subscribeActual(observer);

		connect();
	}


	/**
	 * Subscribe this web socket to Observable and stores disposable and tries to connect to {@link
	 * javax.websocket.server.ServerEndpoint} if not connected.
	 *
	 * @param disposable Disposable of subscription.
	 */
	@Override
	public void onSubscribe(final Disposable disposable) {
		super.onSubscribe(disposable);

		connect();
	}

	/**
	 * Connect to {@link javax.websocket.server.ServerEndpoint} if not connected.
	 */
	private void connect() {
		try {
			if (session.get() == null) {
				session.set(webSocketContainer.connectToServer(clientSocket, uri));
			}
		} catch (DeploymentException | IOException ignored) {
			// TODO Maybe do, something with exception
		}
	}

	/**
	 * Close opened connections and cleanup resources.
	 *
	 * @throws Exception is thrown when was problem with closing.
	 */
	public void close() throws Exception {
		observers.forEach(Observer::onComplete);

		for (final Disposable disposable : disposables) {
			if (!disposable.isDisposed()) {
				disposable.dispose();
			}
		}

		if (session.get() != null) {
			session.get().close();
		}
	}

	/**
	 * Helper class for connecting to {@link javax.websocket.server.ServerEndpoint} on {@link #uri}.
	 */
	@ClientEndpoint
	public class ClientSocket {

		/**
		 * Receive message from {@link javax.websocket.server.ServerEndpoint} and send it to {@link Observer}s and
		 * {@link Consumer}s.
		 */
		@OnMessage
		public void onMessage(final String message) {
			final T entity = gson.fromJson(message, tClass);
			observers.forEach(observer -> observer.onNext(entity));
		}
	}

}
