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

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.catalina.session.StandardSession;
import org.redisson.api.RMapAsync;

public class ImprovedRedissonSession extends StandardSession {
	private static final long serialVersionUID = 1L;

	public static final String REDEX_AUTHTYPE_ATTR = "redex:session:authtype";
	public static final String REDEX_CREATION_TIME_ATTR = "redex:session:creationTime";
	public static final String REDEX_IS_NEW_ATTR = "redex:session:isNew";
	public static final String REDEX_IS_VALID_ATTR = "redex:session:isValid";
	public static final String REDEX_LAST_ACCESSED_TIME_ATTR = "redex:session:lastAccessedTime";
	public static final String REDEX_MAX_INACTIVE_INTERVAL_ATTR = "redex:session:maxInactiveInterval";
	public static final String REDEX_PRINCIPAL_ATTR = "redex:session:principal";
	public static final String REDEX_THIS_ACCESSED_TIME_ATTR = "redex:session:thisAccessedTime";

	protected Map<String, Object> attributeMap;

	@SuppressWarnings("unchecked")
	protected ImprovedRedissonSession(final ImprovedRedissonSessionManager manager) {
		super(manager);
		try {
			final Field attributeMapField = StandardSession.class.getDeclaredField("attributes");
			attributeMap = (Map<String, Object>) attributeMapField.get(this);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected Map<String, Object> getAttributeMap() {
		return attributeMap;
	}

	protected void load(final Map<String, Object> rmap) {
		attributeMap.clear();

		authType = (String) rmap.get(REDEX_AUTHTYPE_ATTR);

		final Object rMapCreationTime = rmap.get(REDEX_CREATION_TIME_ATTR);
		if (rMapCreationTime != null) {
			creationTime = (long) rMapCreationTime;
		} else {
			creationTime = 0L;
		}

		final Object rMapIsNew = rmap.get(REDEX_IS_NEW_ATTR);
		if (rMapIsNew != null) {
			isNew = (boolean) rMapIsNew;
		} else {
			isNew = false;
		}

		final Object rMapIsValid = rmap.get(REDEX_IS_VALID_ATTR);
		if (rMapIsValid != null) {
			isValid = (boolean) rMapIsValid;
		} else {
			isValid = false;
		}

		final Object rMapLastAccessedTime = rmap.get(REDEX_LAST_ACCESSED_TIME_ATTR);
		if (rMapLastAccessedTime != null) {
			lastAccessedTime = (long) rMapLastAccessedTime;
		} else {
			lastAccessedTime = creationTime;
		}

		final Object rMapMaxInactiveInterval = rmap.get(REDEX_MAX_INACTIVE_INTERVAL_ATTR);
		if (rMapMaxInactiveInterval != null) {
			maxInactiveInterval = (int) rMapMaxInactiveInterval;
		} else {
			maxInactiveInterval = -1;
		}

		principal = (Principal) rmap.get(REDEX_PRINCIPAL_ATTR);

		final Object rMapThisAccessedTime = rmap.get(REDEX_THIS_ACCESSED_TIME_ATTR);
		if (rMapThisAccessedTime != null) {
			thisAccessedTime = (long) rMapThisAccessedTime;
		} else {
			thisAccessedTime = creationTime;
		}

		final List<String> sessionAttributeKeys = filterOutRedexAttributes(rmap.keySet());
		for (final String key : sessionAttributeKeys) {
			setAttribute(key, rmap.get(key), true);
		}
	}

	protected void store(final RMapAsync<String, Object> rmap) {
		final List<String> sessionAttributeKeys = filterOutRedexAttributes(attributeMap.keySet());
		for (final String key : sessionAttributeKeys) {
			final Object value = attributeMap.get(key);
			if (value != null) {
				rmap.fastPutAsync(key, value);
			}
		}
		if (authType != null) {
			rmap.fastPutAsync(REDEX_AUTHTYPE_ATTR, authType);
		}
		rmap.fastPutAsync(REDEX_CREATION_TIME_ATTR, creationTime);
		rmap.fastPutAsync(REDEX_IS_NEW_ATTR, isNew);
		rmap.fastPutAsync(REDEX_IS_VALID_ATTR, isValid);
		rmap.fastPutAsync(REDEX_LAST_ACCESSED_TIME_ATTR, lastAccessedTime);
		rmap.fastPutAsync(REDEX_MAX_INACTIVE_INTERVAL_ATTR, maxInactiveInterval);
		if (principal != null) {
			rmap.fastPutAsync(REDEX_PRINCIPAL_ATTR, principal);
		}
		rmap.fastPutAsync(REDEX_THIS_ACCESSED_TIME_ATTR, thisAccessedTime);
	}

	protected static final List<String> filterOutRedexAttributes(final Set<String> keySet) {
		final List<String> sessionAttributeKeys = keySet.stream().filter((final String predicate) -> !predicate.startsWith("redex:"))
				.toList();
		return sessionAttributeKeys;
	}
}
