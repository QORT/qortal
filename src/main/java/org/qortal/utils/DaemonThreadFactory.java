package org.qortal.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DaemonThreadFactory implements ThreadFactory {

	private final String name;
	private final AtomicInteger threadNumber = new AtomicInteger(1);
	private int priority = Thread.NORM_PRIORITY;

	public DaemonThreadFactory(String name, int priority) {
		this.name = name;
		this.priority = priority;
	}

	public DaemonThreadFactory(int priority) {
		this(null, priority);;
	}

	@Override
	public Thread newThread(Runnable runnable) {
		Thread thread = Executors.defaultThreadFactory().newThread(runnable);
		thread.setDaemon(true);
		thread.setPriority(this.priority);

		if (this.name != null)
			thread.setName(this.name + "-" + this.threadNumber.getAndIncrement());

		return thread;
	}

}
