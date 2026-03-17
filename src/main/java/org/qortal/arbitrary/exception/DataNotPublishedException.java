package org.qortal.arbitrary.exception;

import org.qortal.repository.DataException;

public class DataNotPublishedException extends DataException {

	public DataNotPublishedException() {
	}

	public DataNotPublishedException(String message) {
		super(message);
	}

	public DataNotPublishedException(String message, Throwable cause) {
		super(message, cause);
	}

	public DataNotPublishedException(Throwable cause) {
		super(cause);
	}

}
