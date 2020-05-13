package com.ordinarynick.reactive.websocket;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Mocked {@link ContainerProvider} to be able simulate web socket server. At default it will create {@link Session},
 * which is opened and secured. Also it can simulate closing of {@link Session}, simulate sending messages through
 * websocket.
 */
public class MockContainerProvider extends ContainerProvider {

	private static Session session = null;
	private static Subject<String> serverReceivedData = null;
	private static final WebSocketContainer container = mock(WebSocketContainer.class);

	private static final ArgumentCaptor<Object> clientEndpointCaptor = ArgumentCaptor.forClass(Object.class);

	/**
	 * Resets all mocks on this class.
	 */
	public static void reset() {
		Mockito.reset(container);

		serverReceivedData = BehaviorSubject.create();
	}

	/**
	 * Return actual mocked {@link Session}.
	 *
	 * @return Mocked {@link Session} if is there any, otherwise {@code null}.
	 */
	public static Session getSession() {
		return session;
	}

	/**
	 * Mock {@link Session} with default mock. Which is that {@link Session} is open and secured and received data from
	 * client is available in {@link #getServerReceivedData()}-
	 */
	public static void mockSession() {
		try {
			mockSession(getDefaultMockSession());
		} catch (final IOException exception) {
			throw new IllegalStateException("Could not mock session!");
		}
	}

	/**
	 * Mock {@link Session} with given session..
	 *
	 * @param session {@link Session} which will be used.
	 */
	public static void mockSession(final Session session) {
		MockContainerProvider.session = session;
		mockSession(doReturn(session));
	}

	/**
	 * Mock {@link Session} with given {@link Stubber} (something like {@link Mockito#doReturn(Object)} or {@link
	 * Mockito#doAnswer(Answer)}).
	 *
	 * @param stubber {@link Stubber} which will be used.
	 */
	public static void mockSession(final Stubber stubber) {
		try {
			stubber.when(container).connectToServer(clientEndpointCaptor.capture(), any(URI.class));
		} catch (final DeploymentException | IOException ignored) {
		}
	}

	/**
	 * Return {@link Observable} with all received data thought {@link Session}.
	 *
	 * @return {@link Observable} which contains all received data.
	 */
	public static Observable<String> getServerReceivedData() {
		return serverReceivedData;
	}

	/**
	 * Sends message through {@link com.ordinarynick.reactive.websocket.ReactiveClientWebSocket.ClientSocket}.
	 *
	 * @param message Message which will be sent.
	 */
	public static void sendMessage(final String message) {
		getClientSocket().onMessage(message);
	}

	/**
	 * Sends error through {@link com.ordinarynick.reactive.websocket.ReactiveClientWebSocket.ClientSocket}.
	 */
	public static void sendError(final Throwable throwable) {
		getClientSocket().onError(throwable);
	}

	/**
	 * Sends close through {@link com.ordinarynick.reactive.websocket.ReactiveClientWebSocket.ClientSocket}.
	 */
	public static void sendClose() {
		getClientSocket().onClose();
	}

	@Override
	protected WebSocketContainer getContainer() {
		return container;
	}

	private static Session getDefaultMockSession() throws IOException {
		final Session session = mock(Session.class);

		final RemoteEndpoint.Basic basicRemote = mockBasicRemote();

		doReturn(true).when(session).isOpen();
		doAnswer(invocationOnMock -> {
			doReturn(false).when(session).isOpen();
			return null;
		}).when(session).close();
		doReturn(true).when(session).isSecure();
		doReturn(basicRemote).when(session).getBasicRemote();

		return session;
	}

	private static RemoteEndpoint.Basic mockBasicRemote() throws IOException {
		final RemoteEndpoint.Basic basicRemote = mock(RemoteEndpoint.Basic.class);

		final ArgumentCaptor<String> dataCaptor = ArgumentCaptor.forClass(String.class);
		doAnswer(invocationOnMock -> {
			serverReceivedData.onNext(dataCaptor.getValue());
			return null;
		}).when(basicRemote).sendText(dataCaptor.capture());

		return basicRemote;
	}

	private static ReactiveClientWebSocket.ClientSocket getClientSocket() {
		return (ReactiveClientWebSocket.ClientSocket) clientEndpointCaptor.getValue();
	}
}
