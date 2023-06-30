package com.github.exabrial.redexsm.model;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ClassUtils;

import com.github.exabrial.redexsm.EncryptionSupport;
import com.github.exabrial.redexsm.ImprovedRedisSession;
import com.github.exabrial.redexsm.ImprovedRedisSessionManager;

public class SessionChangeset {
	public static final String REDEX_SESSION_ID = "redex:sessionId";
	public static final String REDEX_NODE_ID = "redex:nodeId";
	protected static final List<String> plaintextAttributes = List.of(REDEX_NODE_ID, REDEX_SESSION_ID,
			ImprovedRedisSessionManager.REDEX_UID, ImprovedRedisSession.REDEX_AUTHTYPE_ATTR, ImprovedRedisSession.REDEX_CREATION_TIME_ATTR,
			ImprovedRedisSession.REDEX_IS_NEW_ATTR, ImprovedRedisSession.REDEX_IS_VALID_ATTR,
			ImprovedRedisSession.REDEX_LAST_ACCESSED_TIME_ATTR, ImprovedRedisSession.REDEX_MAX_INACTIVE_INTERVAL_ATTR,
			ImprovedRedisSession.REDEX_THIS_ACCESSED_TIME_ATTR);
	private static final String REDEX = "redex:";
	protected final Map<String, Object> changsetMap = new HashMap<>();
	protected final String sessionId;
	protected final long expirationInSeconds;

	public SessionChangeset(final String sessionId, final String nodeId, final long expirationInSeconds) {
		this.sessionId = sessionId;
		this.expirationInSeconds = expirationInSeconds;
		changsetMap.put(REDEX_SESSION_ID, sessionId);
		changsetMap.put(REDEX_NODE_ID, nodeId);
	}

	public void put(final String key, final Object value) {
		changsetMap.put(key, value);
	}

	public Map<byte[], byte[]> toEncodedMap(final EncryptionSupport encryptionSupport) {
		try {
			final Map<byte[], byte[]> redisMap = new HashMap<>();

			for (final String key : changsetMap.keySet()) {
				final byte[] encodedBytes;
				final StringBuilder storageKey = new StringBuilder();
				final Object value = changsetMap.get(key);
				try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					if (ClassUtils.isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
						try (CustomDataOutputStream cdos = new CustomDataOutputStream(baos)) {
							final char writeType = cdos.writeValue(value);
							storageKey.append("d");
							storageKey.append(writeType);
							storageKey.append(":");
							cdos.flush();
						}
					} else {
						try (final ObjectOutputStream oos = new ObjectOutputStream(baos)) {
							storageKey.append("so:");
							oos.writeObject(value);
							oos.flush();
						}
					}
					encodedBytes = baos.toByteArray();
				}
				if (plaintextAttributes.contains(key) || ClassUtils.isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
					storageKey.append("pt:");
					storageKey.append(key);
					redisMap.put(storageKey.toString().getBytes(StandardCharsets.UTF_8), encodedBytes);
				} else {
					storageKey.append("ct:");
					storageKey.append(key);
					final byte[] encryptedBytes = encryptionSupport.encrypt(encodedBytes);
					redisMap.put(storageKey.toString().getBytes(StandardCharsets.UTF_8), encryptedBytes);
				}
			}

			return redisMap;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	public long getExpirationInSeconds() {
		return expirationInSeconds;
	}

	public byte[] toEncodedSessionId(final String keyPrefix) {
		return toEncodedSessionId(keyPrefix, sessionId);
	}

	public static byte[] toEncodedSessionId(final String keyPrefix, final String sessionId) {
		return (REDEX + keyPrefix + ":" + sessionId).getBytes(StandardCharsets.UTF_8);
	}
}
