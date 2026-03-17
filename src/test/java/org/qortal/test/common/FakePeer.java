package org.qortal.test.common;

import org.qortal.network.message.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class FakePeer implements Runnable {
	private static final long DEFAULT_BROADCAST_INTERVAL = 60 * 1000;

	protected final int id;
	protected final long startedWhen;

	protected final LinkedBlockingQueue<PeerMessage> pendingMessages;
	protected final List<FakePeer> peers;

	private long nextBroadcast;

	public FakePeer(int id) {
		this.id = id;
		this.startedWhen = System.currentTimeMillis();

		this.pendingMessages = new LinkedBlockingQueue<>();
		this.peers = Collections.synchronizedList(new ArrayList<>());

		this.nextBroadcast = this.startedWhen;
	}

	protected static long getBroadcastInterval() {
		return DEFAULT_BROADCAST_INTERVAL;
	}

	public int getId() {
		return this.id;
	}

	public void run() {
		try {
			while (true) {
				PeerMessage peerMessage = this.pendingMessages.poll(1, TimeUnit.SECONDS);

				if (peerMessage != null)
					processMessage(peerMessage.peer, peerMessage.message);
				else
					idleTasksCheck();
			}
		} catch (InterruptedException e) {
			// fall-through to exit
		}
	}

	protected abstract void processMessage(FakePeer peer, Message message) throws InterruptedException;

	protected void idleTasksCheck() {
		final long now = System.currentTimeMillis();

		if (now < this.nextBroadcast)
			return;

		this.nextBroadcast = now + getBroadcastInterval();

		this.performIdleTasks();
	}

	protected abstract void performIdleTasks();

	public void connect(FakePeer peer) {
		synchronized (this.peers) {
			if (this.peers.contains(peer))
				return;

			this.peers.add(peer);
		}
	}

	protected void send(FakePeer otherPeer, Message message) {
		otherPeer.receive(this, message);
	}

	protected void broadcast(Message message) {
		synchronized (this.peers) {
			for (int i = 0; i < this.peers.size(); ++i)
				this.send(this.peers.get(i), message);
		}
	}

	public void receive(FakePeer sendingPeer, Message message) {
		this.pendingMessages.add(new PeerMessage(sendingPeer, message));
	}

	public void disconnect(FakePeer peer) {
		this.peers.remove(peer);
	}

	public FakePeer pickRandomPeer() {
		synchronized (this.peers) {
			if (this.peers.isEmpty())
				return null;

			Random random = new Random();
			int i = random.nextInt(this.peers.size());
			return this.peers.get(i);
		}
	}

}
