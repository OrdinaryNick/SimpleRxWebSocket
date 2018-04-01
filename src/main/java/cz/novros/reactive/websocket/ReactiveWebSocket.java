package cz.novros.reactive.websocket;

import com.google.gson.Gson;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

/**
 * Top class for all reactive web socket implementations.
 *
 * @param <T> Type of class which is expected in messages.
 */
public abstract class ReactiveWebSocket<T> extends Observable<T> implements Observer<T> {
	/**
	 * Json class for decoding and encoding entities.
	 */
	protected static final Gson gson = new Gson();
	/**
	 * Expected class in {@link OnMessage}.
	 */
	protected final Class<T> tClass;
	/**
	 * Disposables of {@link Observable}s
	 */
	protected final Collection<Disposable> disposables = new HashSet<>();
	/**
	 * {@link Observer} subscribed to this instance.
	 */
	protected final Collection<Observer<? super T>> observers = new HashSet<>();

	/**
	 * Prepare web socket for sending or serving data.
	 *
	 * @param tClass Class which will expected in communication.
	 */
	protected ReactiveWebSocket(final Class<T> tClass) {
		this.tClass = tClass;
	}

	/**
	 * Subscribe observer to web socket.
	 *
	 * @param observer Observer, which will be subscribed.
	 */
	@Override
	protected void subscribeActual(final Observer<? super T> observer) {
		observers.add(observer);
		observer.onSubscribe(new WebSocketDisposable(observer));
	}

	/**
	 * Subscribe this web socket to Observable and stores disposable.
	 *
	 * @param disposable Disposable of subscription.
	 */
	public void onSubscribe(final Disposable disposable) {
		disposables.add(disposable);
	}

	/**
	 * Do nothing.
	 *
	 * @param throwable Error which occurs.
	 */
	public void onError(final Throwable throwable) {
		// Empty
	}

	/**
	 * Do nothing.
	 */
	public void onComplete() {
		// Empty
	}

	/**
	 * Sent entity to {@link Session}.
	 *
	 * @param entity  Entity which will be sent.
	 * @param session Into this session will be sent.
	 */
	protected void sentEntity(final T entity, final Session session) {
		try {
			session.getBasicRemote().sendText(gson.toJson(entity));
		} catch (final IOException e) {
			// TODO Do something with exception
		}
	}

	/**
	 * {@link Disposable} for subscribed {@link Observer}s.
	 */
	private final class WebSocketDisposable implements Disposable {

		/**
		 * Observer, which holds this {@link Disposable}.
		 */
		private final Observer observer;

		private WebSocketDisposable(final Observer observer) {
			this.observer = observer;
		}

		/**
		 * Dispose observer from web socket.
		 */
		@Override
		public void dispose() {
			observers.remove(observer);
		}

		/**
		 * @return True if observer is disposed from web socket.
		 */
		@Override
		public boolean isDisposed() {
			return !observers.contains(observer);
		}
	}
}
