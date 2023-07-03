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
package com.github.exabrial.redexsm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.catalina.Context;
import org.apache.commons.lang3.ClassUtils;

import com.github.exabrial.redexsm.inboundevents.SessionDestructionListener;
import com.github.exabrial.redexsm.inboundevents.SessionEvicitionListener;
import com.github.exabrial.redexsm.model.AutoDataInputStream;
import com.github.exabrial.redexsm.model.AutoDataOutputStream;
import com.github.exabrial.redexsm.model.ClassloaderAwareObjectInputStream;
import com.github.exabrial.redexsm.model.SessionChangeset;
import com.github.exabrial.redexsm.model.SessionDestructionMessage;
import com.github.exabrial.redexsm.model.SessionEvictionMessage;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.UnifiedJedis;

public class JedisRedisService implements Closeable {
	public static final String REDEX_SESSION_DESTRUCTION = "redex:sessionDestruction:";
	public static final String REDEX_SESSION_EVICTION = "redex:sessionEviction:";
	protected static final List<String> plaintextAttributes = List.of(SessionChangeset.REDEX_NODE_ID, SessionChangeset.REDEX_SESSION_ID,
			ImprovedRedisSessionManager.REDEX_UID, ImprovedRedisSession.REDEX_AUTHTYPE_ATTR, ImprovedRedisSession.REDEX_CREATION_TIME_ATTR,
			ImprovedRedisSession.REDEX_IS_NEW_ATTR, ImprovedRedisSession.REDEX_IS_VALID_ATTR,
			ImprovedRedisSession.REDEX_LAST_ACCESSED_TIME_ATTR, ImprovedRedisSession.REDEX_MAX_INACTIVE_INTERVAL_ATTR,
			ImprovedRedisSession.REDEX_THIS_ACCESSED_TIME_ATTR);
	private final String url;
	private final String keyPrefix;
	private final EncryptionSupport encryptionSupport;
	private final String nodeId;
	private UnifiedJedis jedis;
	private SessionDestructionListener destructionListener;
	private SessionEvicitionListener evicitionListener;

	public JedisRedisService(final String url, final String keyPrefix, final String nodeId) {
		this.url = url;
		this.keyPrefix = keyPrefix;
		this.nodeId = nodeId;
		this.encryptionSupport = new EncryptionSupport();
	}

	public void start(final SessionRemover sessionRemover) {
		final ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
		poolConfig.setMinIdle(1);
		poolConfig.setMaxIdle(3);
		poolConfig.setMaxTotal(15);
		poolConfig.setMaxWait(Duration.of(5000, ChronoUnit.MILLIS));
		poolConfig.setJmxEnabled(true);
		poolConfig.setBlockWhenExhausted(true);
		this.jedis = new JedisPooled(poolConfig, url);
		destructionListener = new SessionDestructionListener(sessionRemover, jedis, REDEX_SESSION_DESTRUCTION + keyPrefix, nodeId);
		evicitionListener = new SessionEvicitionListener(sessionRemover, jedis, REDEX_SESSION_EVICTION + keyPrefix, nodeId);
	}

	@Override
	public void close() {
		destructionListener.close();
		destructionListener = null;
		evicitionListener.close();
		evicitionListener = null;
		jedis.close();
		jedis = null;
	}

	public void publishChangeset(final SessionChangeset sessionChangeset) {
		try (final Transaction multi = jedis.multi()) {
			final byte[] sessionKey = sessionChangeset.toEncodedSessionId(keyPrefix);
			multi.del(sessionKey);
			final Map<byte[], byte[]> encodedMap = toEncodedMap(encryptionSupport, sessionChangeset.getSessionMap());
			multi.hset(sessionKey, encodedMap);
			multi.expire(sessionKey, sessionChangeset.getExpirationInSeconds());
			multi.publish((REDEX_SESSION_EVICTION + keyPrefix).getBytes(StandardCharsets.UTF_8),
					new SessionEvictionMessage(nodeId, sessionChangeset.getSessionId()).toBytes());
			multi.exec();
		}
	}

	public void remove(final String sessionId) {
		final byte[] sessionKey = SessionChangeset.toEncodedSessionId(keyPrefix, sessionId);
		try (final Transaction multi = jedis.multi()) {
			multi.del(sessionKey);
			multi.publish((REDEX_SESSION_DESTRUCTION + keyPrefix).getBytes(StandardCharsets.UTF_8),
					new SessionDestructionMessage(nodeId, sessionId).toBytes());
			multi.exec();
		}
	}

	public Map<String, Object> loadSessionMap(final String sessionId, final Context context) {
		try {
			final Map<String, Object> sessionMap;
			final byte[] sessionKey = SessionChangeset.toEncodedSessionId(keyPrefix, sessionId);
			if (jedis.exists(sessionKey)) {
				sessionMap = new HashMap<>();
				final Map<byte[], byte[]> encodedMap = jedis.hgetAll(sessionKey);
				for (final byte[] encodedKey : encodedMap.keySet()) {
					final String fullKey = new String(encodedKey, StandardCharsets.UTF_8);

					final String key = fullKey.substring(6);
					final char[] encryptionHeader = fullKey.substring(3, 5).toCharArray();
					byte[] encodedBytes;
					switch (encryptionHeader[0]) {
						case 'p' -> {
							encodedBytes = encodedMap.get(encodedKey);
						}
						case 'c' -> {
							encodedBytes = encryptionSupport.decrypt(encodedMap.get(encodedKey));
						}
						default -> {
							throw new RuntimeException("Unknown encryptionHeader prefix:" + fullKey);
						}
					}

					final Object value;
					final char[] valueEncodingHeader = fullKey.substring(0, 2).toCharArray();
					try (final ByteArrayInputStream bais = new ByteArrayInputStream(encodedBytes)) {
						switch (valueEncodingHeader[0]) {
							case 's' -> {
								try (ObjectInputStream ois = new ClassloaderAwareObjectInputStream(context.getLoader().getClassLoader(), bais)) {
									value = ois.readObject();
								}
							}
							case 'd' -> {
								try (AutoDataInputStream adis = new AutoDataInputStream(bais)) {
									value = adis.readType(valueEncodingHeader[1]);
								}
							}
							default -> {
								throw new RuntimeException("Unknown encodingHeader prefix:" + fullKey);
							}
						}
					}
					sessionMap.put(key, value);
				}
			} else {
				sessionMap = null;
			}
			return sessionMap;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Map<byte[], byte[]> toEncodedMap(final EncryptionSupport encryptionSupport, final Map<String, Object> changsetMap) {
		try {
			final Map<byte[], byte[]> redisMap = new HashMap<>();

			for (final String key : changsetMap.keySet()) {
				final byte[] encodedBytes;
				final StringBuilder storageKey = new StringBuilder();
				final Object value = changsetMap.get(key);
				try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					if (ClassUtils.isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
						try (AutoDataOutputStream ados = new AutoDataOutputStream(baos)) {
							final char writeType = ados.writeValue(value);
							storageKey.append("d");
							storageKey.append(writeType);
							storageKey.append(":");
							ados.flush();
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
}
