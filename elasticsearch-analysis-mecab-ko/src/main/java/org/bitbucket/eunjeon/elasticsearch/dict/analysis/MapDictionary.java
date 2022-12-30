package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import com.danawa.io.DataInput;
import com.danawa.io.DataOutput;
import com.danawa.io.InputStreamDataInput;
import com.danawa.io.OutputStreamDataOutput;
import org.bitbucket.eunjeon.elasticsearch.util.CharVector;

import java.io.*;
import java.util.*;

/**
 * map 범용 사전. CharSequence : CharSequence[] pair이다. 만약 value에 Object[]를 사용하길
 * 원한다면 custom dictionary를 사용한다.
 */
public class MapDictionary extends SourceDictionary<Object> {

	protected Map<CharSequence, CharSequence[]> map;

	public MapDictionary() {
		this(false);
	}

	public MapDictionary(boolean ignoreCase) {
		super(ignoreCase, null, 0, null, ProductNameDictionary.Type.MAP);
		map = new HashMap<>();
	}

	public MapDictionary(Map<CharSequence, CharSequence[]> map, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(ignoreCase, label, seq, tokenType, type);
		this.map = map;
	}

	public MapDictionary(File file, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(ignoreCase, label, seq, tokenType, type);
		if (!file.exists()) {
			map = new HashMap<>();
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

	public MapDictionary(InputStream is, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(ignoreCase, label, seq, tokenType, type);
		try {
			readFrom(is);
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	@Override
	public void addEntry(CharSequence keyword, Object[] values, List<Object> columnList) {
		if (keyword == null) { return; }
		if (values == null) { return; }
		CharVector cv = new CharVector(String.valueOf(keyword).trim(), ignoreCase);
		if (cv.length() == 0) { return; }
		CharSequence[] list = new CharSequence[values.length];
		for (int inx = 0; inx < values.length; inx++) {
			list[inx] = new CharVector(String.valueOf(values[inx]), ignoreCase);
		}
		map.put(cv.removeWhitespaces(), list);
	}

	public Map<CharSequence, CharSequence[]> getUnmodifiableMap() {
		return Collections.unmodifiableMap(map);
	}

	public Map<CharSequence, CharSequence[]> map() {
		return map;
	}

	public void setMap(Map<CharSequence, CharSequence[]> map) {
		this.map = map;
	}

	public boolean containsKey(CharSequence key) {
		return map.containsKey(key);
	}

	public CharSequence[] get(CharSequence key) {
		return map.get(key);
	}

	@Override
	@SuppressWarnings("resource")
	public void writeTo(OutputStream out) throws IOException {
		if (!(out instanceof BufferedOutputStream)) {
			try { out = new BufferedOutputStream(out); } catch (Exception ignore) { }
		}
		DataOutput output = new OutputStreamDataOutput(out);
		Iterator<CharSequence> keySet = map.keySet().iterator();
		// write size of map
		output.writeVInt(map.size());
		// write key and value map
		for (; keySet.hasNext();) {
			// write key
			CharVector key = CharVector.valueOf(keySet.next());
			output.writeUString(key.array(), key.offset(), key.length());
			// write values
			CharSequence[] values = map.get(key);
			output.writeVInt(values.length);
			for (CharSequence v : values) {
				CharVector value = CharVector.valueOf(v);
				output.writeUString(value.array(), value.offset(), value.length());
			}
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
		map = new HashMap<>();
		int size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			CharSequence key = new CharVector(input.readUString(), ignoreCase);

			int valueLength = input.readVInt();

			CharSequence[] values = new CharSequence[valueLength];

			for (int valueInx = 0; valueInx < valueLength; valueInx++) {
				values[valueInx] = new CharVector(input.readUString(), ignoreCase);
			}
			map.put(key, values);
		}
	}

	@Override
	public void addSourceLineEntry(CharSequence line) {
		if (line == null) { return; }
		String[] kv = String.valueOf(line).split("\t");
		if (kv.length == 1) {
			String value = kv[0].trim();
			addEntry(null, new String[] { value }, null);
		} else if (kv.length == 2) {
			String keyword = kv[0].trim();
			String value = kv[1].trim();
			addEntry(keyword, new String[] { value }, null);
		}
	}

	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if (object != null && object instanceof MapDictionary) {
			MapDictionary mapDictionary = (MapDictionary) object;
			this.map = mapDictionary.map();
		} else {
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}
}
