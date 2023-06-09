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

import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.codec.SerializationCodec;

public class EncryptedSerializationCodec extends SerializationCodec {
	private final Decoder<Object> decoder;
	private final Encoder encoder;
	private final EncryptionSupport encryption = new EncryptionSupport();

	public EncryptedSerializationCodec() {
		super();
		decoder = new EncryptedDecoder(super.getValueDecoder(), encryption);
		encoder = new EncryptedEncoder(super.getValueEncoder(), encryption);
	}

	public EncryptedSerializationCodec(final ClassLoader classLoader) {
		super(classLoader);
		decoder = new EncryptedDecoder(super.getValueDecoder(), encryption);
		encoder = new EncryptedEncoder(super.getValueEncoder(), encryption);
	}

	public EncryptedSerializationCodec(final ClassLoader classLoader, final EncryptedSerializationCodec codec) {
		super(classLoader, codec);
		decoder = new EncryptedDecoder(super.getValueDecoder(), encryption);
		encoder = new EncryptedEncoder(super.getValueEncoder(), encryption);
	}

	@Override
	public Decoder<Object> getValueDecoder() {
		return decoder;
	}

	@Override
	public Encoder getValueEncoder() {
		return encoder;
	}

	@Override
	public Decoder<Object> getMapValueDecoder() {
		return decoder;
	}

	@Override
	public Encoder getMapValueEncoder() {
		return encoder;
	}
}
