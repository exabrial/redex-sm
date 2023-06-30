package com.github.exabrial.redexsm;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.Context;

import com.github.exabrial.redexsm.model.CustomObjectInputStream;
import com.github.exabrial.redexsm.model.SessionChangeset;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.UnifiedJedis;

public class JedisRedisService implements Closeable {
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
			final Map<byte[], byte[]> encodedMap = sessionChangeset.toEncodedMap(encryptionSupport);
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
}
