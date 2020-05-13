package com.ordinarynick.reactive.websocket.exception;

/**
 * Common exception when was problem with something in this lib.
 */
public class ReactiveWebSocketException extends Exception {

	/**
	 * Creates this exception with given data.
	 *
	 * @param message Message what happened.
	 * @param cause   What caused this exception.
	 */
	public ReactiveWebSocketException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
