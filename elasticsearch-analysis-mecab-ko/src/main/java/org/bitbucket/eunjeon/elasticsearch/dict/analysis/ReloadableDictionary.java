package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

public interface ReloadableDictionary {
	public void reload(Object object) throws IllegalArgumentException;
}
