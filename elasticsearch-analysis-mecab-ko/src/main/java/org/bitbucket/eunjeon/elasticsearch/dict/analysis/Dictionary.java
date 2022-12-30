package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Dictionary<T, P> {
	public abstract List<T> find(CharSequence token);

	public abstract P findPreResult(CharSequence token);

	public abstract void setPreDictionary(Map<CharSequence, P> map);

	public abstract int size();

	public abstract int seq();

	public abstract String label();

	public abstract void appendAdditionalNounEntry(Set<CharSequence> set, String tokenType);
}
