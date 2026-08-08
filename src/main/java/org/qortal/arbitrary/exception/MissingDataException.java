package org.qortal.arbitrary.exception;

public class MissingDataException extends Exception {

	public MissingDataException() {
	}

	public MissingDataException(String message) {
		super(message);
	}

	public MissingDataException(String message, Throwable cause) {
		super(message, cause);
	}

	public MissingDataException(Throwable cause) {
		super(cause);
	}

}
