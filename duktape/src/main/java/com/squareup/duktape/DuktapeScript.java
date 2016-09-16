package com.squareup.duktape;

import java.io.Closeable;
import java.io.IOException;

public final class DuktapeScript implements Closeable {
	private final long context;

	DuktapeScript(long context, String sourceCode) {
		this.context = context;
		loadScript(context, sourceCode);
	}

	public void put(String key, double value) {
		putDouble(context, key, value);
	}

	public double getDouble(String key) {
		return getDouble(context, key);
	}

	public void put(String key, String value) {
		putString(context, key, value);
	}

	public String getString(String key) {
		return getString(context, key);
	}

	public void put(String key, long value) {
		putLong(context, key, value);
	}

	public long getLong(String key) {
		return getLong(context, key);
	}

	public void put(String key, boolean value) {
		putBoolean(context, key, value);
	}

	public boolean getBoolean(String key) {
		return getBoolean(context, key);
	}

	public boolean isNull(String key) {
		return isNull(context, key);
	}

	public String callFunction(String name, Object... args) {
		return callFunction(context, name, args);
	}

	@Override public void close() throws IOException {
		closeScriptContext(context);
	}

	private static native void loadScript(long context, String sourceCode);
	private static native void closeScriptContext(long context);
	private static native void putDouble(long context, String key, double value);
	private static native double getDouble(long context, String key);
	private static native void putString(long context, String key, String value);
	private static native String getString(long context, String key);
	private static native void putLong(long context, String key, long value);
	private static native long getLong(long context, String key);
	private static native void putBoolean(long context, String key, boolean value);
	private static native boolean getBoolean(long context, String key);
	private static native boolean isNull(long context, String key);
	private static native String callFunction(final long context, final String key, Object... args);
}
