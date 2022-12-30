package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

import java.util.*;

public class CommonDictionary<T, P> {
    private static Logger logger = Loggers.getLogger(CommonDictionary.class, "");
	private Date createTime;

	private Dictionary<T, P> systemDictionary;

	private Map<String, SourceDictionary<?>> dictionaryMap;

	public CommonDictionary(Dictionary<T, P> systemDictionary) {
		this.systemDictionary = systemDictionary;
		dictionaryMap = new HashMap<>();
		createTime = new Date();
	}

	// systemDictionary를 재설정한다. dictionaryMap은 따로 외부에서 해주어야함.
	public void reset(CommonDictionary<T, P> dictionary) {
		this.systemDictionary = dictionary.systemDictionary;
		this.createTime = dictionary.createTime;
	}

	public List<T> find(CharSequence token) {
		return systemDictionary.find(token);
	}

	public P findPreResult(CharSequence token) {
		return systemDictionary.findPreResult(token);
	}

	public void setPreDictionary(Map<CharSequence, P> map) {
		systemDictionary.setPreDictionary(map);
	}

	public int size() {
		return systemDictionary.size();
	}
	public String label() {
		return systemDictionary.label();
	}
	public int seq() {
		return systemDictionary.seq();
	}
	public boolean ignoreCase() {
		return ((TagProbDictionary) systemDictionary).ignoreCase();
	}
	public <D extends SourceDictionary<?>> D  getDictionary(String dictionaryId, Class<D> cls) {
		try {
			@SuppressWarnings("unchecked")
			D ret = (D) dictionaryMap.get(dictionaryId);
			return ret;
		} catch (Exception ignore) { }
		return null;
	}

	public Map<String, SourceDictionary<?>> getDictionaryMap() {
		return dictionaryMap;
	}

	public SourceDictionary<?> addDictionary(String dictionaryId, SourceDictionary<?> dictionary) {
		int[] info = ProductNameDictionary.getDictionaryInfo(dictionary);
		logger.debug("addDictionary {} : {} [{} / {}]", dictionaryId, dictionary, info[0], info[1]);
		return dictionaryMap.put(dictionaryId, dictionary);
	}

	public void appendAdditionalNounEntry(Set<CharSequence> keySet, String tokenType) {
		systemDictionary.appendAdditionalNounEntry(keySet, tokenType);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "] createTime=" + createTime + ", entry = "
				+ (systemDictionary != null ? systemDictionary.size() : 0) + ", dictionaries = " + dictionaryMap.size();
	}
}
