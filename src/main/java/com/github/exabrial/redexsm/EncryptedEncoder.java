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

import org.apache.commons.lang3.ClassUtils;
import org.redisson.client.protocol.Encoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

public class EncryptedEncoder implements Encoder {
	private final Encoder valueEncoder;
	private final EncryptionSupport encryption;

	public EncryptedEncoder(final Encoder valueEncoder, final EncryptionSupport encryption) {
		this.valueEncoder = valueEncoder;
		this.encryption = encryption;
	}

	@Override
	public ByteBuf encode(final Object in) throws IOException {
		final ByteBuf buf = valueEncoder.encode(in);
		try (ByteBufInputStream bbis = new ByteBufInputStream(buf)) {
			final byte[] encodedBytes = bbis.readAllBytes();
			buf.clear();
			if (in instanceof String || ClassUtils.isPrimitiveOrWrapper(in.getClass())) {
				buf.writeBoolean(false);
				buf.writeBytes(encodedBytes);
			} else {
				final byte[] encryptedBytes = encryption.encrypt(encodedBytes);
				buf.writeBoolean(true);
				buf.writeBytes(encryptedBytes);
			}
		}
		return buf;
	}
}
