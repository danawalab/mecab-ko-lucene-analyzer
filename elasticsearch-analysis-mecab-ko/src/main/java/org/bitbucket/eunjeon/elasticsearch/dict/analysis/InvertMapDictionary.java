package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import org.bitbucket.eunjeon.elasticsearch.util.CharVector;
import java.io.File;
import java.util.List;

/**
 * Created by swsong on 2015. 7. 31..
 */
public class InvertMapDictionary extends MapDictionary {

	public InvertMapDictionary() {
	}

	public InvertMapDictionary(boolean ignoreCase) {
		super(ignoreCase);
	}

	public InvertMapDictionary(File file, boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		super(file, ignoreCase, label, seq, tokenType, type);
	}

	@Override
	public void addEntry(CharSequence keyword, Object[] values, List<Object> columnList) {
		if (keyword == null) { return; }
		CharVector cv = new CharVector(String.valueOf(keyword).trim(), ignoreCase);
		if (cv.length() == 0) { return; }
		CharVector[] value = new CharVector[] { cv };
		for (int i = 0; i < values.length; i++) {
			map.put(new CharVector(String.valueOf(values[i]), ignoreCase), value);
		}
	}
}