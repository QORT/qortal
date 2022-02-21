package org.qortal.event;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum EventBus {
	INSTANCE;

	private static final Logger LOGGER = LogManager.getLogger(EventBus.class);

	private static final List<Listener> LISTENERS = new ArrayList<>();

	public void addListener(Listener newListener) {
		synchronized (LISTENERS) {
			LISTENERS.add(newListener);
		}
	}

	public void removeListener(Listener listener) {
		synchronized (LISTENERS) {
			LISTENERS.remove(listener);
		}
	}

	/**
	 * <b>WARNING:</b> before calling this method,
	 * make sure current thread's repository session
	 * holds no locks, e.g. by calling
	 * <tt>repository.saveChanges()</tt> or
	 * <tt>repository.discardChanges()</tt>.
	 * <p>
	 * This is because event listeners might open a new
	 * repository session which will deadlock HSQLDB
	 * if it tries to CHECKPOINT.
	 * <p>
	 * The HSQLDB deadlock path is:
	 * <ul>
	 * <li>write-log <tt>blockchain.log</tt> has grown past CHECKPOINT threshold (50MB)</li>
	 * <li>alternatively, another thread has explicitly requested CHECKPOINT</li>
	 * <li>HSQLDB won't begin CHECKPOINT until all pending (SQL) transactions are committed or rolled back</li>
	 * <li>Same thread calls <tt>EventBus.INSTANCE.notify()</tt> <i>before</i> (SQL) transaction closed</li>
	 * <li>EventBus listener (same thread) requests a new repository session via <tt>RepositoryManager.getRepository()</tt></li>
	 * <li>New repository sessions are blocked pending completion of CHECKPOINT</li>
	 * <li>Caller is blocked so never has a chance to close (SQL) transaction - hence deadlock</li>
	 * </ul>
	 */
	public void notify(Event event) {
		List<Listener> clonedListeners;

		synchronized (LISTENERS) {
			clonedListeners = new ArrayList<>(LISTENERS);
		}

		for (Listener listener : clonedListeners)
			try {
				listener.listen(event);
			} catch (Exception e) {
				// We don't want one listener to break other listeners, or caller
				LOGGER.warn(() -> String.format("Caught %s from a listener processing %s", e.getClass().getSimpleName(), event.getClass().getSimpleName()), e);
			}
	}
}
