package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import com.danawa.io.DataInput;
import com.danawa.io.DataOutput;
import com.danawa.io.InputStreamDataInput;
import com.danawa.io.OutputStreamDataOutput;
import org.bitbucket.eunjeon.elasticsearch.util.CharVector;

import java.io.*;
import java.util.*;

public class CompoundDictionary extends MapDictionary {

	private Set<CharSequence> mainWordSet;
	private Set<CharSequence> wordSet;

	public CompoundDictionary() {
		this(false);
	}

	public CompoundDictionary(boolean ignoreCase) {
		super(ignoreCase);
		if (mainWordSet == null) {
			mainWordSet = new HashSet<>();
		}
		if (wordSet == null) {
			wordSet = new HashSet<>();
		}
	}

	public CompoundDictionary(File file, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(file, ignoreCase, label, seq, tokenType, type);
		if (mainWordSet == null) {
			mainWordSet = new HashSet<>();
		}
		if (wordSet == null) {
			wordSet = new HashSet<>();
		}
	}

	public CompoundDictionary(InputStream is, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(is, ignoreCase, label, seq, tokenType, type);
		if (mainWordSet == null) {
			mainWordSet = new HashSet<>();
		}
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

	public Set<CharSequence> getMainWordSet() {
		return mainWordSet;
	}

	public void setMainWordSet(Set<CharSequence> mainWordSet) {
		this.mainWordSet = mainWordSet;
	}

	public Set<CharSequence> getUnmodifiableWordSet() {
		return Collections.unmodifiableSet(wordSet);
	}

	public Set<CharSequence> getUnmodifiableMainWordSet() {
		return Collections.unmodifiableSet(mainWordSet);
	}

	@Override
	public void addEntry(CharSequence keyword, Object[] values, List<Object> columnSettingList) {
		if (keyword == null) { return; }
		if (values == null) { return; }
		if (values.length == 0) { return; }
		CharVector mainWord = new CharVector(String.valueOf(keyword).trim(), ignoreCase);
		if (mainWord.length() == 0) { return; }
		mainWordSet.add(mainWord);
		List<CharSequence> list = new ArrayList<>(4);

		// 0번째에 복합명사들이 컴마 단위로 모두 입력되어 있으므로 [0]만 확인하면 된다.
		String valueString = values[0].toString();
		String[] nouns = valueString.split(",");
		for (int k = 0; k < nouns.length; k++) {
			String noun = nouns[k].trim();
			if (noun.length() > 0) {
				CharSequence word = new CharVector(noun, ignoreCase);
				list.add(word);
				wordSet.add(word);
			}
		}

		CharSequence[] value = new CharSequence[list.size()];
		for (int j = 0; j < value.length; j++) {
			CharSequence word = list.get(j);
			value[j] = word;
		}
		if (value.length > 0) {
			map.put(mainWord, value);
		}
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
		output.writeVInt(mainWordSet.size());
		// write synonyms
		Iterator<CharSequence> mainWordIter = mainWordSet.iterator();
		while (mainWordIter.hasNext()) {
			CharVector value = CharVector.valueOf(mainWordIter.next());
			output.writeUString(value.array(), value.offset(), value.length());
		}
		// write size of synonyms
		output.writeVInt(wordSet.size());
		// write synonyms
		Iterator<CharSequence> wordIter = wordSet.iterator();
		while (wordIter.hasNext()) {
			CharVector value = CharVector.valueOf(wordIter.next());
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
		mainWordSet = new HashSet<>();
		int mainWordSize = input.readVInt();
		for (int entryInx = 0; entryInx < mainWordSize; entryInx++) {
			mainWordSet.add(new CharVector(input.readUString(), ignoreCase));
		}
		wordSet = new HashSet<>();
		int size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			wordSet.add(new CharVector(input.readUString(), ignoreCase));
		}
	}
	
	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if (object != null && object instanceof CompoundDictionary) {
			super.reload(object);
			CompoundDictionary compoundDictionary = (CompoundDictionary) object;
			this.mainWordSet = compoundDictionary.getMainWordSet();
			this.wordSet = compoundDictionary.getWordSet();
		} else {
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}
}
