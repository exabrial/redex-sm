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
import java.io.DataInputStream;
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

import com.github.exabrial.redexsm.model.CustomDataOutputStream;
import com.github.exabrial.redexsm.model.CustomObjectInputStream;
import com.github.exabrial.redexsm.model.SessionChangeset;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.UnifiedJedis;

public class JedisRedisService implements Closeable {
	protected static final List<String> plaintextAttributes = List.of(SessionChangeset.REDEX_NODE_ID, SessionChangeset.REDEX_SESSION_ID,
			ImprovedRedisSessionManager.REDEX_UID, ImprovedRedisSession.REDEX_AUTHTYPE_ATTR, ImprovedRedisSession.REDEX_CREATION_TIME_ATTR,
			ImprovedRedisSession.REDEX_IS_NEW_ATTR, ImprovedRedisSession.REDEX_IS_VALID_ATTR,
			ImprovedRedisSession.REDEX_LAST_ACCESSED_TIME_ATTR, ImprovedRedisSession.REDEX_MAX_INACTIVE_INTERVAL_ATTR,
			ImprovedRedisSession.REDEX_THIS_ACCESSED_TIME_ATTR);
	private final String url;
	private UnifiedJedis jedis;
	private SessionRemover sessionRemover;
	private final String keyPrefix;
	private final EncryptionSupport encryptionSupport;

	public JedisRedisService(final String url, final String keyPrefix) {
		this.url = url;
		this.keyPrefix = keyPrefix;
		this.encryptionSupport = new EncryptionSupport();
	}

	public void start(final SessionRemover sessionRemover) {
		final ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
		poolConfig.setBlockWhenExhausted(true);
		poolConfig.setMaxIdle(3);
		poolConfig.setMaxTotal(15);
		poolConfig.setMaxWait(Duration.of(5000, ChronoUnit.MILLIS));
		poolConfig.setJmxEnabled(true);
		jedis = new JedisPooled(poolConfig, url);
		this.sessionRemover = sessionRemover;
	}

	@Override
	public void close() {
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
			// getRTopicAsync(rBatch, SESSION_EVICTION).publishAsync(new SessionEvictionMessage(nodeId, sessionId));
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

					final char[] encryptionHeader = fullKey.substring(3, 5).toCharArray();
					final String key = fullKey.substring(6);
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

					final char[] valueEncodingHeader = fullKey.substring(0, 2).toCharArray();
					try (final ByteArrayInputStream bais = new ByteArrayInputStream(encodedBytes)) {
						switch (valueEncodingHeader[0]) {
							case 's' -> {
								try (ObjectInputStream ois = new CustomObjectInputStream(context.getLoader().getClassLoader(), bais)) {
									final Object value = ois.readObject();
									sessionMap.put(key, value);
								}
							}
							case 'd' -> {
								try (DataInputStream dis = new DataInputStream(bais)) {
									switch (valueEncodingHeader[1]) {
										case 'Z' -> {
											sessionMap.put(key, dis.readBoolean());
										}
										case 'B' -> {
											sessionMap.put(key, dis.readByte());
										}
										case 'C' -> {
											sessionMap.put(key, dis.readChar());
										}
										case 'D' -> {
											sessionMap.put(key, dis.readDouble());
										}
										case 'F' -> {
											sessionMap.put(key, dis.readFloat());
										}
										case 'I' -> {
											sessionMap.put(key, dis.readInt());
										}
										case 'J' -> {
											sessionMap.put(key, dis.readLong());
										}
										case 'S' -> {
											sessionMap.put(key, dis.readShort());
										}
										case 'T' -> {
											sessionMap.put(key, dis.readUTF());
										}
										default -> {
											throw new RuntimeException("Unknown encodingHeaderDataType value:" + fullKey);
										}
									}
								}
							}
							default -> {
								throw new RuntimeException("Unknown encodingHeader prefix:" + fullKey);
							}
						}
					}
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
}
