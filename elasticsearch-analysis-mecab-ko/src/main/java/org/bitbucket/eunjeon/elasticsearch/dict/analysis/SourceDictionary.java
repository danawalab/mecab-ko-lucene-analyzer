package org.bitbucket.eunjeon.elasticsearch.dict.analysis;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

import java.io.*;
import java.util.List;

public abstract class SourceDictionary<E> implements ReloadableDictionary, WritableDictionary, ReadableDictionary {
	protected String label;
	protected int seq;
	protected String tokenType;
	protected ProductNameDictionary.Type type;
	protected static Logger logger = Loggers.getLogger(SourceDictionary.class, "");

	protected boolean ignoreCase;

	public boolean ignoreCase() {
		return ignoreCase;
	}
	public String label() {
		return label;
	}
	public int seq() {
		return seq;
	}
	public String tokenType() {
		return tokenType;
	}
	public void ignoreCase(boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
	}
	public ProductNameDictionary.Type type() {
		return this.type;
	}
	public SourceDictionary(boolean ignoreCase, String label, int seq, String tokenType, ProductNameDictionary.Type type) {
		this.ignoreCase = ignoreCase;
		this.label = label;
		this.seq = seq;
		this.tokenType = tokenType;
		this.type = type;
	}

	public void loadSource(File file) {
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			loadSource(is);
		} catch (FileNotFoundException e) {
			logger.error("사전소스파일을 찾을수 없습니다.", e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ignore) { }
			}
		}
	}

	public void loadSource(InputStream is) {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
			String line = null;
			while ((line = br.readLine()) != null) {
				addSourceLineEntry(line);
			}
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	public void addEntry(CharSequence keyword, Object[] values) {
		addEntry(keyword, values, null);
	}

	public abstract void addEntry(CharSequence keyword, Object[] values, List<E> columnSettingList);

	public abstract void addSourceLineEntry(CharSequence line);
}