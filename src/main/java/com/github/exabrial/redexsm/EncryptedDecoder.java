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

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

public class EncryptedDecoder implements Decoder<Object> {
	private final Decoder<Object> valueDecoder;
	private final EncryptionSupport encryption;

	public EncryptedDecoder(final Decoder<Object> valueDecoder, final EncryptionSupport encryption) {
		this.valueDecoder = valueDecoder;
		this.encryption = encryption;
	}

	@Override
	public Object decode(final ByteBuf buf, final State state) throws IOException {
		final boolean encrypted = buf.readBoolean();
		if (encrypted) {
			try (ByteBufInputStream bbis = new ByteBufInputStream(buf)) {
				final byte[] encryptedBytes = bbis.readAllBytes();
				final byte[] decryptedBytes = encryption.decrypt(encryptedBytes);
				buf.clear();
				buf.writeBytes(decryptedBytes);
			}
		}
		return valueDecoder.decode(buf, state);
	}
}
