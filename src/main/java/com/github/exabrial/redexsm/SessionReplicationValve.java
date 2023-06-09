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

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

public class SessionReplicationValve extends ValveBase {
	private final ImprovedRedissonSessionManager improvedRedissonSessionManager;

	public SessionReplicationValve(final ImprovedRedissonSessionManager improvedRedissonSessionManager) {
		this.improvedRedissonSessionManager = improvedRedissonSessionManager;
	}

	@Override
	public void invoke(final Request request, final Response response) throws IOException, ServletException {
		if (getNext() == null) {
			return;
		} else {
			try {
				improvedRedissonSessionManager.requestStarted(request, response);
				getNext().invoke(request, response);
			} finally {
				improvedRedissonSessionManager.requestComplete(request, response);
			}
		}
	}
}
