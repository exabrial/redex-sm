package com.github.exabrial.redexsm.inboundevents;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;

import com.github.exabrial.redexsm.SessionRemover;
import com.github.exabrial.redexsm.model.SessionDestructionMessage;
import com.github.exabrial.redexsm.model.SessionMessage;

import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.UnifiedJedis;

public class SessionDestructionListener implements Closeable {
	private final Thread backgroundThread;
	private final BinaryJedisPubSub jedisPubSub;

	public SessionDestructionListener(final SessionRemover sessionRemover, final UnifiedJedis jedis, final String channelName,
			final String nodeId) {
		jedisPubSub = new BinaryJedisPubSub() {
			@Override
			public void onMessage(final byte[] channel, final byte[] message) {
				final SessionMessage destructionMessage = new SessionDestructionMessage(message);
				if (!nodeId.equals(destructionMessage.sourceNodeId)) {
					sessionRemover.destroySession(destructionMessage.sessionId);
				}
			}
		};
		backgroundThread = new Thread((Runnable) () -> {
			jedis.subscribe(jedisPubSub, channelName.getBytes(StandardCharsets.UTF_8));
		}, channelName);
		backgroundThread.start();
	}

	@Override
	public void close() {
		jedisPubSub.unsubscribe();
		backgroundThread.interrupt();
	}
}
