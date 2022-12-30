package org.bitbucket.eunjeon.elasticsearch.dict.korean;

import java.io.Serializable;

public class PosTagProbEntry implements Serializable {
	private static final long serialVersionUID = 1L;

	public TagProb tagProb;
	public PosTagProbEntry next;
	public CharSequence key;

	public PosTagProbEntry() {
	}

	public PosTagProbEntry(TagProb tagProb) {
		this.tagProb = tagProb;
	}

	public PosTagProbEntry(TagProb tagProb, CharSequence cvKey) {
		this.tagProb = tagProb;
		this.key = cvKey;
	}

	public void set(TagProb tagProb) {
		this.tagProb = tagProb;
	}

	public TagProb get() {
		return tagProb;
	}

	public PosTagProbEntry next(TagProb next) {
		this.next = new PosTagProbEntry(next);
		this.next.key = this.key;
		return this.next;
	}

	public PosTagProbEntry next() {
		return next;
	}

	public boolean contains(TagProb posTag) {
		PosTagProbEntry nextTagEntry = this;
		while (nextTagEntry != null) {
			if (nextTagEntry.get() == posTag) {
				return true;
			}
			nextTagEntry = nextTagEntry.next();
		}

		return false;
	}

	public boolean posContains(PosTag posTag) {
		PosTagProbEntry nextTagEntry = this;
		while (nextTagEntry != null) {
			if (nextTagEntry.get().posTag == posTag) {
				return true;
			}
			nextTagEntry = nextTagEntry.next();
		}

		return false;
	}

	public enum PosTag {
		SOE, UNK, N, V, M, IC, J, E, EP, X, ALPHA/* 영문 */, XPN, EOE, GUESS, DIGIT, SYMBOL
	}

	public static class TagProb implements Serializable {
		private static final long serialVersionUID = 1L;
		private PosTag posTag;
		private double prob;
		public static final double MAX_PROB = -5.0;
		public static final double HIGH_PROB = -8.0;
		public static final double P11_PROB = -11.0;
		public static final double P12_PROB = -12.0;
		public static final double MID_PROB = -13.0;
		public static final double LOW_PROB = -14.0;
		public static final double MIN_PROB = -16.0;

		public static final TagProb UNK = new TagProb(PosTag.UNK);
		public static final TagProb GUESS = new TagProb(PosTag.GUESS);
		public static final TagProb DIGIT = new TagProb(PosTag.DIGIT, HIGH_PROB);
		public static final TagProb ALPHA = new TagProb(PosTag.ALPHA, HIGH_PROB);
		public static final TagProb SYMBOL = new TagProb(PosTag.SYMBOL);
		public static final TagProb JOSA = new TagProb(PosTag.J, TagProb.MID_PROB);

		public static double getProb(String probType) {
			if (probType == null || probType.length() == 0) {
				return -1;
			}

			if (probType.equalsIgnoreCase("max")) {
				return TagProb.MAX_PROB;
			} else if (probType.equalsIgnoreCase("high")) {
				return TagProb.HIGH_PROB;
			} else if (probType.equalsIgnoreCase("mid")) {
				return TagProb.MID_PROB;
			} else if (probType.equalsIgnoreCase("p11")) {
				return TagProb.P11_PROB;
			} else if (probType.equalsIgnoreCase("p12")) {
				return TagProb.P12_PROB;
			} else if (probType.equalsIgnoreCase("low")) {
				return TagProb.LOW_PROB;
			} else if (probType.equalsIgnoreCase("min")) {
				return TagProb.MIN_PROB;
			}

			return MIN_PROB;
		}

		public TagProb(PosTag posTag) {
			this(posTag, MIN_PROB);
		}

		public TagProb(PosTag posTag, double prob) {
			this.posTag = posTag;
			this.prob = prob;
		}

		public PosTag posTag() {
			return posTag;
		}

		public double prob() {
			return prob;
		}

		@Override
		public boolean equals(Object obj) {
			TagProb another = (TagProb) obj;
			// 태그만 같다면 같은것으로 본다.
			return another.posTag == this.posTag;// && another.prob == this.prob;
		}

		public String toString() {
			return posTag.name() + "[" + prob + "]";
		}

		public String toShortString() {
			return posTag.name();
		}
	}
}