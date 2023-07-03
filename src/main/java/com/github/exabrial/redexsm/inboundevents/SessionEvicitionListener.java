/*
 * Copyright 2023 Jonathan S. Fisher
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission - subsequent versions of the EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/sites/default/files/custom-page/attachment/2020-03/EUPL-1.2%20EN.txt
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */
package com.github.exabrial.redexsm.inboundevents;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;

import com.github.exabrial.redexsm.SessionRemover;
import com.github.exabrial.redexsm.model.SessionEvictionMessage;

import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.UnifiedJedis;

public class SessionEvicitionListener implements Closeable {
	private final Thread backgroundThread;
	private final BinaryJedisPubSub jedisPubSub;

	public SessionEvicitionListener(final SessionRemover sessionRemover, final UnifiedJedis jedis, final String channelName,
			final String nodeId) {
		jedisPubSub = new BinaryJedisPubSub() {
			@Override
			public void onMessage(final byte[] channel, final byte[] message) {
				final SessionEvictionMessage destructionMessage = new SessionEvictionMessage(message);
				if (!nodeId.equals(destructionMessage.sourceNodeId)) {
					sessionRemover.evictSession(destructionMessage.sessionId);
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
