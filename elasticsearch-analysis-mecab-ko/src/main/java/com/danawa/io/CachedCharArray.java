package com.danawa.io;

import java.lang.ref.SoftReference;

/**
 *
 */
public class CachedCharArray {

	static class Entry {
		char[] chars = new char[80];
	}

	private static final ThreadLocal<SoftReference<Entry>> cache = new ThreadLocal<SoftReference<Entry>>();

	static Entry instance() {
		SoftReference<Entry> ref = cache.get();
		Entry entry = ref == null ? null : ref.get();
		if (entry == null) {
			entry = new Entry();
			cache.set(new SoftReference<Entry>(entry));
		}
		return entry;
	}

	public static void clear() {
		cache.remove();
	}

	public static char[] getCharArray(int size) {
		Entry entry = instance();
		if (entry.chars.length < size) {
			entry.chars = new char[size];
		}
		return entry.chars;
	}
}