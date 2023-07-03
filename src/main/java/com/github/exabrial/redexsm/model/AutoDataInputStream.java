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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class AutoDataInputStream extends DataInputStream {
	public AutoDataInputStream(final InputStream in) {
		super(in);
	}

	public Object readType(final char type) throws IOException {
		final Object readValue = switch (type) {
			case 'Z' -> {
				yield readBoolean();
			}
			case 'B' -> {
				yield readByte();
			}
			case 'C' -> {
				yield readChar();
			}
			case 'D' -> {
				yield readDouble();
			}
			case 'F' -> {
				yield readFloat();
			}
			case 'I' -> {
				yield readInt();
			}
			case 'J' -> {
				yield readLong();
			}
			case 'S' -> {
				yield readShort();
			}
			case 'T' -> {
				yield readUTF();
			}
			default -> {
				throw new RuntimeException("Unknown type:" + type);
			}
		};
		return readValue;
	}
}
