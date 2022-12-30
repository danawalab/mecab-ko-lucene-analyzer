package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import java.io.IOException;
import java.io.OutputStream;

public interface WritableDictionary {
	public void writeTo(OutputStream out) throws IOException;
}