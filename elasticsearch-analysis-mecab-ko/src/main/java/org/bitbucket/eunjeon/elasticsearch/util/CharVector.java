package org.bitbucket.eunjeon.elasticsearch.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CharVector implements CharSequence, Comparable<CharSequence>, Serializable {
	private static final long serialVersionUID = 1L;

	private char[] array;
	private int offset;
	private int length;
	protected int hash;

	private boolean ignoreCase;

	public CharVector() { }

	public CharVector(CharSequence str) { this(str, false); }

	public CharVector(CharSequence str, boolean ignoreCase) {
		if (str != null) {
			array = String.valueOf(str).toCharArray();
			offset = 0;
			length = array.length;
		} else {
			array = new char[0];
			offset = 0;
			length = 0;
		}
		this.ignoreCase = ignoreCase;
	}

	public CharVector(char[] array) {
		this(array, 0, array.length);
	}

	public CharVector(char[] array, boolean ignoreCase) {
		this(array, 0, array.length, ignoreCase);
	}

	public CharVector(char[] array, int offset, int length) {
		this(array, offset, length, false);
	}

	public CharVector(char[] array, int offset, int length, boolean ignoreCase) {
		this.array = array;
		this.offset = offset;
		this.length = length;
		this.hash = 0;
		this.ignoreCase = ignoreCase;
	}

	public void init(char[] array, int offset, int length) {
		this.array = array;
		this.offset = offset;
		this.length = length;
		this.hash = 0;
	}

	public void init(int offset, int length) {
		this.offset = offset;
		this.length = length;
		this.hash = 0;
	}

	public void offset(int offset) {
		this.offset = offset;
		this.hash = 0;
	}

	public void length(int length) {
		this.length = length;
		this.hash = 0;
	}

	public void ignoreCase() {
		if (!ignoreCase) {
			this.ignoreCase = true;
			hash = 0;
		}
	}

	public void verifyCase() {
		if (ignoreCase) {
			this.ignoreCase = false;
			hash = 0;
		}
	}

	public boolean isIgnoreCase() {
		return ignoreCase;
	}

	public int hashCode() {
		// 해시코드는 대소문자 구분없이 모두 대문자 기준으로 만들어준다.
		if (hash > 0) { return hash; }
		int h = 0;
		int off = offset;
		for (int i = 0; i < length; i++) {
			int ch = array[off++];
			ch = toUpperChar(ch);
			h = 31 * h + ch;
		}
		hash = h;
		return h;
	}

	public CharVector trim() {
		while (length > 0 && array[offset] == ' ') {
			offset++;
			length--;
		}
		while (length > 0 && array[offset + length - 1] == ' ') {
			length--;
		}
		hash = 0;
		return this;
	}

	@Override
	public String toString() {
		if (length > 0) {
			return new String(array, offset, length);
		} else {
			return "";
		}
	}

	@Override
	public boolean equals(Object anObject) {
		if (this == anObject) { return true; }
		if (anObject instanceof CharVector) {
			CharVector anotherArray = (CharVector) anObject;
			int n = length;
			if (n == anotherArray.length) {
				if (ignoreCase || anotherArray.ignoreCase) {
					// 둘중 하나라도 ignorecase이면 ignorecase로 비교한다.
					for (int i = 0; i < length; i++) {
						if (toUpperChar(charAt(i)) != toUpperChar(anotherArray.charAt(i))) {
							return false;
						}
					}
				} else {
					for (int i = 0; i < length; i++) {
						if (charAt(i) != anotherArray.charAt(i)) {
							return false;
						}
					}
				}
				return true;
			}
		} else if (anObject instanceof CharSequence) {

		}
		return false;
	}

	@Override
	public CharVector clone() {
		// share array reference
		CharVector cv = new CharVector(array, offset, length, ignoreCase);
		cv.hash = hash;
		return cv;
	}

	@Override
	public int compareTo(CharSequence cs) {
		int len1 = this.length;
		int len2 = cs.length();
		int minlen = len1;
		if (minlen > len2) {
			minlen = len2;
		}
		for (int cinx = 0; cinx < minlen; cinx++) {
			char c1 = this.charAt(cinx);
			char c2 = cs.charAt(cinx);
			if (c1 == c2) {
			} else if (c1 > c2) {
				return 1;
			} else if (c1 < c2) {
				return -1;
			}
		}
		if (len1 == len2) {
			return 0;
		} else if (len1 > len2) {
			return 1;
		} else if (len1 < len2) {
			return -1;
		}
		return 0;
	}

	@Override
	public char charAt(int inx) {
		char ch = array[offset + inx];
		if (ignoreCase) {
			if ((ch <= 'z' && ch >= 'a')) {
				// 소문자이면..
				ch -= 32;
			}
		}
		return ch;
	}

	private char toUpperChar(int ch) {
		if ((ch <= 'z' && ch >= 'a')) {
			// 소문자이면..
			ch -= 32;
		}
		return (char) ch;
	}

	// 내부 공백을 삭제해준다.
	public CharVector removeWhitespaces() {
		int len = 0;
		for (int i = 0; i < length; i++) {
			if (array[offset + i] != ' ') {
				array[offset + len++] = array[offset + i];
			}
		}
		length = len;
		hash = 0;
		return this;
	}

	public boolean hasWhitespaces() {
		for (int i = 0; i < length; i++) {
			if (array[offset + i] == ' ') {
				return true;
			}
		}
		return false;
	}

	public char[] array() {
		return array;
	}

	public int offset() {
		return offset;
	}

	@Override
	public int length() {
		return length;
	}

	@Override
	public CharSequence subSequence(int startIndex, int endIndex) {
		CharVector cv = new CharVector();
		cv.array = this.array;
		cv.offset = this.offset + startIndex;
		cv.length = endIndex - startIndex + 1;
		return cv;
	}

	public List<CharVector> splitByWhitespace() {
		return splitByWhitespace(this);
	}

	public static CharVector valueOf(Object o) {
		CharVector ret;
		if (o == null) {
			ret = null;
		} else if (o instanceof CharVector) {
			ret = (CharVector) o;
		} else {
			ret = new CharVector(String.valueOf(o));
		}
		return ret;
	}

	public static List<CharVector> splitByWhitespace(CharVector term) {
		int start = 0;
		boolean isPrevWhitespace = true;
		List<CharVector> list = new ArrayList<>();
		for (int i = 0; i < term.length(); i++) {
			char ch = term.charAt(i);
			if (ch == ' ') {
				if (!isPrevWhitespace) {
					list.add(new CharVector(term.array(), start + term.offset(), i - start, term.isIgnoreCase()));
				}
				start = i + 1;
				isPrevWhitespace = true;
			} else {
				isPrevWhitespace = false;
			}
		}
		if (!isPrevWhitespace) {
			list.add(new CharVector(term.array(), start + term.offset(), term.length() - start, term.isIgnoreCase()));
		}
		return list;
	}
}
