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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Session;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.session.ManagerBase;
import org.redisson.Redisson;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.api.RBatch;
import org.redisson.api.RMap;
import org.redisson.api.RMapAsync;
import org.redisson.api.RTopic;
import org.redisson.api.RTopicAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.exabrial.redexsm.listeners.SessionDestructionListener;
import com.github.exabrial.redexsm.listeners.SessionEvictionListener;
import com.github.exabrial.redexsm.model.SessionDestructionMessage;
import com.github.exabrial.redexsm.model.SessionEvictionMessage;

public class ImprovedRedisSessionManager extends ManagerBase {
	public static final String REDEX_NODE_ID = "redex:nodeId";
	public static final String REDEX_SESSION_ID = "redex:sessionId";
	public static final String REDEX_UID = "redex:uid";
	public static final String SESSION_DESTRUCTION = "sessionDestruction";
	public static final String SESSION_EVICTION = "sessionEviction";

	private static final String JSESSIONID = "JSESSIONID";
	private static final Logger log = LoggerFactory.getLogger(ImprovedRedisSessionManager.class);
	private Codec codec;
	private RedissonClient redisson;
	private SessionDestructionListener sessionDestructionListener;
	private SessionEvictionListener sessionEvictionListener;
	private Valve valve;

	private String configPath;
	private Pattern ignorePattern;
	private String keyPrefix;
	private String nodeId;
	private String sessionCookieName;
	private int sessionTimeoutMins;

	public void requestStarted(final Request request, final Response response) {
	}

	public void requestComplete(final Request request, final Response response) {
		final String requestURI = request.getRequestURI();
		if (!ignorePattern.matcher(requestURI).matches()) {
			final String sessionId = toSessionId(request, response);
			if (sessionId != null) {
				try {
					final ImprovedRedisSession session = (ImprovedRedisSession) super.findSession(sessionId);
					if (session != null) {
						final RBatch rBatch = createRBatch();
						final RMapAsync<String, Object> rMapAsync = getRMapAsync(rBatch, sessionId);
						rMapAsync.clearAsync();
						session.store(rMapAsync);
						rMapAsync.fastPutAsync(REDEX_NODE_ID, nodeId);
						final String remoteUser = request.getRemoteUser();
						if (remoteUser != null) {
							rMapAsync.fastPutAsync(REDEX_UID, remoteUser);
						}
						rMapAsync.fastPutAsync(REDEX_SESSION_ID, sessionId);
						rMapAsync.expireAsync(Duration.of(sessionTimeoutMins, ChronoUnit.MINUTES));
						getRTopicAsync(rBatch, SESSION_EVICTION).publishAsync(new SessionEvictionMessage(nodeId, sessionId));
						rMapAsync.touchAsync();
						log.trace("requestComplete() executing batch update: publishing session and eviction notice to topic for sessionId:{}",
								sessionId);
						rBatch.execute();
					}
				} catch (final IOException e) {
					log.error("requestComplete() caught exception", e);
					throw new RuntimeException(e);
				}
			}
		}
	}

	public void evictSession(final String sessionId) {
		log.trace("evictSession() sessionId:{}", sessionId);
		sessions.remove(sessionId);
	}

	public void destroySession(final String sessionId) {
		log.trace("destroySession() sessionId:{}", sessionId);
		try {
			final ImprovedRedisSession session = (ImprovedRedisSession) super.findSession(sessionId);
			if (session != null) {
				session.expire(true);
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Session createEmptySession() {
		log.trace("createEmptySession()");
		return new ImprovedRedisSession(this);
	}

	@Override
	public void remove(final Session session, final boolean update) {
		log.trace("remove() session.id:{} update:{}", session.getId(), update);
		super.remove(session, update);
		final RBatch rBatch = createRBatch();
		getRMapAsync(rBatch, session.getId()).deleteAsync();
		final RTopicAsync sessionDestructionTopic = getRTopicAsync(rBatch, SESSION_DESTRUCTION);
		sessionDestructionTopic.publishAsync(new SessionDestructionMessage(nodeId, session.getId()));
		rBatch.execute();
	}

	@Override
	public Session findSession(final String id) throws IOException {
		log.trace("findSession() id:{}", id);
		ImprovedRedisSession session;
		if (id != null) {
			session = (ImprovedRedisSession) super.findSession(id);
			if (session == null) {
				log.trace("findSession() local cache miss. Trying redis...");
				final RMap<String, Object> rmap = getRMap(id);
				if (rmap.isExists()) {
					log.trace("findSession() session located in redis");
					session = (ImprovedRedisSession) createEmptySession();
					session.load(rmap);
					session.setAttribute(REDEX_NODE_ID, nodeId);
					session.setId(id, true);
					rmap.touchAsync();
				} else {
					log.trace("findSession() redis cache miss too; giving up");
				}
			}
		} else {
			session = null;
		}
		return session;
	}

	@Override
	protected void startInternal() throws LifecycleException {
		log.info("startInternal() starting ImprovedRedisSessionManager");
		super.startInternal();
		try {
			redisson = buildClient();
			installValve();
			if (codec == null) {
				final Codec encryptionCodec = new EncryptedSerializationCodec();
				codec = new CompositeCodec(StringCodec.INSTANCE, encryptionCodec, encryptionCodec);
			}
			final String contextCookieName = getContext().getSessionCookieName();
			if (contextCookieName == null) {
				sessionCookieName = JSESSIONID;
			} else {
				sessionCookieName = contextCookieName;
			}
			sessionTimeoutMins = getContext().getSessionTimeout();
			if (keyPrefix == null) {
				keyPrefix = getContext().getName().replaceFirst("/", "").replace("/", ":");
			}
			if (nodeId == null) {
				nodeId = getHostName() + ":" + keyPrefix + ":" + UUID.randomUUID();
			}
			addSessionEvictionListener();
			addSessionDestructionListener();
			setState(LifecycleState.STARTING);
		} catch (final Exception e) {
			log.error("startInternal() exception", e);
			throw new LifecycleException(e);
		}
		log.info("startInternal() complete.");
	}

	@Override
	protected void stopInternal() throws LifecycleException {
		log.info("stopInternal() stopping ImprovedRedisSessionManager nodeId:{}", nodeId);
		super.stopInternal();
		setState(LifecycleState.STOPPING);
		try {
			uninstallValve();
			removeSessionEvictionListener();
			removeSessionDestructionListener();
			redisson.shutdown();
		} catch (final Exception e) {
			log.error("stopInternal() exception", e);
			throw new LifecycleException(e);
		}
		log.info("stopInternal() complete.");
	}

	protected void addSessionDestructionListener() {
		final RTopic sessionDestructionTopic = getRTopic(SESSION_DESTRUCTION);
		sessionDestructionListener = new SessionDestructionListener(this);
		sessionDestructionTopic.addListener(SessionDestructionMessage.class, sessionDestructionListener);
	}

	protected void removeSessionDestructionListener() {
		final RTopic sessionDestructionTopic = getRTopic(SESSION_DESTRUCTION);
		sessionDestructionTopic.removeListenerAsync(sessionDestructionListener);
	}

	protected void addSessionEvictionListener() {
		final RTopic sessionEvictionTopic = getRTopic(SESSION_EVICTION);
		sessionEvictionListener = new SessionEvictionListener(this);
		sessionEvictionTopic.addListener(SessionEvictionMessage.class, sessionEvictionListener);
	}

	protected void removeSessionEvictionListener() {
		final RTopic sessionEvictionTopic = getRTopic(SESSION_EVICTION);
		sessionEvictionTopic.removeListenerAsync(sessionEvictionListener);
	}

	protected String getHostName() {
		String hostName;
		hostName = System.getProperty("server.hostname");
		if (hostName == null) {
			try {
				final Process p = Runtime.getRuntime().exec("hostname").destroyForcibly();
				final byte[] bytes = p.getInputStream().readAllBytes();
				hostName = new String(bytes, "ASCII").trim();
			} catch (final Exception e) {
				try {
					hostName = InetAddress.getLocalHost().getHostName();
				} catch (final UnknownHostException e1) {
					log.error("getHostName() error #1:", e);
					log.error("getHostName() error #2:", e1);
					throw new RuntimeException(e1);
				}
			}
		}
		return hostName;
	}

	protected void uninstallValve() {
		final Pipeline pipeline = getContext().getPipeline();
		synchronized (pipeline) {
			pipeline.removeValve(valve);
		}
	}

	protected void installValve() {
		final Pipeline pipeline = getContext().getPipeline();
		synchronized (pipeline) {
			valve = new SessionReplicationValve(this);
			pipeline.addValve(valve);
		}
	}

	protected RedissonClient buildClient() throws IOException {
		final Config config = Config.fromYAML(new File(configPath), getClass().getClassLoader());
		return Redisson.create(config);
	}

	protected RBatch createRBatch() {
		return redisson.createBatch(BatchOptions.defaults().executionMode(ExecutionMode.IN_MEMORY_ATOMIC));
	}

	protected RMapAsync<String, Object> getRMapAsync(final RBatch rBatch, final String sessionId) {
		final String name = keyPrefix + ":redex:tomcat_session:" + sessionId;
		return rBatch.getMap(name, codec);
	}

	protected RTopicAsync getRTopicAsync(final RBatch rBatch, final String name) {
		final String topicNmae = keyPrefix + ":redex:tomcat_session_updates:" + getContext().getName() + ":" + name;
		return rBatch.getTopic(topicNmae);
	}

	protected RMap<String, Object> getRMap(final String sessionId) {
		final String name = keyPrefix + ":redex:tomcat_session:" + sessionId;
		return redisson.getMap(name, codec);
	}

	protected RTopic getRTopic(final String name) {
		final String topicNmae = keyPrefix + ":redex:tomcat_session_updates:" + getContext().getName() + ":" + name;
		return redisson.getTopic(topicNmae);
	}

	protected String toSessionId(final Request request, final Response response) {
		String sessionId;
		final String setCookieHeader = response.getHeaders("Set-Cookie").stream()
				.filter((final String predicate) -> predicate.startsWith(sessionCookieName)).findFirst().orElse(null);
		if (setCookieHeader != null) {
			int stop = setCookieHeader.indexOf(';');
			if (stop == -1) {
				stop = setCookieHeader.length();
			}
			sessionId = setCookieHeader.substring(setCookieHeader.indexOf('=') + 1, stop);
		} else {
			sessionId = null;
			if (request.getCookies() != null) {
				for (final Cookie cookie : request.getCookies()) {
					if (sessionCookieName.equals(cookie.getName())) {
						sessionId = cookie.getValue();
						break;
					}
				}
			}
		}
		return sessionId;
	}

	public void setConfigPath(final String configPath) {
		this.configPath = configPath;
	}

	public void setIgnorePattern(final String ignorePattern) {
		this.ignorePattern = Pattern.compile(ignorePattern);
	}

	public void setKeyPrefix(final String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}

	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public Codec getCodec() {
		return codec;
	}

	public void setCodec(final Codec codec) {
		this.codec = codec;
	}

	@Override
	public void load() throws ClassNotFoundException, IOException {
	}

	@Override
	public void unload() throws IOException {
	}

	@Override
	public String getName() {
		return ImprovedRedisSessionManager.class.getName();
	}
}
