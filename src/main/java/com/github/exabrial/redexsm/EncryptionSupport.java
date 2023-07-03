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

import java.nio.ByteBuffer;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionSupport {
	protected byte[] keygenSalt = new byte[] { 93, 56, -45, 23, 46, 98, -106, -54, 0, 94, -58, -74, -6, -23, -55, 10, -3, 77, 23, 108,
			76, 11, -117, -72, -50, -25, 60, -49, 60, 51, 13, 48 };

	private static final String AES = "AES";
	private static final String AES_GCM_NO_PADDING = AES + "/GCM/NoPadding";
	private static final int AES_KEY_LENGTH = 128;
	private static final int AES_GCM_IV_LENGTH = 12;

	private static final String KEYGEN_ALGO = "PBKDF2WithHmacSHA256";
	private static final int KEYGEN_ITERATIONS = 64 * 1024;

	private final SecretKey secretKey;
	private final SecureRandom secureRandom;

	public EncryptionSupport() {
		this(null);
	}

	public EncryptionSupport(final String keyPassword) {
		if (keyPassword == null) {
			throw new RuntimeException("An encryption key keyPassword was not set");
		} else {
			secretKey = (SecretKey) keyFromPassword(keyPassword.toCharArray());
			try {
				secureRandom = SecureRandom.getInstanceStrong();
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public byte[] decrypt(final byte[] cipherMessage) {
		try {
			final Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
			final AlgorithmParameterSpec gcmIv = new GCMParameterSpec(AES_KEY_LENGTH, cipherMessage, 0, AES_GCM_IV_LENGTH);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);
			final byte[] plainText = cipher.doFinal(cipherMessage, AES_GCM_IV_LENGTH, cipherMessage.length - AES_GCM_IV_LENGTH);
			return plainText;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	public byte[] encrypt(final byte[] plainText) {
		try {
			final byte[] iv = new byte[AES_GCM_IV_LENGTH];
			secureRandom.nextBytes(iv);
			final Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
			final GCMParameterSpec parameterSpec = new GCMParameterSpec(AES_KEY_LENGTH, iv);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
			final byte[] cipherText = cipher.doFinal(plainText);
			final ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
			byteBuffer.put(iv);
			byteBuffer.put(cipherText);
			return byteBuffer.array();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected Key keyFromPassword(final char[] password) {
		try {
			final SecretKeyFactory factory = SecretKeyFactory.getInstance(KEYGEN_ALGO);
			final KeySpec spec = new PBEKeySpec(password, keygenSalt, KEYGEN_ITERATIONS, AES_KEY_LENGTH);
			final SecretKey tmp = factory.generateSecret(spec);
			return new SecretKeySpec(tmp.getEncoded(), AES);
		} catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException(e);
		}
	}
}
