package org.bitbucket.eunjeon.elasticsearch.dict.korean;

public class MorphUtil {

	private static final char[] CHOSEONG = { 'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ',
			'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ' };

	private static final char[] JUNGSEONG = { 'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ',
			'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ' };

	private static final char[] JONGSEONG = { '\0', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ',
			'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ' };

	
	private static final int JUNG_JONG_LENGTH = 21 * 28;
	private static final int JONG_LENGTH = 28;
	
	private static final int getCHOSEONGIDX(char ch) {
		int i = 0;
		for (; i < CHOSEONG.length; i++) {
			if (ch == CHOSEONG[i]) { return i; }
		}
		return -1;
	}
	
	private static final int getJUNGSEONGIDX(char ch) {
		int i = 0;
		for (; i < JUNGSEONG.length; i++) {
			if (ch == JUNGSEONG[i]) { return i; }
		}
		return -1;
	}
	
	private static final int getJONGSEONGIDX(char ch) {
		int i = 0;
		for (; i < JONGSEONG.length; i++) {
			if (ch == JONGSEONG[i]) { return i; }
		}
		return -1;
	}
	
	public static char makeChar(char ch, int mdl, int last) {
		ch -= 0xAC00;
		int first = ch / JUNG_JONG_LENGTH;
		return compound(first, mdl, last);
	}
	
	public static char compound(int first, int middle, int last) {
		return (char) (0xAC00 + first * JUNG_JONG_LENGTH + middle * JONG_LENGTH + last);
	}

	public static char getFirstElement(char c) {
		if (!isHangul(c)) {
			return c;
		}
		return CHOSEONG[(c - 0xAC00) / JUNG_JONG_LENGTH];
	}

	public static char getMiddleElement(char c) {
		if (!isHangul(c)) { return c; }
		c = (char) ((c - 0xAC00) % JUNG_JONG_LENGTH);
		return JUNGSEONG[c / JONG_LENGTH];
	}
	
	public static boolean hasLastElement(char c) {
		return getLastElementCode(c) > 0;
	}
	
	public static int getLastElementCode(char c) {
		if (!isHangul(c)) { return -1; }
		return (c - 0xAC00) % 28;
	}

	public static boolean isHangul(char c) {
		if (c < 0xAC00 || c > 0xD7A3) { return false; }
		return true;
	}

	public static int[] split(char c) {
		int sub[] = new int[3];
		c -= 0xAC00;
		sub[0] = c / JUNG_JONG_LENGTH; // 초성의 위치
		sub[1] = (c % JUNG_JONG_LENGTH) / JONG_LENGTH; // 중성의 위치
		sub[2] = c % JONG_LENGTH;// 종성의 위치
		return sub;
	}

	public static char combine(char... chars) {
		if (chars.length > 2) {
			return compound(getCHOSEONGIDX(chars[0]), getJUNGSEONGIDX(chars[1]), getJONGSEONGIDX(chars[2]));
		} else if (chars.length > 1) {
			return compound(getCHOSEONGIDX(chars[0]), getJUNGSEONGIDX(chars[1]), 0);
		} else if (chars.length == 1) {
			return chars[0];
		}
		return 0;
	}

	public static char[] decompose(char c) {
		char[] result = null;
		if (!isHangul(c)) {
			return new char[] { c };
		}
		c -= 0xAC00;
		char choseong = CHOSEONG[c / JUNG_JONG_LENGTH];
		c = (char) (c % JUNG_JONG_LENGTH);
		char jungseong = JUNGSEONG[c / JONG_LENGTH];
		char jongseong = JONGSEONG[c % JONG_LENGTH];
		if (jongseong != 0) {
			result = new char[] { choseong, jungseong, jongseong };
		} else {
			result = new char[] { choseong, jungseong };
		}
		return result;
	}
}