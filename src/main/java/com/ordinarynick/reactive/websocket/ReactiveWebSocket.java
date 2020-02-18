package com.ordinarynick.reactive.websocket;

import com.google.gson.Gson;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.Subject;

import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

/**
 * Base class for server websocket endpoint and client websocket connection. It contains common things for both like JSON
 * mapping, holding {@link Disposable}s, subscribed {@link Observer}s, etc.
 *
 * @param <T> Type of {@link Class} which is expected to send in websocket messages.
 *
 * @author Ordinary Nick
 * @version 1.0.0
 * @since reactive.websocket-1.0.0
 */
public abstract class ReactiveWebSocket<T> extends Subject<T> {

	/** JSON mapper for decoding and encoding JSON. */
	protected static final Gson gson = new Gson();

	/** Expected {@link Class} in {@link OnMessage}. */
	protected final Class<T> tClass;

	/** All {@link Disposable} of subscription this websocket to other {@link Observable}s. */
	protected final Collection<Disposable> disposables = new HashSet<>();

	/** If this subject received complete event */
	private boolean complete = false;

	/** If this subject has received {@link Throwable}. */
	private Throwable throwable = null;

	/** {@link Observer}s subscribed to this websocket instance. */
	protected final Collection<Observer<? super T>> observers = new HashSet<>();

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

	/**
	 * Subscribe {@link Observer} to this websocket instance.
	 *
	 * @param observer {@link Observer} which will be subscribed.
	 */
	@Override
	protected void subscribeActual(final Observer<? super T> observer) {
		observers.add(observer);
		observer.onSubscribe(new WebSocketDisposable(observer));
	}

	/**
	 * Subscribes this websocket to {@link Observable} and stores {@link Disposable}.
	 *
	 * @param disposable Disposable of subscription.
	 */
	public void onSubscribe(final Disposable disposable) {
		disposables.add(disposable);
	}

	/**
	 * Default implementation of {@link Observer#onError(Throwable)} method. If you override this method, do not forget to
	 * call {@code super.onError(throwable)}.
	 *
	 * @param throwable {@link Throwable} which occurs in subscribed {@link Observable}.
	 */
	public void onError(final Throwable throwable) {
		this.throwable = throwable;
	}

	/**
	 * Default implementation of {@link Observer#onComplete()} method. If you override this method, do not forget to call
	 * {@code super.onComplete()}.
	 */
	public void onComplete() {
		complete = true;
	}

	/**
	 * Sent entity of type {@link T} to {@link Session}.
	 *
	 * @param entity  Entity (type {@link T}) which will be sent into {@link Session}.
	 * @param session Into this {@link Session} will be entity sent.
	 *
	 * @since reactive.websocket-1.0.0
	 */
	protected void sentEntity(final T entity, final Session session) {
		try {
			session.getBasicRemote().sendText(gson.toJson(entity));
		} catch (final IOException e) {
			// FIXME Do something with exception (log or sent as error or something else)
		}
	}

	@Override
	public boolean hasObservers() {
		return observers.isEmpty();
	}

	@Override
	public boolean hasThrowable() {
		return throwable != null;
	}

	@Override
	public boolean hasComplete() {
		return complete;
	}

	@Override
	public Throwable getThrowable() {
		return throwable;
	}

	/**
	 * {@link Disposable} for subscribed {@link Observer}s to this {@link ReactiveWebSocket}.
	 *
	 * @version 1.0.0
	 * @since reactive.websocket-1.0.0
	 */
	private final class WebSocketDisposable implements Disposable {

		/**
		 * {@link Observer} which holds this {@link Disposable}.
		 */
		private final Observer<?> observer;

		private WebSocketDisposable(final Observer<?> observer) {
			this.observer = observer;
		}

		@Override
		public void dispose() {
			observers.remove(observer);
		}

		@Override
		public boolean isDisposed() {
			return !observers.contains(observer);
		}
	}
}
