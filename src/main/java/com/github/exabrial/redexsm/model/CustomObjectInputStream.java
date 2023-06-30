package com.github.exabrial.redexsm.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class CustomObjectInputStream extends ObjectInputStream {
	private final ClassLoader classLoader;

	public CustomObjectInputStream(final ClassLoader classLoader, final InputStream in) throws IOException {
		super(in);
		this.classLoader = classLoader;
	}

	@Override
	protected Class<?> resolveClass(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
		try {
			final String name = desc.getName();
			return Class.forName(name, false, classLoader);
		} catch (final ClassNotFoundException e) {
			return super.resolveClass(desc);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected Class<?> resolveProxyClass(final String[] interfaces) throws IOException, ClassNotFoundException {
		final List<Class<?>> loadedClasses = new ArrayList<>(interfaces.length);
		for (final String name : interfaces) {
			final Class<?> clazz = Class.forName(name, false, classLoader);
			loadedClasses.add(clazz);
		}
		return Proxy.getProxyClass(classLoader, loadedClasses.toArray(new Class[0]));
	}
}
