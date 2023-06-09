/*
 * Copyright 2023 Jonathan S. Fisher
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission - subsequent versions of the EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at->
 *
 * https->//joinup.ec.europa.eu/sites/default/files/custom-page/attachment/2020-03/EUPL-1.2%20EN.txt
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */
package com.github.exabrial.redexsm.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AutoDataOutputStream extends DataOutputStream {
	public AutoDataOutputStream(final OutputStream out) {
		super(out);
	}

	public char writeValue(final Object value) throws IOException {
		final String name = value.getClass().getName();
		final char writtenType = switch (name) {
			case "java.lang.Boolean" -> {
				super.writeBoolean((boolean) value);
				yield 'Z';
			}
			case "java.lang.Byte" -> {
				super.writeByte((byte) value);
				yield 'B';
			}
			case "java.lang.Char" -> {
				super.writeChar((char) value);
				yield 'C';
			}
			case "java.lang.Double" -> {
				super.writeDouble((double) value);
				yield 'D';
			}
			case "java.lang.Float" -> {
				super.writeFloat((float) value);
				yield 'F';
			}
			case "java.lang.Integer" -> {
				super.writeInt((int) value);
				yield 'I';
			}
			case "java.lang.Long" -> {
				super.writeLong((long) value);
				yield 'J';
			}
			case "java.lang.Short" -> {
				super.writeShort((short) value);
				yield 'S';
			}
			case "java.lang.String" -> {
				super.writeUTF((String) value);
				yield 'T';
			}
			default -> {
				throw new IllegalArgumentException("Unexpected value-> " + name);
			}
		};
		return writtenType;
	}
}
