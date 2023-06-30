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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CustomDataOutputStream extends DataOutputStream {
	public CustomDataOutputStream(final OutputStream out) {
		super(out);
	}

	public char writeValue(final Object value) throws IOException {
		final String name = value.getClass().getName();
		switch (name) {
			case "java.lang.Boolean": {
				super.writeBoolean((boolean) value);
				return 'Z';
			}
			case "java.lang.Byte": {
				super.writeByte((byte) value);
				return 'B';
			}
			case "java.lang.Char": {
				super.writeChar((char) value);
				return 'C';
			}
			case "java.lang.Double": {
				super.writeDouble((double) value);
				return 'D';
			}
			case "java.lang.Float": {
				super.writeFloat((float) value);
				return 'F';
			}
			case "java.lang.Integer": {
				super.writeInt((int) value);
				return 'I';
			}
			case "java.lang.Long": {
				super.writeLong((long) value);
				return 'J';
			}
			case "java.lang.Short": {
				super.writeShort((short) value);
				return 'S';
			}
			case "java.lang.String": {
				super.writeUTF((String) value);
				return 'T';
			}
			default:
				throw new IllegalArgumentException("Unexpected value: " + name);
		}
	}
}
