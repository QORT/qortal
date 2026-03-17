package org.qortal.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {

	private final String name;
	private final AtomicInteger threadNumber = new AtomicInteger(1);
	private final int priority;

	public NamedThreadFactory(String name, int priority) {
		this.name = name;
		this.priority = priority;
	}

	@Override
	public Thread newThread(Runnable runnable) {
		Thread thread = Executors.defaultThreadFactory().newThread(runnable);
		thread.setName(this.name + "-" + this.threadNumber.getAndIncrement());
		thread.setPriority(this.priority);

		return thread;
	}

}
