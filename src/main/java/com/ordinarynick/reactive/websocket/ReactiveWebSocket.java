package com.ordinarynick.reactive.websocket;

import com.google.gson.Gson;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subjects.Subject;

import javax.websocket.Session;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for server websocket endpoint and client websocket connection. It contains common things for both like
 * JSON mapping, holding {@link Disposable}s, subscribed {@link Observer}s, etc.
 * <p>
 * This class implements {@link Subject} in way that it behaves like this:
 * <ul>
 * <li> {@link Observable} contains data received through websocket.</li>
 * <li> {@link Observer} receives data to be sent through websocket.</li>
 * </ul>
 *
 * @param <T> Type of {@link Class} which is expected to send in websocket messages.
 *
 * @author Ordinary Nick
 * @version 1.0.0
 * @since reactive.websocket-1.0.0
 */
// TODO Think about this: Can be this class separated to Observable and Observer?
public abstract class ReactiveWebSocket<T> extends Subject<T> {

	// region Common - attributes

	/** Expected {@link Class} in {@link javax.websocket.OnMessage}. */
	protected final Class<T> tClass;

	// endregion Common - attributes

	// region Observer - attributes

	/** JSON mapper for decoding and encoding JSON. */
	// TODO Maybe use some object mapper interface? (only if exists)
	protected static final Gson gson = new Gson();

	// endregion Observer - attributes

	// region Observable - attributes

	/** All {@link Disposable}s of subscribed {@link Observer}s to this {@link ReactiveWebSocket}. */
	protected final Collection<WebSocketDisposable<T>> subscribers = new CopyOnWriteArraySet<>();

	/** Terminal object of this {@link Subject}. It can be complete (boolean=true) or error (throwable). */
	protected AtomicReference<Object> terminalObject = new AtomicReference<>();

	// endregion Observable - attributes

	/**
	 * Prepare websocket for sending or serving data.
	 *
	 * @param tClass {@link Class} which is expected in the websocket communication.
	 *
	 * @since reactive.websocket-1.0.0
	 */
	protected ReactiveWebSocket(final Class<T> tClass) {
		this.tClass = tClass;
	}

	// region Observer - methods

	public void onSubscribe(final Disposable disposable) {
		// If connection is lost, dispose this observer.
		if (!isConnectionOpen()) {
			disposable.dispose();
		}
	}

	@Override
	public void onNext(final T t) {
		if (isConnectionOpen()) {
			sendData(t);
		}
	}

	public void onError(final Throwable throwable) {
		// It makes no sense to sent it through websocket, So, nothing to do here.
	}

	public void onComplete() {
		// We want be connected until connection is closed. So, nothing to do here.
	}

	/**
	 * Checks if connection with other side of websocket is open.
	 *
	 * @return True if connection is alive. Otherwise it returns false.
	 */
	protected abstract boolean isConnectionOpen();

	/**
	 * Sends data through websocket.
	 *
	 * @param t Data to be send.
	 */
	protected abstract void sendData(final T t);

	/**
	 * Send data (entity of type {@link T} through websocket (via {@link Session}).
	 *
	 * @param entity  Entity (type {@link T}) which will be sent into {@link Session}.
	 * @param session Into this {@link Session} will be entity sent.
	 *
	 * @since reactive.websocket-1.0.0
	 */
	protected void sentEntity(final T entity, final Session session) {
		try {
			session.getBasicRemote().sendText(gson.toJson(entity));
		} catch (final IOException exception) {
			RxJavaPlugins.onError(exception);
		}
	}
	// endregion Observer - methods

	// region Observable - methods

	@Override
	protected void subscribeActual(final Observer<? super T> observer) {
		final WebSocketDisposable<T> disposable = new WebSocketDisposable<>(observer, this);
		observer.onSubscribe(disposable);

		try {
			if (subscribers.add(disposable)) {
				// Observer is already between subscribers.
				if (disposable.isDisposed()) {
					subscribers.remove(disposable);
				}
			} else {
				if (hasComplete()) {
					observer.onComplete();
				}
			}
		} catch (final Exception exception) {
			observer.onError(exception);
		}
	}

	@Override
	public boolean hasObservers() {
		return !subscribers.isEmpty();
	}

	@Override
	public boolean hasThrowable() {
		return terminalObject.get() instanceof Throwable;
	}

	@Override
	public boolean hasComplete() {
		return terminalObject.get() instanceof Boolean && (Boolean) terminalObject.get();
	}

	@Override
	public Throwable getThrowable() {
		return hasThrowable() ? (Throwable) terminalObject.get() : null;
	}

	private void remove(final WebSocketDisposable<T> disposable) {
		subscribers.remove(disposable);
	}

	// endregion Observable - methods

	// region Common - methods

	/**
	 * Close websocket connection ({@link Session}, release all resources and unsubscribe all subscribers,
	 *
	 * @throws IOException is thrown when was problem with closing connection.
	 * @since reactive.websocket-1.0.0
	 */
	public void close() throws IOException {
		terminalObject.set(true);
		subscribers.forEach(WebSocketDisposable::onComplete);
		subscribers.forEach(WebSocketDisposable::dispose);
		closeConnection();
	}

	/**
	 * Close websocket connection ({@link Session}.
	 *
	 * @throws IOException is thrown when was problem with closing connection.
	 * @since reactive.websocket-1.0.0
	 */
	protected abstract void closeConnection() throws IOException;
	// endregion Common - methods

	/**
	 * {@link Disposable} for subscribed {@link Observer}s to this {@link ReactiveWebSocket}.
	 *
	 * @version 1.0.0
	 * @since reactive.websocket-1.0.0
	 */
	static final class WebSocketDisposable<T> extends AtomicBoolean implements Disposable {

		/** {@link Observer} which holds this {@link Disposable}. */
		private final Observer<? super T> observer;
		/** The subject state. */
		private final ReactiveWebSocket<T> observable;

		private WebSocketDisposable(final Observer<? super T> observer, final ReactiveWebSocket<T> observable) {
			this.observer = observer;
			this.observable = observable;
		}

		@Override
		public void dispose() {
			if (compareAndSet(false, true)) {
				observable.remove(this);
			}
		}

		@Override
		public boolean isDisposed() {
			return get();
		}

		public void onNext(final T t) {
			if (!get()) {
				observer.onNext(t);
			}
		}

		public void onError(final Throwable t) {
			if (get()) {
				RxJavaPlugins.onError(t);
			} else {
				observer.onError(t);
			}
		}

		public void onComplete() {
			if (!get()) {
				observer.onComplete();
			}
		}
	}
}
