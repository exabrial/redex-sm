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
package com.github.exabrial.redexsm.listeners;

import org.redisson.api.listener.MessageListener;

import com.github.exabrial.redexsm.ImprovedRedisSessionManager;
import com.github.exabrial.redexsm.model.SessionEvictionMessage;

public class SessionEvictionListener implements MessageListener<SessionEvictionMessage> {
	private final ImprovedRedisSessionManager improvedRedisSessionManager;

	public SessionEvictionListener(final ImprovedRedisSessionManager improvedRedisSessionManager) {
		this.improvedRedisSessionManager = improvedRedisSessionManager;
	}

	@Override
	public void onMessage(final CharSequence channel, final SessionEvictionMessage msg) {
		if (!msg.sourceNodeId.equals(improvedRedisSessionManager.getNodeId())) {
			improvedRedisSessionManager.evictSession(msg.sessionId);
		}
	}
}
