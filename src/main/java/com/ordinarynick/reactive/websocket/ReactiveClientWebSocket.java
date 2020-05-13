package com.ordinarynick.reactive.websocket;

import com.ordinarynick.reactive.websocket.exception.ReactiveWebSocketException;
import io.reactivex.Observer;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
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

	// region Observer - methods

	@Override
	protected boolean isConnectionOpen() {
		return getConnection().isPresent();
	}

	@Override
	protected void sendData(final T t) {
		getConnection().ifPresent(wsSession -> sentEntity(t, wsSession));
	}
	// endregion Observer - methods

	// region Observable - methods
	@Override
	protected void subscribeActual(final Observer<? super T> observer) {
		super.subscribeActual(observer);

		// Try connect if not connected already.
		try {
			connect();
		} catch (final ReactiveWebSocketException exception) {
			terminalObject.set(exception);
			subscribers.forEach(subscriber -> subscriber.onError(exception));
		}
	}
	// endregion Observable - methods

	// region Common - methods

	/**
	 * Returns opened {@link Session}.
	 *
	 * @return {@link Optional} with open and connected {@link Session}, otherwise {@link Optional#empty()};
	 */
	private Optional<Session> getConnection() {
		try {
			connect(); // Try connect if not connected.
			return Optional.ofNullable(session.get()).filter(Session::isOpen);

		} catch (final ReactiveWebSocketException exception) {
			RxJavaPlugins.onError(exception);
			return Optional.empty();
		}
	}

	/**
	 * Connects to {@link javax.websocket.server.ServerEndpoint}, if is not already connected and returns open {@link
	 * Session}.
	 *
	 * @throws ReactiveWebSocketException is thrown when was problem with connecting to server websocket.
	 * @since reactive.websocket-1.0.0
	 */
	private void connect() throws ReactiveWebSocketException {
		if (session.get() == null) {
			try {
				// TODO Try using ClientEndpoint.class instead of object.
				session.set(webSocketContainer.connectToServer(clientSocket, uri));
			} catch (final DeploymentException | IOException exception) {
				throw new ReactiveWebSocketException("Could not connect to websocket server!", exception);
			}
		}
	}

	@Override
	protected void closeConnection() throws IOException {
		if (session.get() != null) {
			session.getAndSet(null).close();
		}
	}

	// endregion Common - methods

	/**
	 * Helper class for connecting to {@link javax.websocket.server.ServerEndpoint ServerEndpoint} on {@link #uri} and
	 * defining what to do when client receives message from {@link javax.websocket.server.ServerEndpoint
	 * ServerEndpoint}.
	 *
	 * @since reactive.websocket-1.0.0
	 */
	@ClientEndpoint
	public class ClientSocket {

		/**
		 * Receive message from {@link javax.websocket.server.ServerEndpoint ServerEndpoint} and send it to all
		 * subscribed {@link Observer}s and all {@link Consumer}s.
		 *
		 * @since reactive.websocket-1.0.0
		 */
		@OnMessage
		public void onMessage(final String message) {
			final T entity = gson.fromJson(message, tClass);
			subscribers.forEach(subscriber -> subscriber.onNext(entity));
		}

		/**
		 * Is called when {@link Throwable} appears during communication with {@link
		 * javax.websocket.server.ServerEndpoint ServerEndpoint},
		 *
		 * @param error {@link Throwable}
		 */
		@OnError
		public void onError(final Throwable error) {
			terminalObject.set(error);
			subscribers.forEach(subscriber -> subscriber.onError(error));
		}

		/**
		 * Is called when {@link javax.websocket.server.ServerEndpoint ServerEndpoint} closed websocket connection.
		 *
		 * @since reactive.websocket-1.0.0
		 */
		@OnClose
		public void onClose() {
			terminalObject.set(true);
			subscribers.forEach(WebSocketDisposable::onComplete);
		}
	}

}
