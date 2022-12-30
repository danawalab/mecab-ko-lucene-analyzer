package org.bitbucket.eunjeon.elasticsearch.dict.korean;

import org.apache.logging.log4j.Logger;
import org.bitbucket.eunjeon.elasticsearch.dict.korean.PosTagProbEntry.TagProb;
import org.bitbucket.eunjeon.elasticsearch.dict.korean.PosTagProbEntry.PosTag;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.ProductNameDictionary;
import org.bitbucket.eunjeon.elasticsearch.product.analysis.AnalyzeExceedException;
import org.bitbucket.eunjeon.elasticsearch.util.CharVector;
import org.elasticsearch.common.logging.Loggers;

import java.util.*;

import static org.bitbucket.eunjeon.elasticsearch.product.analysis.ProductNameTokenizer.*;

public class KoreanWordExtractor {
	private static Logger logger = Loggers.getLogger(KoreanWordExtractor.class, "");

	private ProductNameDictionary koreanDict;

	private PosTagProbEntry[][] tabular;
	private int[] status;
	private Set<CharVector> josaSet;
	// 최상의 후보가 만들어지면 바로 리턴. 기본 true;
	boolean fastResultOption = true;
	// 발견되었는지 여부.
	boolean fastResultFound;
	int remnantOffset;
	int remnantLength;
	boolean isUnicode;
	CharVector charVector = new CharVector();
	TabularStringer tabularStringer;
	EntryStringer entryStringer;

	private PriorityQueue<ExtractedEntry> queue = new PriorityQueue<ExtractedEntry>(8, new Comparator<ExtractedEntry>() {
		@Override
		public int compare(ExtractedEntry o1, ExtractedEntry o2) {
			return (int) (o2.totalScore() - o1.totalScore());
		}
	});
	
	private static final int QUEUE_MAX = 200;
	private static final int RESULT_MAX = 10;
	private List<ExtractedEntry> result = new ArrayList<>();

	protected char[] source;
	protected int offset;
	protected int length;

	public KoreanWordExtractor(ProductNameDictionary koreanDict) {
		this(koreanDict, 20);
	}

	public KoreanWordExtractor(ProductNameDictionary koreanDict, int tabularSize) {
		tabular = new PosTagProbEntry[tabularSize][];
		// tabular 파싱 초기화.
		for (int row = 0; row < tabular.length; row++) {
			tabular[row] = new PosTagProbEntry[row + 2];
		}
		status = new int[tabularSize];
		this.koreanDict = koreanDict;
		josaSet = new HashSet<CharVector>();
		String josaList = "은 는 이 가 을 를 에 과 와 의 로 만 께 에게 에서 으로 부터 라서 라고 께서 한테 처럼 같이 라는 하며 하고 까지 이라고 이라는 이라도 이라면 에서도 이기도";
		String[] jl = josaList.split("\\s");
		for (String j : jl) {
			josaSet.add(new CharVector(j));
		}
		tabularStringer = new TabularStringer();
		entryStringer = new EntryStringer();
	}

	public ProductNameDictionary dictionary() {
		return koreanDict;
	}

	public void setKoreanDic(ProductNameDictionary koreanDict) {
		this.koreanDict = koreanDict;
	}

	public void setFastResultOption(boolean fastResultOption) {
		this.fastResultOption = fastResultOption;
	}

	/*
	 * 음절을 조합하여 사전에서 찾아준다. 찾은 단어와 tag는 table에 저장한다.
	 */
	private ExtractedEntry doSegment() {
		CharVector cv = new CharVector(source, offset, length);
		// 길이가 1~2 단어는 완전매칭이 아니면 UNK이다.
		if (length == 1) {
			// FIXME 나중엔 뺀다. extractor를 제일 먼저받을 것이기 때문..
			// 조사이면 전체 조사로.
			List<TagProb> tag = koreanDict.find(cv);
			if (tag != null) {
				return new ExtractedEntry(length - 1, length, tag.get(0), offset);
			}
			if (isDigit(cv)) {
				return new ExtractedEntry(length - 1, length, TagProb.DIGIT, offset);
			} else if (isSymbol(cv)) {
				return new ExtractedEntry(length - 1, length, TagProb.SYMBOL, offset);
			}
			return new ExtractedEntry(length - 1, length, TagProb.UNK, offset);
		}
		List<TagProb> tag = koreanDict.find(cv);
		if (tag != null) {
			return new ExtractedEntry(length - 1, length, tag.get(0), offset);
		}
		int start = length - 1;
		for (int row = start; row >= 0; row--) {
			for (int column = row + 1; column >= 1; column--) {
				cv.init(offset + row - column + 1, column);
				List<TagProb> tagList = null;
				boolean isAlpha = false;
				if (isDigit(cv)) {
					// 무조건 셋팅.
					tabular[row][column] = new PosTagProbEntry(TagProb.DIGIT);
					status[row]++;
				} else if (isSymbol(cv)) {
					tabular[row][column] = new PosTagProbEntry(TagProb.SYMBOL);
					status[row]++;
				} else {
					if (isAlpha(cv)) {
						if (column == 1) {
							tabular[row][column] = new PosTagProbEntry(TagProb.ALPHA);
							/* 2019.3.28 @swsong ALPHA 는 분석발견이 아니라고 가정한다. */
							continue;
						}
						// 길이가 2이상이면 사전에서 확인해본다.
						isAlpha = true;
					}
					tagList = koreanDict.find(cv);
					if (tagList != null) {
						if (column == length) {
							// 완전일치시
							// logger.debug("Exact match {}", cv);
							return new ExtractedEntry(row, column, tagList.get(0), offset);
						}
						PosTagProbEntry chainedEntry = null;
						for (int i = 0; i < tagList.size(); i++) {
							TagProb tagProb = tagList.get(i);
							if (i == 0) {
								chainedEntry = new PosTagProbEntry(tagProb);
								tabular[row][column] = chainedEntry;
							} else {
								chainedEntry = chainedEntry.next(tagProb);
							}
						}
						status[row]++;
					} else {

						if (isAlpha) {
							// 영문자이면 null이 아닌 ALPHA로 셋팅한다.
							tabular[row][column] = new PosTagProbEntry(TagProb.ALPHA);
						} else {
							tabular[row][column] = null;
						}
					}

					if (column < 3) {
						// 1글자가 조사인지.
						if (josaSet.contains(cv)) {
							PosTagProbEntry entry = new PosTagProbEntry(TagProb.JOSA);
							if (tabular[row][column] == null) {
								tabular[row][column] = entry;
							} else {
								entry.next = tabular[row][column];
								tabular[row][column] = entry;
							}
							status[row]++;
						}
					}
				}
			}
		}
		return null;
	}

	// 유니코드 블럭인지 판단 (일부가 유니코드이면 유니코드 블럭으로 인식)
	private boolean isUnicode(char[] buffer, int offset, int length) {
		for (int inx = 0; inx < length; inx++) {
			char ch = buffer[offset + inx];
			if (ch > 127) {
				return true;
			}
		}
		return false;
	}

	// 단어가 전부숫자인지.
	private boolean isDigit(CharVector cv) {
		for (int i = 0; i < cv.length(); i++) {
			char ch = cv.charAt(i);
			if (ch >= '0' && ch <= '9') {
				// 숫자면 다음을 본다.
			} else {
				return false;
			}
		}
		return true;
	}
	
	private boolean isAlpha(CharVector cv) {
		for (int i = 0; i < cv.length(); i++) {
			char ch = cv.charAt(i);
			if ((ch >= 'a' && ch <= 'z') || ch >= 'A' && ch <= 'Z') {
				// 다음을 본다.
			} else {
				return false;
			}
		}
		return true;
	}

	private boolean isSymbol(CharVector cv) {
		for (int i = 0; i < cv.length(); i++) {
			int chInt = cv.charAt(i);
			if (!Character.isLetterOrDigit(chInt)) {
				// 다음을 본다.
			} else {
				return false;
			}
		}
		return true;
	}
	
	private void makeResult() {
		int headRow = -1;
		for (int row = length - 1; row >= 0; row--) {
			if (status[row] > 0) {
				// 분석결과가 존재하는지.
				headRow = row;
				break;
			}
		}

		try {
			if (headRow == -1) {
				// 통째 미등록어.
				addResult(new ExtractedEntry(length - 1, length, TagProb.UNK, offset));
				return;
			}
			// 최초 char부터의 단어매칭이 없다면.
			// 예를들어 "대한민국"분석시 "대한"만 사전에 있어서 "민국"은 결과가 없을경우.
			if (headRow < length - 1) {
				// 뒷부분을 미등록어로 처리한다. "대한(N)+민국(UNK)" 이 된다.
				ExtractedEntry tail = new ExtractedEntry(length - 1, length - 1 - headRow, TagProb.UNK, offset);
				connectAllTo(headRow, tail);
			} else {
				connectAllTo(headRow, null);
			}
			ExtractedEntry tail = null;
			// logger.trace("Q:{}", queue);
			while ((tail = queue.poll()) != null) {
				// logger.trace("T:{}", tail);
				int connectRow = tail.row() - tail.column();
				if (status[connectRow] > 0) {
					connectAllTo(connectRow, tail);
				} else {
					//
					// TODO 앞에 붙을 단어가 없으면 row를 줄여가면서 존재하는 단어를 찾은후 connectAllTo를 붙인다.
					//
					connectTo(null, connectRow, -1, tail);
				}
			}
		} catch (AnalyzeExceedException e) {
			// 분석을 중단하고 탈출한다.
			logger.debug("Analyze exceed : " + e.getMessage());
		}
	}

	private int connectAllTo(int headRow, ExtractedEntry tail) throws AnalyzeExceedException {
		PosTagProbEntry[] rowData = tabular[headRow];
		int found = 0;
		// 최장길이부터 찾는다.
		for (int headColumn = headRow + 1; headColumn > 0; headColumn--) {
			if (rowData[headColumn] != null) {
				PosTagProbEntry tagEntry = rowData[headColumn];
				entryStringer.set(tagEntry, source, headRow, headColumn, offset);
				/* 2019.3.28 @swsong 알파벳은 무조건 성공분석으로 잡혀서 사전에 없는 단어들도 후보가 되어 ALPHA를 건너뛰게 한다. */
				if (tagEntry.tagProb.posTag() != PosTag.ALPHA) {
					connectTo(tagEntry, headRow, headColumn, tail);
					found++;
				}
			}

			// 갯수만큼 다 찾았으면 일찍 종료한다.
			if (found >= status[headRow]) {
				break;
			}

		}
		// 추가 20190725 (영문인경우 모두 분석되어야 분석성공으로 만들도록)
		// logger.trace("CHECK:{} / {}", isUnicode, result);
		if (!isUnicode) {
			for (int inx = 0; inx < result.size(); inx++) {
				if (result.get(inx).last().posTag() == PosTag.UNK) {
					result.remove(inx);
					inx--;
				}
			}
		}
		return found;
	}

	private int connectTo(PosTagProbEntry headTagEntry, int headRow, int headColumn, ExtractedEntry tail) throws AnalyzeExceedException {
		// entryStringer.set(headTagEntry, source, headRow, headColumn, offset);
		// logger.trace("CONNECTTO:{}/{}", entryStringer, tail);
		// headColumn = -1 이면 앞쪽에 연결될 단어가 없는것이다.
		if (tail == null) {
			// 처음
			int found = 0;
			while (headTagEntry != null) {
				ExtractedEntry headEntry = new ExtractedEntry(headRow, headColumn, headTagEntry.get(), offset);
				if (headEntry.row() - headEntry.column() < 0) {
					addResult(headEntry);
				} else {
					addQueue(headEntry);
				}
				headTagEntry = headTagEntry.next;
				found++;
			}
			// 바로리턴.
			return found;
		}

		if (headTagEntry == null) {
			// 해당 row의 모든 column을 확인해본다.
			for (int column = 1; column <= headRow + 1; column++) {
				int row2 = headRow - column;
				// head앞에 결합가능한 것이 있다면 현재 head를 미등록처 처리하고 링크로 이어준다.
				// 앞쪽에 결합가능한것이 없으면 현재 head는 버린다.
				// row2 < 0 는 어절의 처음에 도달한것임.
				if (row2 < 0 || status[row2] > 0) {
					// 2014-1-27처리..
				}
			}
			return 1;
		}
		
		int found = 0;
		while (headTagEntry != null) {
			if (isConnectableByRule(headTagEntry.get(), headRow, headColumn, tail)) {
				ExtractedEntry newTail = modifyAndConnect(headTagEntry.get(), headRow, headColumn, tail);
				if (newTail == null) {
					// null이면 버리는것이므로 다음으로..
				} else {
					if (newTail.row() - newTail.column() < 0) {
						addResult(newTail);
					} else {
						addQueue(newTail);
					}
					found++;
				}
			}
			headTagEntry = headTagEntry.next;
		}
		return found;
	}
	
	protected void addQueue(ExtractedEntry entry) throws AnalyzeExceedException {
		if (fastResultFound) {
			return;
		}
		// 결과로 넣음.
		// logger.trace("ADD-QUEUE:{}", entry);
		queue.add(entry);
		if (queue.size() >= QUEUE_MAX) {
			throw new AnalyzeExceedException("Queue size exceed " + queue.size() + " : " + new String(source));
		}
	}

	protected void addResult(ExtractedEntry entry) throws AnalyzeExceedException {
		// logger.trace("ADD-RESULT:{}", entry);
		ExtractedEntry e = finalCheck(entry);
		if (e == null) {
			return;
		}
		// 결과로 넣음.
		result.add(entry);

		// 짧은 문장에서는 사용하지 않음.
		if (fastResultOption && length > 6) {
			fastResultFound = true;
			queue.clear();
		}

		if (result.size() >= RESULT_MAX) {
			ExtractedEntry tmpResult = getHighResult();
			result.clear();
			if (tmpResult != null) {
				result.add(tmpResult);
			}
		}
	}
	
	public int setInput(char[] buffer, int length) {
		return setInput(buffer, 0, length);
	}
	
	public int setInput(char[] buffer, int offset, int length) {
		remnantOffset = 0;
		remnantLength = 0;
		isUnicode = isUnicode(buffer, offset, length);
		Arrays.fill(status, 0);
		for (int row = 0; row < tabular.length; row++) {
			for (int col = 0; col < tabular[row].length; col++) {
				tabular[row][col] = null;
			}
		}
		result.clear();
		
		String type = null;
		String ptype = null;
		String pptype = null;
		if (length > tabular.length) {
			logger.trace("LENGTH IS OVER THAN {} / {} / {}", length, tabular.length, offset);
			// 내부적으로 자를 수 있는 기준을 살펴 본다.
			// 자를수 있는 기준은 다음과 같다.
			// 한글 사이의 특수 문자. ( & 제외 : 존슨&존스 등 )
			for (int inx = offset + length; inx > offset; inx--) {
				pptype = ptype;
				ptype = type;
				type = getType(buffer[inx - 1]);
				if (logger.isTraceEnabled()) {
					logger.trace("PP:{}/P:{}/T:{}/C:{} {} [{}/{}/{} | {}/{}/{}]", pptype, ptype, type, buffer[inx - 1],
						inx - offset,
						(pptype != null && (pptype != ALPHA && pptype != NUMBER)),
						(inx < buffer.length && ptype == SYMBOL && buffer[inx] != '&'),
						(type != null), (pptype != null),
						(inx < buffer.length && ptype == SYMBOL && buffer[inx] != '&'),
						(type != null && (type != ALPHA && type != NUMBER)));
				}
				if (((pptype != null && (pptype != ALPHA && pptype != NUMBER))
						&& (inx < buffer.length && ptype == SYMBOL && buffer[inx] != '&')
						&& (type != null))
						|| ((pptype != null) && (inx < buffer.length && ptype == SYMBOL && buffer[inx] != '&')
							&& (type != null && (type != ALPHA && type != NUMBER)))) {
					logger.trace("LENGTH:{} / {}", length - offset, tabular.length);
					length = inx - offset;
					if (length <= tabular.length) {
						logger.trace("BREAK INTO {}", length);
						break;
					}
				}
			}
			//여기까지 와서 찾지 못했다면, 앞에서부터 최초 타입이 달라지는 순간 끊어준다.
			//영숫자+기호 : 한글
			if (length > tabular.length) {
				type = null;
				for (int inx = offset; inx < (offset + length); inx++) {
					ptype = type;
					type = getType(buffer[inx]);
					if (ptype != null && (
						( (type == ALPHA || type == NUMBER || type == SYMBOL)
							&& !(ptype == ALPHA || ptype == NUMBER || ptype == SYMBOL) )
						|| (!(type == ALPHA || type == NUMBER || type == SYMBOL)
							&& (ptype == ALPHA || ptype == NUMBER || ptype == SYMBOL)) )) {
						length = inx - offset;
					}
				}
			}
			
			if (length > tabular.length) {
				if (logger.isTraceEnabled()) {
					logger.trace("CUT TABULAR SIZE : {}/ {} -> {}", length, new String(buffer, offset, length), new String(buffer, offset, tabular.length));
				}
				remnantOffset = tabular.length;
				remnantLength = length - tabular.length;
				length = tabular.length;
			}
		}
		length = setInput0(buffer, offset, length);
		return length;
	}
	
	private int setInput0(char[] buffer, int offset, int length) {
		this.source = buffer;
		queue.clear();
		result.clear();
		// tabluar초기화.
		this.offset = offset;
		this.length = length;
		// this.flushCount = 0;
		fastResultFound = false;
		return length;
	}

	public ExtractedEntry extract() {
		tabularStringer.set(tabular, source, status, length, offset);
		ExtractedEntry.source = source;
		ExtractedEntry e = extract0();
		ExtractedEntry last = e.last();
		while (remnantLength > 0) {
			int len = Math.min(tabular.length, remnantLength);
			// 자른다.
			setInput0(source, remnantOffset, len);
			ExtractedEntry r = extract0();
			if (r != null) {
				// 잘못된 데이터. 순서적으로 나올수 없는 조합.
				if (!(last.offset() + last.column() > r.offset())) {
					last.next(r);
					last = r.last();
				}
			}
			remnantOffset += len;
			remnantLength -= len;
		}
		return e;
	}
	
	public ExtractedEntry extract0() {
		ExtractedEntry e = doSegment();
		if (e != null) {
			return e;
		}
		// logger.trace(tabularStringer);
		makeResult();
		return getBestResult();
	}

	public List<ExtractedEntry> getAllResult() {
		return result;
	}
	
	private ExtractedEntry getHighResult() {
		ExtractedEntry highEntry = null;
		// logger.trace("RESULT:{}", result);
		for (int k = 0; k < result.size(); k++) {
			ExtractedEntry entry = result.get(k);
			entry = finalCheck(entry);
			if (entry == null) {
				continue;
			}
			charVector.init(source, entry.offset(), entry.column());

			if (highEntry == null) {
				highEntry = entry;
			} else {
				if (isBetterThan(entry, highEntry)) {
					// logger.trace("HIGHER:{} / {}", entry, highEntry);
					highEntry = entry;
				}
			}
		}
		return highEntry;
	}

	public ExtractedEntry getBestResult() {
		ExtractedEntry bestEntry = getHighResult();
		if (bestEntry == null) {
			// 통째 미등록어.
			bestEntry = new ExtractedEntry(length - 1, length, TagProb.UNK, offset);
			// logger.trace("UNKNOWN:{}", bestEntry);
			return bestEntry;
		}
		return bestEntry;
	}
	
	public int getTabularSize() {
		return tabular.length;
	}

	/*
	 * 두 PosTag간의 룰기반 접속문법검사
	 */
	protected boolean isConnectableByRule(TagProb headTagProb, int headRow, int headColumn, ExtractedEntry tail) {
		//숫자끼리는 과분석된것이므로 연결해주지 않는다. 제일 긴 숫자가 사용하도록함.
		if (headTagProb.posTag() == PosTag.DIGIT && tail.tagProb().posTag() == PosTag.DIGIT) {
			return false;
		}
		if (headTagProb.posTag() == PosTag.ALPHA && tail.tagProb().posTag() == PosTag.ALPHA) {
			return false;
		}
		if (headTagProb.posTag() == PosTag.SYMBOL && tail.tagProb().posTag() == PosTag.SYMBOL) {
			return false;
		}
		if (headTagProb.posTag() != PosTag.ALPHA && headTagProb.posTag() != PosTag.DIGIT && headTagProb.posTag() != PosTag.SYMBOL) {
			// //은,이 와 는,가 일때 앞의 단어에 받침이 있는 지 확인.
			if (tail.tagProb().posTag() == PosTag.J && tail.column() == 1) {
				// "은 는 이 가 을 를 에 과 와 의 께";
				char ch = source[offset + tail.row() - tail.column() + 1];
				if (ch == '은' || ch == '이' || ch == '을' || ch == '과') {
					if (!MorphUtil.hasLastElement(source[offset + headRow])) {
						return false;
					}
				} else if (ch == '는' || ch == '가' || ch == '를' || ch == '와') {
					if (MorphUtil.hasLastElement(source[offset + headRow])) {
						return false;
					}
				}
			}
		}

		// 두개 모두 한글자분석이면서 점수가 -12이하인것이 하나라도 있으면 통짜로 미등록어처리.
		if (headColumn == 1 && tail.column() == 1 && headTagProb.posTag() != PosTag.J && tail.posTag() != PosTag.J) {
			if (tabular[tail.row()][tail.column() + headColumn] != null) {
				// 단어가 존재하므로 버린다.
				return false;
			}
		}
		//3개연속 불허용.
		return true;
	}

	/*
	 * 두 엔트리를 접속시 합치거나 이어붙이는 로직을 구현한다.
	 */
	protected ExtractedEntry modifyAndConnect(TagProb tagProb, int row, int column, ExtractedEntry tail) {
		ExtractedEntry newEntry = new ExtractedEntry(row, column, tagProb, offset);
		return newEntry.next(tail);
	}

	private ExtractedEntry finalCheck(ExtractedEntry headEntry) {
		// 첫글자 조사버림.
		if (headEntry.posTag() == PosTag.J) {
			return null;
		}

		int count = 0;
		if (headEntry.entryCount() >= 2) {
			ExtractedEntry current = headEntry;
			while (current != null) {
				if (current.column() > 1) {
					break;
				} else {
					count++;
				}
				current = current.next();
			}
		}

		if (count == headEntry.entryCount()) {
			if (headEntry.last().posTag() == PosTag.J) {
				return headEntry;
			}
			return null;
		}
		/*
		 * 한글자 + GUESS(or -15미만) 결합은 합친다.
		 */
		return headEntry;
	}
	
	/*
	 * Best 결과를 뽑을때 사용하는 비교로직.
	 */
	protected boolean isBetterThan(ExtractedEntry entry, ExtractedEntry bestEntry) {
		// 적게 잘린쪽이 우선.
		// 2014-1-27 처리.. 이렇게 하면 무조건 항상 통 UNK가 나오게 된다.

		// 점수가 큰쪽이 우선.
		if (entry.totalScore() > bestEntry.totalScore()) {
			return true;
		}
		return false;
	}

	public static class ExtractedEntry implements Cloneable {
		private static char[] source;
		private int row;
		private int column;
		private TagProb tagProb;
		private ExtractedEntry next; // 다음 엔트리.
		private double score; // 최종 score이다. head가 next이후의 score의 합산을 가지고 있게된다.
		private boolean extracted;
		private int offset;
		
		public ExtractedEntry(int row, int column, TagProb tagProb) {
			this(row, column, tagProb, (double) 0);
		}

		public ExtractedEntry(int row, int column, TagProb tagProb, double scoreAdd) {
			this.row = row;
			this.column = column;
			this.tagProb = tagProb;
			this.score += (tagProb.prob() + scoreAdd);
			// logger.trace("NEWENTRY:{}", this);
		}
		
		public ExtractedEntry(int row, int column, TagProb tagProb, int offset) {
			this.row = row;
			this.column = column;
			this.tagProb = tagProb;
			this.score += tagProb.prob();
			this.offset = offset;
			// logger.trace("NEWENTRY:{}", this);
		}

		public TagProb tagProb() {
			return tagProb;
		}
		
		public PosTag posTag() {
			return tagProb.posTag();
		}

		public void tagProb(TagProb tagProb) {
			// 이전것을 빼고.
			this.score -= this.tagProb.prob();
			this.tagProb = tagProb;
			this.score += tagProb.prob();
		}

		public int offset() {
			return offset + row - column + 1;
		}

		public int row() {
			return row;
		}

		public void row(int row) {
			this.row = row;
		}

		public int column() {
			return column;
		}

		public void column(int column) {
			this.column = column;
		}

		public void addScore(int score) {
			this.score += score;
		}
		
		public double totalScore() {
			return score;
		}
		
		public int entryCount() {
			ExtractedEntry nextEntry = this;
			int count = 0;
			while (nextEntry != null) {
				count++;
				nextEntry = nextEntry.next;
			}
			return count;
		}
		
		public int charSize() {
			return column;
		}

		public ExtractedEntry next() {
			return next;
		}

		public ExtractedEntry last() {
			ExtractedEntry l = this;
			while (true) {
				if (l.next() == null) {
					return l;
				} else {
					l = l.next();
				}
			}
		}
		
		public void setNext(ExtractedEntry next) {
			this.next = next;
		}

		public ExtractedEntry next(ExtractedEntry next) {
			this.next = next;
			if (next != null) {
				this.score += next.score;
			}
			return this;
		}
		
		public boolean isExtracted() {
			return extracted;
		}
		
		public void setExtracted(boolean extracted) {
			this.extracted = extracted;
		}
		
		public String getChainedString() {
			if (next == null) {
				return toString();
			} else {
				return toString() + " + " + next.getChainedString();
			}
		}

		public String getChainedShortString(char[] source) {
			if (next == null) {
				return toShortString(source);
			} else {
				return toShortString(source) + " + " + next.getChainedShortString(source);
			}
		}
		
		public String getChainedString(char[] source) {
			if (next == null) {
				return toDetailString(source);
			} else {
				return toDetailString(source) + " + " + next.getChainedString(source);
			}
		}

		@Override
		public ExtractedEntry clone() {
			ExtractedEntry entry = null;
			try {
				entry = (ExtractedEntry) super.clone();
			} catch (CloneNotSupportedException e) {
				logger.error("", e);
			}
			return entry;
		}

		@Override
		public String toString() {
			return "(" + new String(source, row + offset - column + 1, column) + ":" + (row + offset) + "," + column + "):" + tagProb + ":" + score;
		}

		public String toWord(char[] source) {
			return new String(source, row + offset - column + 1, column);

		}
		
		public String toShortString(char[] source) {
			return new String(source, row + offset - column + 1, column) + ":" + tagProb.toShortString();

		}
		
		public String toDetailString(char[] source) {
			try {
				return new String(source, row + offset - column + 1, column) + "(" + (row + offset) + "," + column + "):" + tagProb + ":" + score;
			} catch (Exception e) {
				logger.debug("{} ({},{})", new String(source), row + offset - column + 1, column);
				throw new RuntimeException();
			}
		}
	}

	public static class EntryStringer {
		private static char[] source;
		private PosTagProbEntry entry;
		private int row;
		private int column;
		private static int offset;

		public void set (PosTagProbEntry entry, char[] source, int row, int column, int offset) {
			this.entry = entry;
			this.row = row;
			this.column = column;
			EntryStringer.source = source;
			EntryStringer.offset = offset;
		}

		@Override public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(new String(source, row - column + 1 + offset, column))
				.append("[");
			while (entry != null) {
				sb.append(entry.get());
				entry = entry.next();
			}
			sb.append("]");
			return String.valueOf(sb);
		}
	}

	public static class TabularStringer {
		private static char[] source;
		private static PosTagProbEntry[][] tabular;
		private static int[] status;
		private static int length;
		private static int offset;

		public void set (PosTagProbEntry[][] tabular, char[] source, int[] status, int length, int offset) {
			TabularStringer.tabular = tabular;
			TabularStringer.source = source;
			TabularStringer.status = status;
			TabularStringer.length = length;
			TabularStringer.offset = offset;
		}

		@Override public String toString() {
			StringBuilder ret = new StringBuilder();
			for (int row = 0; row < length; row++) {
				PosTagProbEntry[] el = tabular[row];
				int count = status[row];
				StringBuilder sb = new StringBuilder();
				sb.append("").append(row).append(" ")
					.append("{ ").append(count).append(" }").append(" | ");
				for (int column = 1; column <= row + 1; column++) {
					PosTagProbEntry e = el[column];
					sb.append(new String(source, row - column + 1 + offset, column))
						.append("[");
					while (e != null) {
						sb.append(e.get());
						e = e.next();
					}
					sb.append("]").append(" | ");
				}
				ret.append("\r\n").append(sb);
				// logger.debug("{}", sb);
			}
			return String.valueOf(ret);
		}
	}
}