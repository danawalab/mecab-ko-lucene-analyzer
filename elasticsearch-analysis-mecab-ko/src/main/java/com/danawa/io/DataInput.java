package com.danawa.io;

import java.io.IOException;
import java.io.InputStream;
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
 * Abstract base class for performing read operations of Lucene's low-level data types.
 * 
 * <p>
 * {@code DataInput} may only be used from one thread, because it is not thread safe (it keeps internal state like file position).
 * To allow multithreaded use, every {@code DataInput} instance must be cloned before used in another thread. Subclasses must
 * therefore implement {@link #clone()}, returning a new {@code DataInput} which operates on the same underlying resource, but
 * positioned independently.
 */
public abstract class DataInput extends InputStream implements Cloneable {

	/**
	 * Reads and returns a single byte.
	 * 
	 * @see IndexOutput#writeByte(byte)
	 */
	public abstract byte readByte() throws IOException;
	
	public abstract long length() throws IOException;
	
	public long position() throws IOException {
		throw new UnsupportedOperationException();
	}

	public void seek(long position) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public int read() throws IOException {
		return readByte();
	}

	/**
	 * Reads a specified number of bytes into an array at the specified offset.
	 * 
	 * @param b
	 *            the array to read bytes into
	 * @param offset
	 *            the offset in the array to start storing bytes
	 * @param len
	 *            the number of bytes to read
	 * @see IndexOutput#writeBytes(byte[],int)
	 */
	public abstract void readBytes(byte[] b, int offset, int len) throws IOException;

	public void readBytes(BytesBuffer dst) throws IOException {
		readBytes(dst.bytes, dst.offset, dst.length());
	}

	/**
	 * Reads a specified number of bytes into an array at the specified offset with control over whether the read should be
	 * buffered (callers who have their own buffer should pass in "false" for useBuffer). Currently only
	 * {@link BufferedIndexInput} respects this parameter.
	 * 
	 * @param b
	 *            the array to read bytes into
	 * @param offset
	 *            the offset in the array to start storing bytes
	 * @param len
	 *            the number of bytes to read
	 * @param useBuffer
	 *            set to false if the caller will handle buffering.
	 * @see IndexOutput#writeBytes(byte[],int)
	 */
	public void readBytes(byte[] b, int offset, int len, boolean useBuffer) throws IOException {
		// Default to ignoring useBuffer entirely
		readBytes(b, offset, len);
	}

	/**
	 * Reads two bytes and returns a short.
	 * 
	 * @see IndexOutput#writeByte(byte)
	 */
	public short readShort() throws IOException {
		return (short) (((readByte() & 0xFF) << 8) | (readByte() & 0xFF));
	}

	/**
	 * Reads four bytes and returns an int.
	 * 
	 * @see IndexOutput#writeInt(int)
	 */
	public int readInt() throws IOException {
		return ((readByte() & 0xFF) << 24) | ((readByte() & 0xFF) << 16) | ((readByte() & 0xFF) << 8) | (readByte() & 0xFF);
	}

	/**
	 * Reads an int stored in variable-length format. Reads between one and five bytes. Smaller values take fewer bytes. Negative
	 * numbers are not supported.
	 * <p>
	 * The format is described further in {@link IndexOutput#writeVInt(int)}.
	 * 
	 * @see IndexOutput#writeVInt(int)
	 */
	public int readVInt() throws IOException {
		/*
		 * This is the original code of this method, but a Hotspot bug (see LUCENE-2975) corrupts the for-loop if readByte() is
		 * inlined. So the loop was unwinded! byte b = readByte(); int i = b & 0x7F; for (int shift = 7; (b & 0x80) != 0; shift +=
		 * 7) { b = readByte(); i |= (b & 0x7F) << shift; } return i;
		 */
		byte b = readByte();
		if (b >= 0) { return b; }
		int i = b & 0x7F;
		b = readByte();
		i |= (b & 0x7F) << 7;
		if (b >= 0) { return i; }
		b = readByte();
		i |= (b & 0x7F) << 14;
		if (b >= 0) { return i; }
		b = readByte();
		i |= (b & 0x7F) << 21;
		if (b >= 0) { return i; }
		b = readByte();
		// Warning: the next ands use 0x0F / 0xF0 - beware copy/paste errors:
		i |= (b & 0x0F) << 28;
		if ((b & 0xF0) == 0) { return i; }
		throw new IOException("Invalid vInt detected (too many bits)");
	}

	/**
	 * Reads eight bytes and returns a long.
	 * 
	 * @see IndexOutput#writeLong(long)
	 */
	public long readLong() throws IOException {
		return (((long) readInt()) << 32) | (readInt() & 0xFFFFFFFFL);
	}

	/**
	 * Reads a long stored in variable-length format. Reads between one and nine bytes. Smaller values take fewer bytes. Negative
	 * numbers are not supported.
	 * <p>
	 * The format is described further in {@link IndexOutput#writeVInt(int)}.
	 * 
	 * @see IndexOutput#writeVLong(long)
	 */
	public long readVLong() throws IOException {
		/*
		 * This is the original code of this method, but a Hotspot bug (see LUCENE-2975) corrupts the for-loop if readByte() is
		 * inlined. So the loop was unwinded! byte b = readByte(); long i = b & 0x7F; for (int shift = 7; (b & 0x80) != 0; shift
		 * += 7) { b = readByte(); i |= (b & 0x7FL) << shift; } return i;
		 */
		byte b = readByte();
		if (b >= 0) { return b; }
		long i = b & 0x7FL;
		b = readByte();
		i |= (b & 0x7FL) << 7;
		if (b >= 0) { return i; }
		b = readByte();
		i |= (b & 0x7FL) << 14;
		if (b >= 0) { return i; }
		b = readByte();
		i |= (b & 0x7FL) << 21;
		if (b >= 0) { return i; }
		b = readByte();
		i |= (b & 0x7FL) << 28;
		if (b >= 0) { return i; }
		b = readByte();
		i |= (b & 0x7FL) << 35;
		if (b >= 0) { return i; }
		b = readByte();
		i |= (b & 0x7FL) << 42;
		if (b >= 0) { return i; }
		b = readByte();
		i |= (b & 0x7FL) << 49;
		if (b >= 0) { return i; }
		b = readByte();
		i |= (b & 0x7FL) << 56;
		if (b >= 0) { return i; }
		throw new IOException("Invalid vLong detected (negative values disallowed)");
	}

	public final float readFloat() throws IOException {
		return Float.intBitsToFloat(readInt());
	}

	public final double readDouble() throws IOException {
		return Double.longBitsToDouble(readLong());
	}

	/**
	 * Reads a boolean.
	 */
	public final boolean readBoolean() throws IOException {
		return readByte() != 0;
	}

	public String readUTF8String() throws IOException {
		int charCount = readVInt();
		char[] chars = CachedCharArray.getCharArray(charCount);
		int c, charIndex = 0;
		while (charIndex < charCount) {
			c = readByte() & 0xff;
			switch (c >> 4) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				chars[charIndex++] = (char) c;
				break;
			case 12:
			case 13:
				chars[charIndex++] = (char) ((c & 0x1F) << 6 | readByte() & 0x3F);
				break;
			case 14:
				chars[charIndex++] = (char) ((c & 0x0F) << 12 | (readByte() & 0x3F) << 6 | (readByte() & 0x3F) << 0);
				break;
			}
		}
		return new String(chars, 0, charCount);
	}

	// ascii는 1byte로 읽고 나머지는 2byte로 읽는다.
	public String readString() throws IOException {
		return new String(readUString());
	}

	/**
	 * Returns a clone of this stream.
	 * 
	 * <p>
	 * Clones of a stream access the same data, and are positioned at the same point as the stream they were cloned from.
	 * 
	 * <p>
	 * Expert: Subclasses must ensure that clones may be positioned at different points in the input from each other and from the
	 * stream they were cloned from.
	 */
	@Override
	public DataInput clone() {
		try {
			return (DataInput) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new Error("This cannot happen: Failing to clone DataInput");
		}
	}

	/**
	 * Reads a Map&lt;String,String&gt; previously written with {@link IndexOutput#writeStringStringMap(Map)}.
	 */
	public Map<String, String> readStringStringMap() throws IOException {
		final Map<String, String> map = new HashMap<String, String>();
		final int count = readInt();
		for (int i = 0; i < count; i++) {
			final String key = readString();
			final String val = readString();
			map.put(key, val);
		}
		return map;
	}

	/**
	 * Reads a Set&lt;String&gt; previously written with {@link IndexOutput#writeStringSet(Set)}.
	 */
	public Set<String> readStringSet() throws IOException {
		final Set<String> set = new HashSet<String>();
		final int count = readInt();
		for (int i = 0; i < count; i++) {
			set.add(readString());
		}
		return set;
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> readMap() throws IOException {
		return (Map<String, Object>) readGenericValue();
	}

	public Object readGenericValue() throws IOException {
		byte type = readByte();
		switch (type) {
		case -1:
			return null;
		case 0:
			return readString();
		case 1:
			return readInt();
		case 2:
			return readLong();
		case 3:
			return readFloat();
		case 4:
			return readDouble();
		case 5:
			return readBoolean();
		case 6:
			int bytesSize = readVInt();
			byte[] value = new byte[bytesSize];
			readBytes(value, 0, bytesSize);
			return value;
		case 7:
			int size = readVInt();
			List<Object> list = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				list.add(readGenericValue());
			}
			return list;
		case 8:
			int size8 = readVInt();
			Object[] list8 = new Object[size8];
			for (int i = 0; i < size8; i++) {
				list8[i] = readGenericValue();
			}
			return list8;
		case 9:
			int size9 = readVInt();
			Map<String, Object> map9 = new LinkedHashMap<>(size9);
			for (int i = 0; i < size9; i++) {
				map9.put(readString(), readGenericValue());
			}
			return map9;
		case 10:
			int size10 = readVInt();
			Map<String, Object> map10 = new HashMap<>(size10);
			for (int i = 0; i < size10; i++) {
				map10.put(readString(), readGenericValue());
			}
			return map10;
		case 11:
			return readByte();
		case 12:
			return new Date(readLong());
		case 14:
			int size14 = readVInt();
			BytesBuffer b = new BytesBuffer(size14);
			readBytes(b);
			return b;
		case 16:
			return readShort();
		default:
			throw new IOException("Can't read unknown type [" + type + "]");
		}
	}

	public void skipVIntData() throws IOException{
		//읽어서 버린다.
		int len = readVInt();
		for (int i = 0; i < len; i++){
			readByte();
		}
	}

    public String readAStrings() throws IOException {
        int len = readVInt();
        char[] cs = new char[len];
        for (int i = 0; i < len; i++) {
			cs[i] = (char) readByte();
		}
        return new String(cs);
    }

	public char[] readAString() throws IOException {
		int len = readVInt();
		char[] cs = new char[len];
		for (int i = 0; i < len; i++) {
			cs[i] = (char) readByte();
		}
		return cs;
	}

	public char[] readUString() throws IOException {
		int byteSize = readVInt();
		char[] cs = new char[byteSize / 2];
		for (int i = 0; i < cs.length; i++) {
			cs[i] = (char) readShort();
		}
		return cs;
	}

	public char readAChar() throws IOException {
		return (char) readByte();
	}

	public char readUChar() throws IOException {
		return (char) readShort();
	}
}