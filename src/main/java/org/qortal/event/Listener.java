package org.qortal.event;

@FunctionalInterface
public interface Listener {
	void listen(Event event);
}
