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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.exabrial.redexsm.jedis.JedisRedisService;
import com.github.exabrial.redexsm.model.SessionChangeset;

public class ImprovedRedisSessionManager extends ManagerBase implements SessionRemover {
	protected static final String JSESSIONID = "JSESSIONID";
	protected static final Logger log = LoggerFactory.getLogger(ImprovedRedisSessionManager.class);

	private RedisService redisService;
	private Valve valve;

	protected String keyPassword;
	protected String redisUrl;
	protected Pattern ignorePattern;
	protected String keyPrefix;
	protected String nodeId;
	protected String sessionCookieName;
	protected long sessionTimeoutSeconds;

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
						final SessionChangeset sessionChangeset = new SessionChangeset(sessionId, nodeId, sessionTimeoutSeconds,
								request.getRemoteUser());
						session.store(sessionChangeset);
						log.trace("requestComplete() executing batch update: publishing session and eviction notice to topic for sessionId:{}",
								sessionId);
						redisService.publishChangeset(sessionChangeset);
					}
				} catch (final Exception e) {
					log.error("requestComplete() caught exception", e);
					throw new RuntimeException(e);
				}
			}
		}
	}

	@Override
	public void evictSession(final String sessionId) {
		log.trace("evictSession() sessionId:{}", sessionId);
		sessions.remove(sessionId);
	}

	@Override
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
		redisService.remove(session.getId());
	}

	@Override
	public Session findSession(final String id) throws IOException {
		log.trace("findSession() id:{}", id);
		ImprovedRedisSession session;
		if (id != null) {
			session = (ImprovedRedisSession) super.findSession(id);
			if (session == null) {
				log.trace("findSession() local cache miss. Trying redis...");
				final Map<String, Object> sessionMap = redisService.loadSessionMap(id, getContext());
				if (sessionMap != null) {
					log.trace("findSession() session located in redis");
					session = (ImprovedRedisSession) createEmptySession();
					session.load(sessionMap);
					session.setId(id, true);
				} else {
					log.trace("findSession() redis miss; giving up");
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
		setState(LifecycleState.STARTING);
		try {
			installValve();
			final String contextCookieName = getContext().getSessionCookieName();
			if (contextCookieName == null) {
				sessionCookieName = JSESSIONID;
			} else {
				sessionCookieName = contextCookieName;
			}
			sessionTimeoutSeconds = getContext().getSessionTimeout() * 60L;
			if (keyPrefix == null) {
				keyPrefix = getContext().getName().replaceFirst("/", "").replace("/", ":");
			}
			if (nodeId == null) {
				nodeId = getHostName() + ":" + keyPrefix + ":" + UUID.randomUUID();
			}
			redisService = new JedisRedisService(redisUrl, keyPrefix, nodeId, keyPassword);
			redisService.start(this);
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
			redisService.close();
		} catch (final Exception e) {
			log.error("stopInternal() exception", e);
			throw new LifecycleException(e);
		}
		log.info("stopInternal() complete.");
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

	public void setRedisUrl(final String redisUrl) {
		this.redisUrl = redisUrl;
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

	public void setKeyPassword(final String keyPassword) {
		this.keyPassword = keyPassword;
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
