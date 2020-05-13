package com.ordinarynick.reactive.websocket;

import com.google.gson.Gson;
import com.ordinarynick.reactive.websocket.exception.ReactiveWebSocketException;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ReactiveClientWebSocketTest {

	private static final String URL = "ws://some-url.com";
	private static final Gson gson = new Gson();

	private ReactiveClientWebSocket<String> clientWebSocket;

	@BeforeEach
	void beforeEachTest() {
		RxJavaPlugins.setErrorHandler(null);

		MockContainerProvider.reset();
		MockContainerProvider.mockSession();

		clientWebSocket = new ReactiveClientWebSocket<>(URL, String.class);
	}

	// region Tests - Common methods
	@Test
	void connect() {
		// Test
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Assert
		testObserver.assertSubscribed();
		testObserver.assertNotComplete();
		testObserver.assertNoErrors();
		testObserver.assertNotTerminated();
		testObserver.assertNoValues();
	}

	@Test
	void connectWithError() {
		// Prepare
		final IOException ioException = new IOException("Some IO exception");

		// Behaviour
		MockContainerProvider.mockSession(doThrow(ioException));

		// Test
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Assert
		testObserver.assertSubscribed();
		testObserver.assertNotComplete();
		testObserver.assertError(ReactiveWebSocketException.class);
		testObserver.assertErrorMessage("Could not connect to websocket server!");
		testObserver.assertTerminated();
		testObserver.assertNoValues();
	}

	@Test
	void close() throws Exception {
		// Prepare
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		clientWebSocket.close();

		// Assert
		final Session session = MockContainerProvider.getSession();
		verify(session, atLeastOnce()).close();

		testObserver.assertComplete();
		testObserver.assertNoErrors();
		testObserver.assertTerminated();
		testObserver.assertNoValues();
	}

	@Test
	void closeWWithError() throws IOException {
		// Prepare
		final IOException expectedError = new IOException("Could not close connection!");

		// Behaviour
		final Session session = mock(Session.class);
		doReturn(true).when(session).isOpen();
		doThrow(expectedError).when(session).close();
		MockContainerProvider.mockSession(session);

		// Test
		final TestObserver<String> testObserver = clientWebSocket.test();
		final IOException exception = assertThrows(IOException.class, () -> clientWebSocket.close());

		// Assert
		assertEquals(expectedError.getMessage(), exception.getMessage());

		verify(session, atLeastOnce()).close();

		testObserver.assertComplete();
		testObserver.assertNoErrors();
		testObserver.assertTerminated();
		testObserver.assertNoValues();
	}
	// endregion Tests - Common methods

	// region Tests - Observer methods
	@Test
	void isConnectionOpen_connect() {
		// Prepare
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		final boolean result = clientWebSocket.isConnectionOpen();

		// Assert
		assertTrue(result);
	}

	@Test
	void isConnectionOpen_connectWithError() {
		// Prepare
		final IOException ioException = new IOException("Some IO exception");

		// Behaviour
		MockContainerProvider.mockSession(doThrow(ioException));

		// Test
		final TestObserver<String> testObserver = clientWebSocket.test();
		final boolean result = clientWebSocket.isConnectionOpen();

		// Assert
		assertFalse(result);
	}

	@Test
	void isConnectionOpen_close() throws Exception {
		// Prepare
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		clientWebSocket.close();

		// Assert
		assertFalse(clientWebSocket.isConnectionOpen());
	}

	@Test
	void isConnectionOpen_closeWithError() throws IOException {
		// Prepare
		final IOException expectedError = new IOException("Could not close connection!");

		// Behaviour
		final Session session = mock(Session.class);
		doReturn(true).when(session).isOpen();
		doThrow(expectedError).when(session).close();
		MockContainerProvider.mockSession(session);

		// Test
		final TestObserver<String> testObserver = clientWebSocket.test();
		final IOException exception = assertThrows(IOException.class, () -> clientWebSocket.close());

		// Assert
		assertTrue(clientWebSocket.isConnectionOpen());
	}

	@Test
	void onNext() {
		// Prepare
		final String data = "Some sent data.";
		final TestObserver<String> receivedDataObserver = MockContainerProvider.getServerReceivedData().test();

		// Test
		clientWebSocket.onNext(data);

		// Assert
		receivedDataObserver.assertValueCount(1);
		receivedDataObserver.assertValue(gson.toJson(data));
	}

	@Test
	void testOnNext_withExceptionDuringConnection() {
		// Behaviour
		final TestObserver<String> receivedDataObserver = MockContainerProvider.getServerReceivedData().test();
		final AtomicReference<Throwable> thrownError = new AtomicReference<>();
		RxJavaPlugins.setErrorHandler(thrownError::set);
		final IOException ioException = new IOException("Error during connection");
		final ReactiveWebSocketException expectedError =
				new ReactiveWebSocketException("Could not connect to websocket server!", ioException);

		// Behaviour
		MockContainerProvider.mockSession(doThrow(ioException));

		// Test
		clientWebSocket.onNext("some data");

		// Assert
		receivedDataObserver.assertNoValues();

		assertNotNull(thrownError.get());
		assertEquals(expectedError.getClass(), thrownError.get().getCause().getClass());
		assertEquals(expectedError.getMessage(), thrownError.get().getCause().getMessage());
	}

	@Test
	void testOnNext_withExceptionDuringSendingData() throws IOException {
		// Prepare
		final TestObserver<String> receivedDataObserver = MockContainerProvider.getServerReceivedData().test();
		final AtomicReference<Throwable> thrownError = new AtomicReference<>();
		RxJavaPlugins.setErrorHandler(thrownError::set);
		final IOException expectedError = new IOException("Error during sent");

		// Behavior
		final RemoteEndpoint.Basic basicRemote = mock(RemoteEndpoint.Basic.class);
		doThrow(expectedError).when(basicRemote).sendText(any());
		final Session session = mock(Session.class);
		doReturn(basicRemote).when(session).getBasicRemote();
		doReturn(true).when(session).isOpen();
		MockContainerProvider.mockSession(session);

		// Test
		clientWebSocket.onNext("some data");

		// Assert
		receivedDataObserver.assertNoValues();

		assertNotNull(thrownError.get());
		assertEquals(expectedError, thrownError.get().getCause());
	}

	@Test
	void onSubscribe() {
		// Prepare
		final String[] data = {"some", "data", "here"};
		final Observable<String> observable = Observable.fromArray(data);
		final TestObserver<String> receivedDataObserver = MockContainerProvider.getServerReceivedData().test();

		// Test
		observable.subscribe(clientWebSocket);

		// Assert
		receivedDataObserver.assertValueCount(data.length);
		receivedDataObserver.assertValues(Stream.of(data).map(gson::toJson).toArray(String[]::new));
	}

	@Test
	void onSubscribe_withExceptionDuringConnection() {
		// Behaviour
		final TestObserver<String> receivedDataObserver = MockContainerProvider.getServerReceivedData().test();
		final AtomicReference<Throwable> thrownError = new AtomicReference<>();
		RxJavaPlugins.setErrorHandler(thrownError::set);
		final IOException ioException = new IOException("Error during connection");
		final ReactiveWebSocketException expectedError =
				new ReactiveWebSocketException("Could not connect to websocket server!", ioException);

		final Observable<String> observable = Observable.fromArray("some", "data");

		// Behaviour
		MockContainerProvider.mockSession(doThrow(ioException));

		// Test
		observable.subscribe(clientWebSocket);

		// Assert
		receivedDataObserver.assertNoValues();

		assertNotNull(thrownError.get());
		assertEquals(expectedError.getClass(), thrownError.get().getCause().getClass());
		assertEquals(expectedError.getMessage(), thrownError.get().getCause().getMessage());
	}

	// endregion Tests - Observer methods

	// region Tests - Observable methods
	@Test
	void subscribeActual() {
		// Test
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Assert
		testObserver.assertSubscribed();
		testObserver.assertNotComplete();
		testObserver.assertNotTerminated();
		testObserver.assertNoErrors();
		testObserver.assertNoValues();
	}

	@Test
	void subscribeActual_connectionWithError() {
		// Behaviour
		MockContainerProvider.mockSession(doThrow(new IOException("Error during connection.")));

		// Test
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Assert
		testObserver.assertSubscribed();
		testObserver.assertNotComplete();
		testObserver.assertTerminated();
		testObserver.assertError(ReactiveWebSocketException.class);
		testObserver.assertErrorMessage("Could not connect to websocket server!");
		testObserver.assertNoValues();
	}

	@Test
	void onMessage() {
		// Prepare
		final String message = "Some message.";
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		MockContainerProvider.sendMessage("\"" + message + "\"");

		// Assert
		testObserver.assertNotComplete();
		testObserver.assertNotTerminated();
		testObserver.assertNoErrors();
		testObserver.assertValue(message);
	}

	@Test
	void onError() {
		// Prepare
		final IOException error = new IOException("Some IO error.");
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		MockContainerProvider.sendError(error);

		// Assert
		testObserver.assertNotComplete();
		testObserver.assertTerminated();
		testObserver.assertError(error.getClass());
		testObserver.assertErrorMessage(error.getMessage());
		testObserver.assertNoValues();
	}

	@Test
	void onClose() {
		// Prepare
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		MockContainerProvider.sendClose();

		// Assert
		testObserver.assertComplete();
		testObserver.assertTerminated();
		testObserver.assertNoErrors();
		testObserver.assertNoValues();
	}

	@Test
	void hasObservers_notSubscribed() {
		// Test
		final boolean result = clientWebSocket.hasObservers();

		// Assert
		assertFalse(result);
	}

	@Test
	void hasObservers_subscribed() {
		// Prepare
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		final boolean result = clientWebSocket.hasObservers();

		// Assert
		assertTrue(result);
	}

	@Test
	void hasComplete_openConnection() {
		// Prepare
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		final boolean result = clientWebSocket.hasComplete();

		// Assert
		assertFalse(result);
	}

	@Test
	void hasComplete_close() throws IOException {
		// Prepare
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		clientWebSocket.close();
		final boolean result = clientWebSocket.hasComplete();

		// Assert
		assertTrue(result);
	}

	@Test
	void hasComplete_onClose() {
		// Prepare
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		MockContainerProvider.sendClose();
		final boolean result = clientWebSocket.hasComplete();

		// Assert
		assertTrue(result);
	}

	@Test
	void hasThrowable_noError() {
		// Test
		final boolean result = clientWebSocket.hasThrowable();

		// Assert
		assertFalse(result);
	}

	@Test
	void hasThrowable_errorDuringConnection() {
		// Behaviour
		MockContainerProvider.mockSession(doThrow(new IOException("Error during connection.")));

		// Test
		final TestObserver<String> testObserver = clientWebSocket.test();
		final boolean result = clientWebSocket.hasThrowable();

		// Assert
		assertTrue(result);
	}

	@Test
	void hasThrowable_onError() {
		// Prepare
		final IOException expectedError = new IOException("Some IO error.");
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		MockContainerProvider.sendError(expectedError);
		final boolean result = clientWebSocket.hasThrowable();

		// Assert
		assertTrue(result);
	}

	@Test
	void getThrowable_noError() {
		// Test
		final Throwable result = clientWebSocket.getThrowable();

		// Assert
		assertNull(result);
	}

	@Test
	void getThrowable_errorDuringConnection() {
		final IOException ioException = new IOException("Error during connection.");
		final ReactiveWebSocketException expectedError =
				new ReactiveWebSocketException("Could not connect to websocket server!", ioException);

		// Behaviour
		MockContainerProvider.mockSession(doThrow(ioException));

		// Test
		final TestObserver<String> testObserver = clientWebSocket.test();
		final Throwable result = clientWebSocket.getThrowable();

		// Assert
		assertNotNull(result);
		assertEquals(expectedError.getClass(), result.getClass());
		assertEquals(expectedError.getMessage(), result.getMessage());
	}

	@Test
	void getThrowable_onError() {
		// Prepare
		final IOException expectedError = new IOException("Some IO error.");
		final TestObserver<String> testObserver = clientWebSocket.test();

		// Test
		MockContainerProvider.sendError(expectedError);
		final Throwable result = clientWebSocket.getThrowable();

		// Assert
		assertNotNull(result);
		assertEquals(expectedError.getClass(), result.getClass());
		assertEquals(expectedError.getMessage(), result.getMessage());
	}
	// endregion Tests - Observable methods
}
