package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import com.danawa.io.DataInput;
import com.danawa.io.DataOutput;
import com.danawa.io.InputStreamDataInput;
import com.danawa.io.OutputStreamDataOutput;
import org.bitbucket.eunjeon.elasticsearch.util.CharVector;

import java.io.*;
import java.util.*;

public class SetDictionary extends SourceDictionary<Object> {

	private Set<CharSequence> set;

	public SetDictionary() {
		this(false);
	}
	
	public SetDictionary(boolean ignoreCase) {
		super(ignoreCase, null, 0, null, ProductNameDictionary.Type.SET);
		set = new HashSet<>();
	}

	public SetDictionary(HashSet<CharSequence> set, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(ignoreCase, label, seq, tokenType, type);
		this.set = set;
	}

	public SetDictionary(File file, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(ignoreCase, label, seq, tokenType, type);
		if (!file.exists()) {
			set = new HashSet<>();
			logger.error("사전파일이 존재하지 않습니다. file={}", file.getAbsolutePath());
			return;
		}
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			readFrom(is);
		} catch (IOException e) {
			logger.error("", e);
		} finally {
			try { is.close(); } catch (Exception ignore) { }
		}
	}

	public SetDictionary(InputStream is, boolean ignoreCase,  String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(ignoreCase, label, seq, tokenType, type);
		try {
			readFrom(is);
		} catch (IOException e) {
			logger.error("", e);
		}
	}
	
	@Override
	public void addEntry(CharSequence keyword, Object[] value, List<Object> columnList) {
		if (keyword == null) { return; }
		CharVector cv = new CharVector(String.valueOf(keyword).trim(), ignoreCase);
		if (cv.length() > 0) {
			set.add(cv.removeWhitespaces());
		}
	}

	public Set<CharSequence> getUnmodifiableSet() {
		return Collections.unmodifiableSet(set);
	}
	
	public Set<CharSequence> set() {
		return set;
	}
	
	public void setSet(Set<CharSequence> set) {
		this.set = set;
	}
	
	public boolean contains(CharSequence key) {
		return set.contains(key);
	}
	
	@Override
	@SuppressWarnings("resource")
	public void writeTo(OutputStream out) throws IOException {
		if (!(out instanceof BufferedOutputStream)) {
			try { out = new BufferedOutputStream(out); } catch (Exception ignore) { }
		}
		DataOutput output = new OutputStreamDataOutput(out);
		Iterator<CharSequence> valueIter = set.iterator();
		// write size of set
		output.writeInt(set.size());
		// write values
		for (; valueIter.hasNext();) {
			CharSequence value = valueIter.next();
			output.writeString(value.toString());
		}
		try { out.flush(); } catch (Exception ignore) { }
	}

	@Override
	@SuppressWarnings("resource")
	public void readFrom(InputStream in) throws IOException {
		if (!(in instanceof BufferedInputStream)) {
			try { in = new BufferedInputStream(in); } catch (Exception ignore) { }
		}
		DataInput input = new InputStreamDataInput(in);
		set = new HashSet<>();
		int size = input.readInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			set.add(new CharVector(input.readString(), ignoreCase));
		}
	}

	@Override
	public void addSourceLineEntry(CharSequence line) {
		addEntry(line, null, null);
	}

	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if (object != null && object instanceof SetDictionary) {
			SetDictionary setDictionary = (SetDictionary) object;
			this.set = setDictionary.set();
		} else {
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}
}
