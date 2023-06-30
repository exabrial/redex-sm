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
