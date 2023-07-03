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
package com.github.exabrial.redexsm.model;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SessionChangeset {
	public static final String REDEX_SESSION_ID = "redex:sessionId";
	public static final String REDEX_NODE_ID = "redex:nodeId";
	public static final String REDEX_UID = "redex:uid";

	private static final String REDEX = "redex:";
	protected final Map<String, Object> changsetMap = new HashMap<>();
	protected final String sessionId;
	protected final long expirationInSeconds;

	public SessionChangeset(final String sessionId, final String nodeId, final long expirationInSeconds, final String remoteUser) {
		this.sessionId = sessionId;
		this.expirationInSeconds = expirationInSeconds;
		changsetMap.put(REDEX_SESSION_ID, sessionId);
		changsetMap.put(REDEX_NODE_ID, nodeId);
		if (remoteUser != null) {
			changsetMap.put(REDEX_UID, remoteUser);
		}
	}

	public void put(final String key, final Object value) {
		changsetMap.put(key, value);
	}

	public long getExpirationInSeconds() {
		return expirationInSeconds;
	}

	public byte[] toEncodedSessionId(final String keyPrefix) {
		return toEncodedSessionId(keyPrefix, sessionId);
	}

	public Map<String, Object> getSessionMap() {
		return Collections.unmodifiableMap(changsetMap);
	}

	public static byte[] toEncodedSessionId(final String keyPrefix, final String sessionId) {
		return (REDEX + keyPrefix + ":" + sessionId).getBytes(StandardCharsets.UTF_8);
	}

	public String getSessionId() {
		return sessionId;
	}
}
