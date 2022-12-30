package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import com.danawa.io.DataInput;
import com.danawa.io.DataOutput;
import com.danawa.io.InputStreamDataInput;
import com.danawa.io.OutputStreamDataOutput;
import org.bitbucket.eunjeon.elasticsearch.util.CharVector;

import java.io.*;
import java.util.*;

public class SynonymDictionary extends MapDictionary {

	private Set<CharSequence> wordSet;
	
	public SynonymDictionary() {
		this(false);
	}

	public SynonymDictionary(boolean ignoreCase) {
		super(ignoreCase);
		if (wordSet == null) {
			wordSet = new HashSet<>();
		}
	}

	public SynonymDictionary(File file, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(file, ignoreCase, label, seq, tokenType, type);
		if (wordSet == null) {
			wordSet = new HashSet<>();
		}
	}

	public SynonymDictionary(InputStream is, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(is, ignoreCase, label, seq, tokenType, type);
		if (wordSet == null) {
			wordSet = new HashSet<>();
		}
	}

	public Set<CharSequence> getWordSet() {
		return wordSet;
	}
	
	public void setWordSet(Set<CharSequence> wordSet) {
		this.wordSet = wordSet;
	}
	
	public Set<CharSequence> getUnmodifiableWordSet() {
		return Collections.unmodifiableSet(wordSet);
	}

	private CharSequence[] duplicateCharList(CharSequence[] arr) {
		if (arr != null) {
			CharSequence[] list = new CharSequence[arr.length];
			System.arraycopy(arr, 0, list, 0, arr.length);
			return list;
		}
		return null;
	}
	// key가 null일수 있다. 양방향의 경우.
	@Override
	public void addEntry(CharSequence keyword, Object[] values, List<Object> columnSettingList) {
		List<CharSequence> list = new ArrayList<>(4);
		CharVector mainWord = null;
		if (keyword != null) {
			mainWord = new CharVector(String.valueOf(keyword).trim(), ignoreCase);
			if (mainWord.length() > 0) {
				wordSet.add(mainWord);
				if (mainWord.hasWhitespaces()) {
					for (CharSequence w : CharVector.splitByWhitespace(mainWord)) {
						wordSet.add(w);
					}
				}
			}
		}

		if (values == null || values.length == 0) { return; }
		// 0번째에 유사어들이 컴마 단위로 모두 입력되어 있으므로 [0]만 확인하면 된다.
		String valueString = values[0].toString();
		// 중복제거.
		String[] synonyms = valueString.split(",");
		dedupSynonym(synonyms);
		for (int k = 0; k < synonyms.length; k++) {
			String synonym = synonyms[k].trim();
			if (synonym.length() > 0) {
				CharVector word = new CharVector(synonym, ignoreCase);
				list.add(word);
				wordSet.add(word);
				if (word.hasWhitespaces()) {
					for (CharSequence w : CharVector.splitByWhitespace(word)) {
						wordSet.add(w);
					}
				}
			}
		}

		if (mainWord == null) {
			// 양방향.
			for (int j = 0; j < list.size(); j++) {
				CharVector key = CharVector.valueOf(list.get(j));
				CharSequence[] value = new CharSequence[list.size() - 1];
				int idx = 0;
				for (int k = 0; k < list.size(); k++) {
					CharSequence val = list.get(k);
					if (!key.equals(val)) {
						// 다른것만 value로 넣는다.
						value[idx++] = val;
					}
				}
				// 유사어사전 데이터에 대표단어와 동일한 단어가 여러개 있을경우, 최종리스트는 더 적어지게 되므로 전체 array
				// 길이를 줄여준다.
				if (idx < value.length) {
					value = Arrays.copyOf(value, idx);
				}

				if (value.length > 0) {
					CharSequence[] value2 = map.get(key);
					if (value2 != null) {
						// 이전값과 머징.
						value2 = duplicateCharList(value2);
						value = mergeSynonyms(value2, value);
					}
					map.put(key, value);
					// 공백을 제거한 key도 하나더 만든다.
					if (key.hasWhitespaces()) {
						key = new CharVector(String.valueOf(key), ignoreCase).removeWhitespaces();
						value2 = map.get(key);
						if (value2 != null) {
							// 이전값과 머징.
							value2 = duplicateCharList(value2);
							value = mergeSynonyms(value2, value);
						}
						map.put(key, value);
					}
					// logger.debug("유사어 양방향 {} >> {}", key, join(value));
				}
			}
		} else {
			// 단방향.
			CharSequence[] value = new CharSequence[list.size()];
			int idx = 0;
			for (int j = 0; j < value.length; j++) {
				CharSequence word = list.get(j);
				if (!mainWord.equals(word)) {
					// 다른것만 value로 넣는다.
					value[idx++] = word;
				}
			}
			if (idx < value.length) {
				value = Arrays.copyOf(value, idx);
			}
			if (value.length > 0) {
				CharSequence[] value2 = map.get(mainWord);
				if (value2 != null) {
					// 이전값과 머징.
					value2 = duplicateCharList(value2);
					value = mergeSynonyms(value2, value);
				}
				//
				// 입력시 키워드는 공백제거.
				//
				map.put(mainWord, value);
				// 공백이 포함되어 있다면, 제거한 단어도 함께 넣어준다.
				if (mainWord.hasWhitespaces()) {
					map.put(new CharVector(String.valueOf(mainWord), ignoreCase).removeWhitespaces(), value);
				}
				// logger.debug("유사어 단방향 {} >> {}", mainWord, join(value));
			}
		}
	}

	// 중복제거한다. 중복이 발견되면 "" 로 치환한다.
	private void dedupSynonym(String[] list) {
		if (list == null || list.length < 2) {
			return;
		}
		for (int i = 0; i < list.length; i++) {
			for (int j = i + 1; j < list.length; j++) {
				if (list[j].length() != 0 && list[i].equals(list[j])) {
					list[j] = "";
				}
			}
		}
		return;
	}

	private CharSequence[] mergeSynonyms(CharSequence[] value2, CharSequence[] value) {
		int removedCount = 0;
		for (int i = 0; i < value.length; i++) {
			for (int j = i + 1; j < value2.length; j++) {
				if (value2[j] != null && value[i].equals(value2[j])) {
					value2[j] = null;
					removedCount++;
				}
			}
		}
		int newSize = value2.length + value.length - removedCount;
		CharSequence[] list = new CharSequence[newSize];
		int i = 0;
		for (CharSequence v : value2) {
			if (v != null) {
				list[i++] = v;
			}
		}
		for (CharSequence v : value) {
			if (v != null) {
				list[i++] = v;
			}
		}
		return list;
	}

	@Override
	@SuppressWarnings("resource")
	public void writeTo(OutputStream out) throws IOException {
		if (!(out instanceof BufferedOutputStream)) {
			try { out = new BufferedOutputStream(out); } catch (Exception ignore) { }
		}
		super.writeTo(out);
		DataOutput output = new OutputStreamDataOutput(out);
		// write size of synonyms
		output.writeVInt(wordSet.size());
		// write synonyms
		Iterator<CharSequence> synonymIter = wordSet.iterator();
		for (; synonymIter.hasNext();) {
			CharVector value = CharVector.valueOf(synonymIter.next());
			output.writeUString(value.array(), value.offset(), value.length());
		}
		try { out.flush(); } catch (Exception ignore) { }
	}

	@Override
	@SuppressWarnings("resource")
	public void readFrom(InputStream in) throws IOException {
		if (!(in instanceof BufferedInputStream)) {
			try { in = new BufferedInputStream(in); } catch (Exception ignore) { }
		}
		super.readFrom(in);
		DataInput input = new InputStreamDataInput(in);
		wordSet = new HashSet<>();
		int size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			wordSet.add(new CharVector(input.readUString(), ignoreCase));
		}
	}
	
	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if (object != null && object instanceof SynonymDictionary) {
			super.reload(object);
			SynonymDictionary synonymDictionary = (SynonymDictionary) object;
			this.wordSet = synonymDictionary.getWordSet();
		} else {
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}
}
