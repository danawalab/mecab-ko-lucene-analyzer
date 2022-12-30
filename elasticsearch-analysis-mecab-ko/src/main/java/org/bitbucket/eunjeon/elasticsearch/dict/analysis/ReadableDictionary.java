package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import java.io.IOException;
import java.io.InputStream;

public interface ReadableDictionary {
	public void readFrom(InputStream in) throws IOException;
}
