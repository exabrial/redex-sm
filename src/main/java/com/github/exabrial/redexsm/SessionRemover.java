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

/**
 *
 * Callback Handler from network sent session events. These methods should only operate on local caches and should not trigger any
 * network I/O. These events will be sources from network events.
 *
 * @author jonathan.fisher
 */
public interface SessionRemover {

	/**
	 * Evict a session from the local cache. Take no further action.
	 *
	 * @param sessionId
	 */
	void evictSession(String sessionId);

	/**
	 * Evict a session from the local cache, but also call the session destruction routines.
	 *
	 * @param sessionId
	 */
	void destroySession(String sessionId);
}
