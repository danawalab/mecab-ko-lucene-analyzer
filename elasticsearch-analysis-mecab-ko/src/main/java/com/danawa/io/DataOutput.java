package com.danawa.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * Abstract base class for performing write operations of Lucene's low-level data types.
 * 
 * <p>
 * {@code DataOutput} may only be used from one thread, because it is not thread safe (it keeps internal state like file
 * position).
 */
public abstract class DataOutput extends OutputStream {

	private static byte ZERO = 0;
	private static byte ONE = 1;

	public boolean seekPositionSupported() {
		return false;
	}

	public long position() throws IOException {
		throw new UnsupportedOperationException();
	}

	public void seek(long position) throws IOException {
		throw new UnsupportedOperationException();
	}

	public abstract void reset() throws IOException;

	/**
	 * Writes a single byte.
	 * <p>
	 * The most primitive data type is an eight-bit byte. Files are accessed as sequences of bytes. All other data types are
	 * defined as sequences of bytes, so file formats are byte-order independent.
	 * 
	 * @see IndexInput#readByte()
	 */
	public abstract void writeByte(byte b) throws IOException;

	public void writeByte(int b) throws IOException {
		writeByte((byte) b);
	}

	@Override
	public void write(int b) throws IOException {
		writeByte((byte) b);
	}

	public void writeBytes(byte[] b) throws IOException {
		writeBytes(b, b.length);
	}

	/**
	 * Writes an array of bytes.
	 * 
	 * @param b
	 *            the bytes to write
	 * @param length
	 *            the number of bytes to write
	 * @see IndexInput#readBytes(byte[],int,int)
	 */
	public void writeBytes(byte[] b, int length) throws IOException {
		writeBytes(b, 0, length);
	}

	/**
	 * Writes an array of bytes.
	 * 
	 * @param b
	 *            the bytes to write
	 * @param offset
	 *            the offset in the byte array
	 * @param length
	 *            the number of bytes to write
	 * @see IndexInput#readBytes(byte[],int,int)
	 */
	public abstract void writeBytes(byte[] b, int offset, int length) throws IOException;

	public void writeBytes(BytesBuffer dst) throws IOException {
		writeBytes(dst.array(), dst.pos(), dst.length());
	}

	/**
	 * Writes an int as four bytes.
	 * <p>
	 * 32-bit unsigned integer written as four bytes, high-order bytes first.
	 * 
	 * @see IndexInput#readInt()
	 */
	public void writeInt(int i) throws IOException {
		writeByte((byte) (i >> 24));
		writeByte((byte) (i >> 16));
		writeByte((byte) (i >> 8));
		writeByte((byte) i);
	}

	/**
	 * Writes a short as two bytes.
	 * 
	 * @see IndexInput#readShort()
	 */
	public void writeShort(short i) throws IOException {
		writeByte((byte) (i >> 8));
		writeByte((byte) i);
	}

	public final void writeVInt(int i) throws IOException {
		while ((i & ~0x7F) != 0) {
			writeByte((byte) ((i & 0x7F) | 0x80));
			i >>>= 7;
		}
		writeByte((byte) i);
	}

	/**
	 * Writes a long as eight bytes.
	 * <p>
	 * 64-bit unsigned integer written as eight bytes, high-order bytes first.
	 * 
	 * @see IndexInput#readLong()
	 */
	public void writeLong(long i) throws IOException {
		writeInt((int) (i >> 32));
		writeInt((int) i);
	}

	/**
	 * Writes an long in a variable-length format. Writes between one and nine bytes. Smaller values take fewer bytes. Negative
	 * numbers are not supported.
	 * <p>
	 * The format is described further in {@link IndexOutput#writeVInt(int)}.
	 * 
	 * @see IndexInput#readVLong()
	 */
	public final void writeVLong(long i) throws IOException {
		assert i >= 0L;
		while ((i & ~0x7FL) != 0L) {
			writeByte((byte) ((i & 0x7FL) | 0x80L));
			i >>>= 7;
		}
		writeByte((byte) i);
	}

	private static int COPY_BUFFER_SIZE = 16384;
	private byte[] copyBuffer;

	/** Copy numBytes bytes from input to ourself. */
	public void copyBytes(com.danawa.io.DataInput input, long numBytes) throws IOException {
		assert numBytes >= 0 : "numBytes=" + numBytes;
		long left = numBytes;
		if (copyBuffer == null) {
			copyBuffer = new byte[COPY_BUFFER_SIZE];
		}
		while (left > 0) {
			final int toCopy;
			if (left > COPY_BUFFER_SIZE) {
				toCopy = COPY_BUFFER_SIZE;
			} else {
				toCopy = (int) left;
			}
			input.readBytes(copyBuffer, 0, toCopy);
			writeBytes(copyBuffer, 0, toCopy);
			left -= toCopy;
		}
	}

	/**
	 * Writes a String map.
	 * <p>
	 * First the size is written as an {@link #writeInt(int) Int32}, followed by each key-value pair written as two consecutive
	 * {@link #writeString(String) String}s.
	 * 
	 * @param map
	 *            Input map. May be null (equivalent to an empty map)
	 */
	public void writeStringStringMap(Map<String, String> map) throws IOException {
		if (map == null) {
			writeInt(0);
		} else {
			writeInt(map.size());
			for (final Map.Entry<String, String> entry : map.entrySet()) {
				writeString(entry.getKey());
				writeString(entry.getValue());
			}
		}
	}

	/**
	 * Writes a String set.
	 * <p>
	 * First the size is written as an {@link #writeInt(int) Int32}, followed by each value written as a
	 * {@link #writeString(String) String}.
	 * 
	 * @param set
	 *            Input set. May be null (equivalent to an empty set)
	 */
	public void writeStringSet(Set<String> set) throws IOException {
		if (set == null) {
			writeInt(0);
		} else {
			writeInt(set.size());
			for (String value : set) {
				writeString(value);
			}
		}
	}

	public void writeUTF8String(String str) throws IOException {
		int charCount = str.length();
		writeVInt(charCount);
		int c;
		for (int i = 0; i < charCount; i++) {
			c = str.charAt(i);
			if (c <= 0x007F) {
				writeByte((byte) c);
			} else if (c > 0x07FF) {
				writeByte((byte) (0xE0 | c >> 12 & 0x0F));
				writeByte((byte) (0x80 | c >> 6 & 0x3F));
				writeByte((byte) (0x80 | c >> 0 & 0x3F));
			} else {
				writeByte((byte) (0xC0 | c >> 6 & 0x1F));
				writeByte((byte) (0x80 | c >> 0 & 0x3F));
			}
		}
	}

	public void writeAString(String str) throws IOException {
		if (str == null) {
			// null이면 길이 0으로 기록.
			str = "";
		}
		writeAString(str.toCharArray(), 0, str.length());
	}
	// 2byte로 기록한다.
	public void writeString(String str) throws IOException {
		if (str == null) {
			// null이면 길이 0으로 기록.
			str = "";
		}
		writeUString(str.toCharArray(), 0, str.length());
	}

	public void writeFloat(float v) throws IOException {
		writeInt(Float.floatToIntBits(v));
	}

	public void writeDouble(double v) throws IOException {
		writeLong(Double.doubleToLongBits(v));
	}

	/**
	 * Writes a boolean.
	 */
	public void writeBoolean(boolean b) throws IOException {
		writeByte(b ? ONE : ZERO);
	}

	public void writeAChar(int v) throws IOException {
		writeAChar(v, false);
	}

	public void writeAChar(int v, boolean upperCase) throws IOException {
		if (upperCase && v >= 97 && v <= 122) {
			v -= 32;
		}
		writeByte((byte) v);
	}

	public void writeUChar(int v) throws IOException {
		writeUChar(v, false);
	}

	public void writeUChar(int v, boolean upperCase) throws IOException {
		if (upperCase && v >= 97 && v <= 122) {
			v -= 32;
		}
		writeShort((short) v);
	}

	public void writeAString(char[] v, int start, int length) throws IOException {
		writeVInt(length);
		writeAChars(v, start, length);
	}

	public void writeUString(char[] v, int start, int length) throws IOException {
		writeVInt(length * 2);
		writeUChars(v, start, length);
	}

	public void writeAChars(char[] v, int start, int length) throws IOException {
		writeAChars(v, start, length, false);
	}

	public void writeAChars(char[] v, int start, int length, boolean upperCase) throws IOException {
		if (length == 0) { return; }
		int newLength = length;
		if (v.length > 0) {
			if (start >= v.length) {
				throw new IOException("start position exceeds array length. start=" + start + ", array.length=" + v.length);
			}
			if (start + length > v.length) {
				newLength = v.length - start;
			}
			for (int i = 0; i < newLength; i++) {
				writeAChar(v[start + i], upperCase);
			}
		} else {
			// 기록된바 없음.
			newLength = 0;
		}
		// 나머지 0으로 기록. length와 newLength 가 동일하다면 수행되지 않음.
		for (int i = 0; i < length - newLength; i++) {
			writeAChar(0);
		}
	}

	public void writeUChars(char[] v, int start, int length) throws IOException {
		writeUChars(v, start, length, false);
	}

	public void writeUChars(char[] v, int start, int length, boolean upperCase) throws IOException {
		if (length == 0) { return; }
		int newLength = length;
		if (v.length > 0) {
			if (start >= v.length) {
				throw new IOException("start position exceeds array length. start=" + start + ", array.length=" + v.length);
			}
			if (start + length > v.length) {
				newLength = v.length - start;
			}
			for (int i = 0; i < newLength; i++) {
				writeUChar(v[start + i], upperCase);
			}
		} else {
			// 기록된바 없음.
			newLength = 0;
		}
		// 나머지 0으로 기록. length와 newLength 가 동일하다면 수행되지 않음.
		for (int i = 0; i < length - newLength; i++) {
			writeUChar(0);
		}
	}

	public void writeStringArray(String[] array) throws IOException {
		writeVInt(array.length);
		for (String s : array) {
			writeString(s);
		}
	}

	/**
	 * Writes a string array, for nullable string, writes it as 0 (empty string).
	 */
	public void writeStringArrayNullable(String[] array) throws IOException {
		if (array == null) {
			writeVInt(0);
		} else {
			writeVInt(array.length);
			for (String s : array) {
				writeString(s);
			}
		}
	}

	public void writeMap(Map<String, Object> map) throws IOException {
		writeGenericValue(map);
	}

	public void writeGenericValue(Object value) throws IOException {
		if (value == null) {
			writeByte((byte) -1);
			return;
		}
		Class<?> type = value.getClass();
		if (type == String.class) {
			writeByte((byte) 0);
			writeString((String) value);
		} else if (type == Integer.class) {
			writeByte((byte) 1);
			writeInt((Integer) value);
		} else if (type == Long.class) {
			writeByte((byte) 2);
			writeLong((Long) value);
		} else if (type == Float.class) {
			writeByte((byte) 3);
			writeFloat((Float) value);
		} else if (type == Double.class) {
			writeByte((byte) 4);
			writeDouble((Double) value);
		} else if (type == Boolean.class) {
			writeByte((byte) 5);
			writeBoolean((Boolean) value);
		} else if (type == byte[].class) {
			writeByte((byte) 6);
			writeVInt(((byte[]) value).length);
			writeBytes(((byte[]) value));
		} else if (value instanceof List) {
			writeByte((byte) 7);
			List<?> list = (List<?>) value;
			writeVInt(list.size());
			for (Object o : list) {
				writeGenericValue(o);
			}
		} else if (value instanceof Object[]) {
			writeByte((byte) 8);
			Object[] list = (Object[]) value;
			writeVInt(list.length);
			for (Object o : list) {
				writeGenericValue(o);
			}
		} else if (value instanceof Map) {
			if (value instanceof LinkedHashMap) {
				writeByte((byte) 9);
			} else {
				writeByte((byte) 10);
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) value;
			writeVInt(map.size());
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				writeString(entry.getKey());
				writeGenericValue(entry.getValue());
			}
		} else if (type == Byte.class) {
			writeByte((byte) 11);
			writeByte((Byte) value);
		} else if (type == Date.class) {
			writeByte((byte) 12);
			writeLong(((Date) value).getTime());
		} else if (value instanceof BytesBuffer) {
			writeByte((byte) 14);
			writeVInt(((BytesBuffer) value).length());
			writeBytes((BytesBuffer) value);
		} else if (type == Short.class) {
			writeByte((byte) 16);
			writeShort((Short) value);
		} else {
			throw new IOException("Can't write type [" + type + "]");
		}
	}
}