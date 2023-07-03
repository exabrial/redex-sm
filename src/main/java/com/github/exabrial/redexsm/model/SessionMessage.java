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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class SessionMessage {
	public final String sourceNodeId;
	public final String sessionId;

	public SessionMessage(final String sourceNodeId, final String sessionId) {
		this.sourceNodeId = sourceNodeId;
		this.sessionId = sessionId;
	}

	public SessionMessage(final byte[] fromBytes) {
		try (final ByteArrayInputStream bais = new ByteArrayInputStream(fromBytes)) {
			try (final DataInputStream dis = new DataInputStream(bais)) {
				sourceNodeId = dis.readUTF();
				sessionId = dis.readUTF();
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public byte[] toBytes() {
		try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			try (final DataOutputStream dos = new DataOutputStream(baos)) {
				dos.writeUTF(sourceNodeId);
				dos.writeUTF(sessionId);
				dos.flush();
				return baos.toByteArray();
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
}
